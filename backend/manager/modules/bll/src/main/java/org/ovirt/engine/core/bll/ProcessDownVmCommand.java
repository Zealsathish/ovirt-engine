package org.ovirt.engine.core.bll;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.ovirt.engine.core.bll.context.CommandContext;
import org.ovirt.engine.core.bll.job.ExecutionHandler;
import org.ovirt.engine.core.bll.quota.QuotaManager;
import org.ovirt.engine.core.bll.snapshots.SnapshotsManager;
import org.ovirt.engine.core.bll.utils.PermissionSubject;
import org.ovirt.engine.core.common.action.IdParameters;
import org.ovirt.engine.core.common.action.VdcActionType;
import org.ovirt.engine.core.common.action.VmManagementParametersBase;
import org.ovirt.engine.core.common.action.VmOperationParameterBase;
import org.ovirt.engine.core.common.action.VmPoolSimpleUserParameters;
import org.ovirt.engine.core.common.businessentities.DbUser;
import org.ovirt.engine.core.common.businessentities.Snapshot;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VmDevice;
import org.ovirt.engine.core.common.businessentities.VmPayload;
import org.ovirt.engine.core.common.businessentities.VmPool;
import org.ovirt.engine.core.common.businessentities.VmPoolMap;
import org.ovirt.engine.core.common.businessentities.VmPoolType;
import org.ovirt.engine.core.common.businessentities.VmWatchdog;
import org.ovirt.engine.core.common.businessentities.Snapshot.SnapshotType;
import org.ovirt.engine.core.common.utils.VmDeviceType;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dal.dbbroker.DbFacade;
import org.ovirt.engine.core.utils.log.Log;
import org.ovirt.engine.core.utils.log.LogFactory;

@InternalCommandAttribute
@NonTransactiveCommandAttribute
public class ProcessDownVmCommand<T extends IdParameters> extends CommandBase<T> {

    private static final Log log = LogFactory.getLog(ProcessDownVmCommand.class);

    protected ProcessDownVmCommand(Guid commandId) {
        super(commandId);
    }

    public ProcessDownVmCommand(T parameters) {
        super(parameters);
    }

    @Override
    protected void executeCommand() {
        Guid vmId = getParameters().getId();
        VmPoolMap map = DbFacade.getInstance().getVmPoolDao().getVmPoolMapByVmGuid(vmId);
        List<DbUser> users = DbFacade.getInstance().getDbUserDao().getAllForVm(vmId);
        // Check if this is a Vm from a Vm pool, and is attached to a user
        if (map != null && users != null && !users.isEmpty()) {
            VmPool pool = DbFacade.getInstance().getVmPoolDao().get(map.getvm_pool_id());
            if (pool != null && pool.getVmPoolType() == VmPoolType.Automatic) {
                // should be only one user in the collection
                for (DbUser dbUser : users) {
                    Backend.getInstance().runInternalAction(VdcActionType.DetachUserFromVmFromPool,
                            new VmPoolSimpleUserParameters(map.getvm_pool_id(), dbUser.getId(), vmId),
                            ExecutionHandler.createDefaultContexForTasks(getExecutionContext(), getLock()));
                }
            }
        } else {
            // If we are dealing with a prestarted Vm or a regular Vm - clean stateless images
            // Otherwise this was already done in DetachUserFromVmFromPoolCommand
            removeVmStatelessImages(vmId,
                    ExecutionHandler.createDefaultContexForTasks(getExecutionContext(), getLock()));
        }

        QuotaManager.getInstance().rollbackQuotaByVmId(vmId);
        VmHandler.removeStatelessVmUnmanagedDevices(vmId);

        ApplyNextRunConfiguration(vmId);
    }

    @Override
    public List<PermissionSubject> getPermissionCheckSubjects() {
        return Collections.emptyList();
    }

    /**
     * Update vm configuration with NEXT_RUN configuration, if exists
     * @param vmId
     */
    private static void ApplyNextRunConfiguration(Guid vmId) {
        // Remove snpashot first, in case other update is in progress, it will block this one with exclusive lock
        // and any newer update should be preffered to this one.
        Snapshot runSnap = DbFacade.getInstance().getSnapshotDao().get(vmId, SnapshotType.NEXT_RUN);
        if (runSnap != null) {
            DbFacade.getInstance().getSnapshotDao().remove(runSnap.getId());
            VM vm = DbFacade.getInstance().getVmDao().get(vmId);
            if (vm != null) {
                Date originalCreationDate = vm.getVmCreationDate();
                new SnapshotsManager().updateVmFromConfiguration(vm, runSnap.getVmConfiguration());
                // override creation date because the value in the config is the creation date of the config, not the vm
                vm.setVmCreationDate(originalCreationDate);

                VmManagementParametersBase updateVmParams = createUpdateVmParameters(vm);

                Backend.getInstance().runInternalAction(VdcActionType.UpdateVm, updateVmParams);
            }
        }
    }

    private static VmManagementParametersBase createUpdateVmParameters(VM vm) {
        // clear non updateable fields got from config
        vm.setExportDate(null);
        vm.setOvfVersion(null);

        VmManagementParametersBase updateVmParams = new VmManagementParametersBase(vm);
        updateVmParams.setUpdateWatchdog(true);
        updateVmParams.setSoundDeviceEnabled(false);
        updateVmParams.setBalloonEnabled(false);
        updateVmParams.setVirtioScsiEnabled(false);
        updateVmParams.setClearPayload(true);

        for (VmDevice device : vm.getManagedVmDeviceMap().values()) {
            switch (device.getType()) {
                case WATCHDOG:
                    updateVmParams.setWatchdog(new VmWatchdog(device));
                    break;
                case SOUND:
                    updateVmParams.setSoundDeviceEnabled(true);
                    break;
                case BALLOON:
                    updateVmParams.setBalloonEnabled(true);
                    break;
                case CONTROLLER:
                    if (VmDeviceType.VIRTIOSCSI.getName().equals(device.getDevice())) {
                        updateVmParams.setVirtioScsiEnabled(true);
                    }
                    break;
                case DISK:
                    if (VmPayload.isPayload(device.getSpecParams())) {
                        updateVmParams.setVmPayload(new VmPayload(VmDeviceType.getByName(device.getDevice()), device.getSpecParams()));
                    }
                    break;
                case CONSOLE:
                    updateVmParams.setConsoleEnabled(true);
                    break;
                default:
            }
        }

        // clear these fields as these are non updatable
        vm.getManagedVmDeviceMap().clear();
        vm.getVmUnamagedDeviceList().clear();

        return updateVmParams;
    }

    public static void removeVmStatelessImages(Guid vmId, CommandContext context) {
        if (DbFacade.getInstance().getSnapshotDao().exists(vmId, SnapshotType.STATELESS)) {
            log.infoFormat("Deleting snapshot for stateless vm {0}", vmId);
            Backend.getInstance().runInternalAction(VdcActionType.RestoreStatelessVm,
                    new VmOperationParameterBase(vmId),
                    context);
        }
    }
}