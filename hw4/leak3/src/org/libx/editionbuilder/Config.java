package org.libx.editionbuilder;

import java.io.InputStream;


import java.util.Properties;

/**
 * This class contains global config settings.
 * The properties are set by ZUL beanshell code and can be used
 * both by Java and Beanshell code.
 *
 * @author ...
 */
public class Config {
    public static String libxVersion = "1.5";

    /* The following variables can be 
     * turned off/on from ZUL while the system is running.
     */
    //* verbose logging
    public static boolean verbose = true;

    //* offer links to IE toolbar
    public static boolean ieactivated = true;

    //* offer feed tab
    public static boolean feedsactivated = true;

    public static class FieldOption {
        private String  name;
        private String  label;
        private Class  clazz;
        public String getName() { return this.name; }
        public String getLabel() { return this.label; }
        public Class getClazz() { return this.clazz; }
        public FieldOption(String label, Class clazz, String name) {
            this.name = name;
            this.clazz = clazz;
            this.label = label;
        }
    };

    public static FieldOption [] liveOptions = new FieldOption [] {
        new FieldOption("Verbose Logging", Config.class, "verbose"),
        new FieldOption("Offer IE for download", Config.class, "ieactivated"),
        new FieldOption("Offer Tab to configure feeds", Config.class, "feedsactivated"),
        new FieldOption("Email address for emails", MailSystem.class, "fromAddr"),
        new FieldOption("From: field used in emails", MailSystem.class, "fromName"),
        new FieldOption("LibX CVS", Config.class, "libxcvspath"),
        new FieldOption("Build Command", Config.class, "buildcommand"),
        new FieldOption("Working Dir during build", Config.class, "buildworkingdir"),
        new FieldOption("Copy Forward", Config.class, "makenewrevisioncommand"),
        new FieldOption("Clone entire Edition", Config.class, "copyeditioncommand"),
        new FieldOption("Edition directory (FS)", Config.class, "editionpath"),
        new FieldOption("Edition directory (HTTP)", Config.class, "httpeditionpath"),
        new FieldOption("Edition builder dir", Config.class, "rootpath"),
        new FieldOption("", Config.class, "docpath"),
        new FieldOption("", Config.class, "xmlpath"),
        new FieldOption("", Config.class, "reldocpath"),
        new FieldOption("", Config.class, "dbjndipath"),
        new FieldOption("Who to send email to for help", Config.class, "helpemail"),
        new FieldOption("Mailing list -request address", UserInfo.class, "mailingListEmail"),
        new FieldOption("Catalog Detection Timeout (in ms)", CatalogDetector.class, "detectCatalogTimeout"),
        new FieldOption("Catalog Detection Timestep (in ms)", CatalogDetector.class, "detectCatalogTimestep"),
        new FieldOption("OpenURL Detection Timeout (in ms)", OpenUrlTabsController.class, "detectResolverTimeout"),
        new FieldOption("Maximum Number of Records to ask for in WorldCat registry search", SearchWorldcat.class, "maxRecords"),
        new FieldOption("Characters to show for catalog url", CatalogsTabController.CatalogController.class, "maxCharOfCatalogUrlToShow"),
        new FieldOption("Interval between forced GCs", GCHelper.class, "PERIOD"),
        new FieldOption("Default White List", FeedController.class, "defaultWhiteList"),
        new FieldOption("Default URL (legacy)", FeedController.class, "defaultRootFeed"),
        new FieldOption("Default Feed Description (legacy)", FeedController.class, "defaultRootDescription"),
        new FieldOption("Default LibApp (LibX2)", FeedController.class, "defaultRootFeedLibX2"),
        new FieldOption("Default Feed Description (LibX2)", FeedController.class, "defaultRootDescriptionLibX2")

    };

    //* directory in which libx.mozdev.org is checked out
    public static String libxcvspath;
    //* build command
    public static String buildcommand;
    //* make a new revision command
    public static String makenewrevisioncommand;
    //* copy an edition command (copies most recent revision)
    public static String copyeditioncommand;
    //* build working dir
    public static String buildworkingdir;
    //* directory in which editions are kept
    public static String editionpath;
    //* http path to directory in which editions are kept
    public static String httpeditionpath;
    //* ...
    public static String rootpath;
    //* ...
    public static String docpath;
    //* ...
    public static String xmlpath;
    //* ...
    public static String reldocpath;
    //* database path
    public static String dbjndipath = "java:comp/env/jdbc/libxeb";
    //* help email
    public static String helpemail;
    

    private static void setRootpath(String path) {
        if (path == null)
            throw new Error("Internal configuration error: root path cannot be null");

        rootpath = path;
        reldocpath = "src/docs";
        docpath = rootpath + "/" + reldocpath;
        xmlpath = rootpath + "/src/xml";
    }

    public static String getDefaultConfigTemplate() {
        return xmlpath + "/emptyconfig.xml";
    }

    static {
        try {
            InputStream is; 
            is = Config.class.getClassLoader().getResourceAsStream("install.properties");
            Properties ip = new Properties();
            ip.load(is);
            System.getProperties().putAll(ip);
        } catch (Exception e) {
            e.printStackTrace();
        }
        setRootpath(System.getProperty("eb.rootpath"));
        editionpath = System.getProperty("eb.editionpath");
        httpeditionpath = System.getProperty("eb.httpeditionpath");
        libxcvspath = System.getProperty("eb.libxcvspath");
        buildcommand = System.getProperty("eb.buildcommand");
        buildworkingdir = System.getProperty("eb.buildworkingdir");
        makenewrevisioncommand = rootpath + "/" + System.getProperty("eb.makenewrevisioncommand");
        copyeditioncommand = rootpath + "/" + System.getProperty("eb.copyeditioncommand");
        helpemail = System.getProperty("eb.helpemail");
    }

    public static void main(String []av) {
        System.out.println("rootpath= " + rootpath);
        System.out.println("docpath= " + docpath);
        System.out.println("editionpath=" + editionpath);
        System.out.println("defaulttemplate= " + getDefaultConfigTemplate());
    }

    // a value for the width of Grid elements that produces cosmetically good results
    static String zkGridWidth = "98%";
}

