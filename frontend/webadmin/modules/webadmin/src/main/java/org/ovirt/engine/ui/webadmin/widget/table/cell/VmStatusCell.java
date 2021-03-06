package org.ovirt.engine.ui.webadmin.widget.table.cell;

import org.ovirt.engine.core.common.businessentities.GuestAgentStatus;
import org.ovirt.engine.core.common.businessentities.VM;
import org.ovirt.engine.core.common.businessentities.VMStatus;
import org.ovirt.engine.core.common.businessentities.VmPauseStatus;
import org.ovirt.engine.ui.common.widget.table.cell.AbstractCell;
import org.ovirt.engine.ui.uicompat.EnumTranslator;
import org.ovirt.engine.ui.webadmin.ApplicationConstants;
import org.ovirt.engine.ui.webadmin.ApplicationResources;
import org.ovirt.engine.ui.webadmin.ApplicationTemplates;
import org.ovirt.engine.ui.webadmin.gin.AssetProvider;

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.AbstractImagePrototype;

public class VmStatusCell extends AbstractCell<VM> {

    private final static ApplicationResources resources = AssetProvider.getResources();
    private final static ApplicationConstants constants = AssetProvider.getConstants();
    private final static ApplicationTemplates templates = AssetProvider.getTemplates();

    @Override
    public void render(Context context, VM vm, SafeHtmlBuilder sb, String id) {

        // Nothing to render if no vm is provided:
        if (vm == null) {
            return;
        }

        // Find the image corresponding to the status of the vm:
        VMStatus status = vm.getStatus();
        ImageResource statusImage;

        switch (status) {
        case Up:
            if (vm.isRunOnce()) {
                statusImage = resources.runOnceUpImage();
            } else {
                statusImage = resources.vmStatusRunning();
            }
            break;
        case SavingState:
            statusImage = resources.vmStatusWait();
            break;
        case RestoringState:
            statusImage = resources.vmStatusWait();
            break;
        case PoweringUp:
            statusImage = resources.vmStatusStarting();
            break;
        case PoweringDown:
            statusImage = resources.vmStatusPoweringDown();
            break;
        case RebootInProgress:
            statusImage = resources.rebootInProgress();
            break;
        case WaitForLaunch:
            statusImage = resources.waitForLaunch();
            break;
        case ImageLocked:
            statusImage = resources.vmStatusWait();
            break;
        case MigratingFrom:
        case MigratingTo:
            statusImage = resources.migrationImage();
            break;
        case Suspended:
            statusImage = resources.suspendedImage();
            break;
        case Paused:
            statusImage = resources.pauseImage();
            break;
        case Unknown:
        case Unassigned:
            statusImage = resources.questionMarkImage();
            break;
        case NotResponding:
            statusImage = resources.questionMarkImage();
            break;
        default:
            statusImage = resources.downStatusImage();
            break;
        }

        // Generate the HTML for status image
        SafeHtml statusImageHtml =
                SafeHtmlUtils.fromTrustedString(AbstractImagePrototype.create(statusImage).getHTML());

        // Find the image corresponding to the alert
        SafeHtml alertImageHtml = getResourceImage(vm);

        if (alertImageHtml != null) {
            sb.append(templates.statusWithAlertTemplate(statusImageHtml, alertImageHtml, id));
        } else {
            sb.append(templates.statusTemplate(statusImageHtml, id));
        }

    }

    SafeHtml getResourceImage(VM vm) {
        boolean updateNeeded = vm.getStatus() == VMStatus.Up && vm.getGuestAgentStatus() == GuestAgentStatus.UpdateNeeded;
        if (!updateNeeded && (vm.getVmPauseStatus() != VmPauseStatus.NONE || vm.getVmPauseStatus() != VmPauseStatus.NOERR)) {
            return null;
        }
        else {
            // Create Image from the alert resource
            ImageResource alertImageResource = resources.alertImage();

            // Get the image html
            AbstractImagePrototype imagePrototype = AbstractImagePrototype.create(alertImageResource);
            String html = imagePrototype.getHTML();

            // Append tooltip
            EnumTranslator translator = EnumTranslator.getInstance();
            String toolTip = updateNeeded ? constants.newtools() : translator.translate(vm.getVmPauseStatus());
            html = html.replaceFirst("img", "img " + "title='" + toolTip + "' "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

            return SafeHtmlUtils.fromTrustedString(html);
        }
    }
}
