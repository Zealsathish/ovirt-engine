package org.ovirt.engine.ui.userportal.widget.table.column;

import org.ovirt.engine.ui.common.widget.table.column.AbstractImageResourceColumn;
import org.ovirt.engine.ui.userportal.ApplicationResources;
import org.ovirt.engine.ui.userportal.ApplicationResourcesWithLookup;
import org.ovirt.engine.ui.userportal.gin.ClientGinjectorProvider;

public abstract class AbstractUserPortalImageResourceColumn<T> extends AbstractImageResourceColumn<T> {

    protected ApplicationResources getApplicationResources() {
        return ClientGinjectorProvider.getApplicationResources();
    }

    protected ApplicationResourcesWithLookup getApplicationResourcesWithLookup() {
        return ClientGinjectorProvider.getApplicationResourcesWithLookup();
    }

}