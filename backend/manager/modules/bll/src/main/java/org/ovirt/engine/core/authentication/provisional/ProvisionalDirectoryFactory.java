package org.ovirt.engine.core.authentication.provisional;

import org.ovirt.engine.core.authentication.Directory;
import org.ovirt.engine.core.authentication.DirectoryFactory;
import org.ovirt.engine.core.bll.adbroker.LdapBroker;
import org.ovirt.engine.core.bll.adbroker.LdapFactory;
import org.ovirt.engine.core.extensions.mgr.Configuration;
import org.ovirt.engine.core.extensions.mgr.ConfigurationException;

/**
 * This is the factory for the bridge between the new directory interface and the existing LDAP infrastructure. It
 * will exist only while the engine is migrated to use the new directory interface, then it will be removed.
 */
public class ProvisionalDirectoryFactory implements DirectoryFactory {
    // The names of the configuration parameters:
    private static final String DOMAIN_PARAMETER = "domain";

    @Override
    public String getType() {
        return "provisional";
    }

    @Override
    public Directory create(Configuration config) throws ConfigurationException {
        // Get the name of the domain from the configuration:
        String domain = config.getInheritedString(DOMAIN_PARAMETER);
        if (domain == null) {
            throw new ConfigurationException(
                "The directory described in configuration file \"" + config.getFile().getAbsolutePath() + "\" can't " +
                "be created because the parameter \"" + config.getAbsoluteKey(DOMAIN_PARAMETER) + "\" doesn't have a " +
                "value."
            );
        }

        // Check that the domain is defined in the database:
        LdapBroker broker = LdapFactory.getInstance(domain);
        if (broker == null) {
            throw new ConfigurationException(
                "The directory described in configuration file \"" + config.getFile().getAbsolutePath() + "\" can't " +
                "be created because the domain \"" + domain + "\" doesn't exist in the database."
            );
        }

        // Create the authenticator:
        return new ProvisionalDirectory(domain, broker);
    }
}
