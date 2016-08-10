package org.libx.editionbuilder;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * This class starts a thread that periodically invokes System.gc()
 *
 * This gc forces the execution of finalizers for unreachable streams,
 * closing them and relieving pressure on the underlying system's
 * fd limit.
 *
 * A full gc involves stopping all threads, resulting in delayed
 * service.  We use this as a work-around - the correct action is
 * to ensure that all file descriptors are closed in finally {}
 * clauses.
 */
public class GCHelper {
    /* 
     * By default, force full GC every 5 minutes.
     */
    public static final int PERIOD = 5 * 60 * 1000;
    private static boolean shuttingDown = false;
    private static Thread gcHelper;

    public static class ShutdownListener implements ServletContextListener {
        /** Notification that the servlet context is about to be shut down. */
        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            if (Config.verbose)
                System.out.println("Servlet context is being destroyed, shutting down GCHelper!");
            shutdown();
        }

        /** Notification that the web application is ready to proceed */
        @Override
        public void contextInitialized(ServletContextEvent sce) { }
    }

    /**
     * Shut down GC Helper.
     * Must be invoked on web application shutdown.
     */
    public static void shutdown() {
        shuttingDown = true; 
        gcHelper.interrupt();
    }

    static {
        gcHelper = new Thread() {
            public void run() {
                Thread.currentThread().setName("GCHelper-" + new java.util.Date());
                while (!shuttingDown && PERIOD > 0) {
                    try {
                        Thread.sleep(PERIOD);
                        System.gc();
                    } catch (InterruptedException ie) {
                        ; // ignore
                    }
                }
            }
        };
        gcHelper.start();
    }
}
