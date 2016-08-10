package org.libx.libappdatabase;

import org.zkoss.zk.ui.Executions;
import javax.servlet.ServletContext;

/**
 * This class must be accessed before the first BaseX class is accessed.
 *
 * It triggers initialization of BaseX with a rigged user.home property, forcing
 * BaseX to access a database in a location relative to this web application.
 */
public class InitializeBaseX 
{
    public static void initialize(ServletContext servletContext) {
        // trick BaseX into reading its configuration from the WEB-INF directory
        // by temporarily redirecting user.home property.
        String basexdir = servletContext.getRealPath("WEB-INF");
        String oldUserHome = System.getProperty("user.home");
        System.setProperty("user.home", basexdir);
        if (!org.basex.core.Prop.HOME.startsWith(basexdir))
            throw new java.lang.Error("BaseX property initialization failed");
        System.setProperty("user.home", oldUserHome);
    }

    public static void initializeInZK() {
        initialize((ServletContext)Executions.getCurrent().getDesktop().getWebApp().getNativeContext());
    }
}
