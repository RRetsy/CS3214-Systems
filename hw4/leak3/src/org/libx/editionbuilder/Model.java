package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.xml.ValidationException;
import org.exolab.castor.xml.XMLClassDescriptor;

import org.libx.symlink.SymLink;

import org.libx.xml.Catalogs;
import org.libx.xml.CatalogsItem;
import org.libx.xml.Edition;
import org.libx.xml.Url;

/**
 * This class implements the Model.
 * Each instance represents an edition.
 *
 * @author ...
 */
public class Model {
    public static boolean debug = System.getProperty("model.debug", "false").equals("true");
    public static final String configFileName = "config.xml";

    final private String path;
    final private int revision;
    final private Edition edition;
    // if a model is opened read-only, no changes are saved to disk
    private boolean readonly;       

    void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    boolean isReadonly() {
        return this.readonly;
    }

    public enum Autosave {
       TRUE, FALSE
    }
    /**
     * Create a new, unused edition id.
     */
    public static String createNewEditionId() throws Exception {
        Random rnd = new Random();
        for (int tries = 0; tries < 10000; tries++) {
            String id = "00000000" + Integer.toHexString(rnd.nextInt()).toUpperCase();
            id = id.substring(id.length() - 8, id.length());
            String p1 = id.substring(0, 2);
            String p2 = id.substring(2, 4);

            File d1 = new File(Config.editionpath + "/" + p1);
            d1.mkdir();
            File d2 = new File(Config.editionpath + "/" + p1 + "/" + p2);
            d2.mkdir();
            String newEditionPath = Config.editionpath + "/" + id2RelPath(id) + ".1";
            File d3 = new File(newEditionPath);
            if (!d3.mkdir())
                continue;
            return id;
        }
        // this may happen because of permission problems.
        throw new Error("Could not create new edition, giving up...");
    }

    public static Model makeNewModelFromEdition(String editionId) throws Exception {
        String newEditionId = createNewEditionId();
        if (Builder.copyEdition(editionId, newEditionId, 1)) {
            Utils.printLog("The edition %s is a clone of %s",newEditionId,editionId);    
            return new Model(newEditionId, 1, Autosave.TRUE);
        }
        else
            return null;
    }

    /**
     * create empty model
     */
    public static Model createNewModel() throws Exception {
        String id = createNewEditionId();

        try {
            String newEditionPath = Config.editionpath + "/" + id2RelPath(id) + ".1";
            FileOutputStream outfile = new FileOutputStream(newEditionPath + "/" + configFileName);
            IOUtils.copy(new FileInputStream(Config.getDefaultConfigTemplate()),
                    outfile);
            outfile.close();
            // XXX: temporary - should probably key off of 
            // default <additionalfiles>
            String [] templates = new String [] {
                    "smalllibx.gif", "largelibx.jpg"
            };
            for (String s : templates) {
                FileOutputStream f = new FileOutputStream(newEditionPath + "/" + s);
                IOUtils.copy(new FileInputStream(Config.xmlpath + "/" + s), f);
                f.close();
            }
            return new Model(id, 1, Autosave.TRUE);
        } catch (IOException ioe) {
            Utils.logUnexpectedException(ioe);
            throw ioe;
        }
    }

    /**
     * Load a new model (called from ZUL's "Build New Edition" button)
     */
    public static void loadNewModel() {
        try {
            Model m = Model.createNewModel();
            Model.setCurrentModel(m);
            Utils.printLog("The edition %s is started from scratch", m.edition.getId());   
        } catch (Exception ex) {
            MainWindowController.showException(ex);
        }
    }

    /**
     * Get path for this model.
     */
    public String getPath() {
        return this.path;
    }

    public int getRevision() {
        return this.revision;
    }

    public Edition getEdition() { 
        return this.edition; 
    }


    /**
     * Translates edition id to path names. 
     * For instance, 89ABCDEF is translated to 89/AB/89ABCDEF
     * Leaves legacy names unchanged.
     */
    public static String id2RelPath(String id) {
        char c0 = id.charAt(0);
        char c1 = id.charAt(1);
        if (!(Character.isDigit(c0) || ('A' <= c0 && c0 <= 'F'))) {
            return id;
        }
        if (!(Character.isDigit(c1) || ('A' <= c1 && c1 <= 'F'))) {
            return id;
        }
        return id.substring(0, 2) + "/" + id.substring(2, 4) + "/" + id;
    }

    /**
     * Read model from directory.
     * This code opens an existing configuration.
     * In addition, two cases must be handled: 
     * - branching off of an existing edition
     * - first-time opening of a completely new edition.
     *
     * @param path - directory that contains all files.
     * @param autosave - whether any changes should be saved to the model
     */
    public Model(String id, int revision, Autosave autosave) throws Exception {
        this.revision = revision;
        this.path = id2RelPath(id) + "." + revision;
        boolean initialSave = false;
        Edition edition = null;
        try {
            FileReader r = new FileReader(Config.editionpath + "/" + this.path + "/" + configFileName);
            edition = (Edition)Unmarshaller.unmarshal(Edition.class, r);
            attachPropertyChangeListeners(edition);

            // a new edition was created from a template
            if ("unassigned".equals(edition.getId())) {
                initialSave = true;
                edition.setId(id);
                // add link for edition home page
                edition.getLinks().addUrl(createLinkToEditionHomePage(id));

                // propose adapted-by line
                UserInfo ui = UserInfo.getUserInfo();
                if (ui.getSessionState() == UserInfo.SessionState.LOGGEDON) {
                    edition.getName().setAdaptedby(ui.getEmail());
                }
            }

            // a branch off an existing edition 
            if (!edition.getId().equals(id)) {
                initialSave = true;
                // in this case, the edition id must refer to a new edition.
                if (!(Character.isDigit(id.charAt(0)) || ('A' <= id.charAt(0) && id.charAt(0) <= 'F')))
                    throw new Exception("edition id mismatch old=" + edition.getId() + " new=" + id);

                // reset edition id to match location where we loaded the file (in case this
                // edition was cloned/copied from another one.)
                edition.setId(id);
                edition.getName().setEdition("Copy of " + edition.getName().getEdition());
                edition.getName().setShort("Copy of " + edition.getName().getShort());
                edition.getName().setLong("Copy of " + edition.getName().getLong());
                // fix up other fields?
            }

            setReadonly(autosave == Autosave.FALSE);

            // set correct revision (for instance, if config.xml contains old revision
            // from copy forward)
            String libxVersion = Config.libxVersion + "." + revision;
            if (autosave == Autosave.TRUE && !edition.getVersion().equals(libxVersion)) {
                edition.setVersion(libxVersion);
                initialSave = true;
            }

            // always add the property change listener, even for read-only models, so we can
            // inform user that changes were not saved.
            addPropertyChangeListener(autoSaver);
        } catch (FileNotFoundException fnf) {
            Utils.logUnexpectedException(fnf);
            throw fnf;
        } catch (MarshalException me) {
            Utils.logUnexpectedException(me);
            throw me;
        } catch (ValidationException ve) {
            Utils.logUnexpectedException(ve);
            throw ve;
        }
        this.edition = edition;

        if (initialSave && autosave == Autosave.TRUE)
            autoSaver.propertyChange(null);
    }

    static Url createLinkToEditionHomePage(String editionId) {
        Url l = new Url();
        l.setHref(getLiveHttpPath(editionId) + "/libx.html"); // XXX don't hardwire
        l.setLabel("About LibX ...");
        return l;
    }

    public static void initializeDesktop() {
        Utils.printLog("new desktop");
        Utils.setDesktopAttribute("modelchange", new Utils.BeanPropertyChangeSupport());
    }

    public static void addCurrentModelChangeListener(PropertyChangeListener l) {
        Utils.BeanPropertyChangeSupport modelchange;
        modelchange = (Utils.BeanPropertyChangeSupport)Utils.getDesktopAttribute("modelchange");
        modelchange.addPropertyChangeListener(l);
    }

    /**
     * Return current http path for this model instance, e.g.
     * http://top.cs.vt.edu/editions/vt.2/
     */
    public static String getCurrentModelHttpPath() {
        return Model.getCurrentModel().getHttpPath();
    }

    public String getHttpPath() {
        return Config.httpeditionpath + "/" + getPath();
    }

    public static String getHttpPath(String id, int revision) {
        return Config.httpeditionpath + "/" + id2RelPath(id) + "." + revision;
    }

    public static String getLiveHttpPath(String id) {
        return Config.httpeditionpath + "/" + id2RelPath(id);
    }

    public static String getHttpXpiPath(String id, int rev) {
        return Model.getHttpPath(id, rev) + "/libx-"+id+".xpi";
    }

    public static String getLiveHttpXpiPath(String id) {
        return Model.getLiveHttpPath(id) + "/libx-"+id+".xpi";
    }

    public static String getBuildXpiPath(String id, int rev) {
        return Model.getFSPath(id, rev) + "/libx-"+id+".xpi";
    }

    public static String getLiveBuildXpiPath(String id) {
        return Model.getLiveFSPath(id) + "/libx-"+id+".xpi";
    }

    public static String getHttpExePath(String id, int rev) {
        return Model.getHttpPath(id, rev) + "/libx-"+id+".exe";
    }

    public static String getLiveHttpExePath(String id) {
        return Model.getLiveHttpPath(id) + "/libx-"+id+".exe";
    }

    public static String getBuildExePath(String id, int rev) {
        return Model.getFSPath(id, rev) + "/libx-"+id+".exe";
    }

    public static String getLiveBuildExePath(String id) {
        return Model.getLiveFSPath(id) + "/libx-"+id+".exe";
    }

    public static String getConfigFilePath(String id, int rev) {
        return Model.getFSPath(id, rev) + "/" + Model.configFileName;
    }

    public static String getTestPageUrl(String id, int rev) {
        return Config.httpeditionpath + "/libxtestedition.php?edition=" + id + "." + rev;
    }

    /* Returns the last modified date of the config.xml file */
    public static Date getLastModifiedDateOfConfig(String id, int rev) {
        File f = new File(getConfigFilePath(id, rev));
        Date date = getLastModifiedDate(f);      
        return date;     
    }
    
    /* Returns the last modified date of the .xpi file */
    public static Date getLastModifiedDateOfXpi(String id, int rev) {
        File f = new File(getBuildXpiPath(id, rev));
        Date date = getLastModifiedDate(f);      
        return date;     
    }

    /*Returns the last modified date of the file*/
    private static Date getLastModifiedDate(File f) {
        Date date = null;
        if(f.exists()) {
            long longDate = f.lastModified();
            date = new Date(longDate);
        }
        return date;
    }
    

    /**
     * Return current filesystem path for this model instance, e.g.
     * /var/www/html/editions/vt.2/
     */
    public String getFSPath() {
        return Config.editionpath + "/" + getPath() + "/";
    }

    /* Return path for current model */
    public static String getCurrentModelFSPath() {
        return Model.getCurrentModel().getFSPath();
    }

    /* Return path based on edition id and revision */
    public static String getFSPath(String id, int revision) {
        return Config.editionpath + "/" + id2RelPath(id) + "." + revision + "/";
    }

    /* Return path for live revision based on edition id */
    public static String getLiveFSPath(String id) {
        return Config.editionpath + "/" + id2RelPath(id);
    }

    public static Model getCurrentModel() {
        return (Model)Utils.getDesktopAttribute("currentmodel");
    }

    public static void setCurrentModel(Model model) {
        Model oldmodel = getCurrentModel();
        Utils.setDesktopAttribute("currentmodel", model);
        // do not send property change notification on unload
        if (model == null) {
            MainWindowController.showStatus(StatusCode.OK, "no edition loaded.");
            return;
        }

        Utils.BeanPropertyChangeSupport modelchange;
        modelchange = (Utils.BeanPropertyChangeSupport)Utils.getDesktopAttribute("modelchange");
        modelchange.notifyPropertyChangeListeners("model", oldmodel, model);
        MainWindowController.showStatus(StatusCode.OK, "loaded.");
    }

    private Object attachPropertyChangeListeners(Object root) {
        if (root == null)
            return null;

        Class rtype = root.getClass();
        if (rtype.isPrimitive() || rtype == String.class || rtype == Boolean.class)
            return root;

        if (root.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(root); i++) {
                attachPropertyChangeListeners(Array.get(root, i));
            }
            return root;
        } 

        /** Some property change events will occur where the new value
         * is a collection. See, for instance,
         * org.libx.xml.Links.setUrl(org.libx.xml.Url[] vUrlArray). 
         * (OTOH, Links.getUrl() returns a Url[], not a Vector<Url>)
         *
         * We interpret those to mean that we must traverse into
         * all elements of the collection.
         */
        if (root instanceof Collection) {
            for (Object o : (Collection)root)
                attachPropertyChangeListeners(o);
            return root;
        }

        for (Method getter : root.getClass().getDeclaredMethods()) {
            /* If root is a bean, e.g., has an addPropertyChangeListener,
             * attach 'propagateevents' listener */
            if (getter.getName().equals("addPropertyChangeListener")) {
                /* Skip these to avoid adding listeners to static fields
                 * in org.libx.xml.types.* classes.  Those fields are constants.
                 * They do not change, no listener is needed.  Adding a listener
                 * would lead to a memory leak.
                 */
                if ("org.libx.xml.types".equals(root.getClass().getPackage().getName()))
                    continue;

                if (debug)
                    System.out.println("adding model listener to:"+root+" "+root.getClass());
                try {
                    getter.invoke(root, propagateevents);
                } catch (Exception iae) { 
                    Utils.logUnexpectedException(iae);
                }
            }

            // skip static methods, skip getChoiceValue, and
            // anything that's not a plain getter
            if ((getter.getModifiers() & Modifier.STATIC) != 0
                || getter.getName().equals("getChoiceValue") 
                || getter.getParameterTypes().length > 0
                || !getter.getName().startsWith("get"))
                continue;

            // recurse
            try {
                Object o = getter.invoke(root);
                attachPropertyChangeListeners(o);
            } catch (Exception iae) {
                Utils.logUnexpectedException(iae);
            }
        }
        return root;
    }   

    private java.beans.PropertyChangeSupport propertyChangeSupport;

    protected void notifyPropertyChangeListeners(java.lang.String fieldName, java.lang.Object oldValue, java.lang.Object newValue)
    {
        if (propertyChangeSupport == null) return;
        propertyChangeSupport.firePropertyChange(fieldName,oldValue,newValue);
    }

    public boolean removePropertyChangeListener(java.beans.PropertyChangeListener pcl)
    {
        if (propertyChangeSupport == null) return false;
        propertyChangeSupport.removePropertyChangeListener(pcl);
        return true;
    }

    public void addPropertyChangeListener(java.beans.PropertyChangeListener pcl)
    {
        if (propertyChangeSupport == null) {
            propertyChangeSupport = new java.beans.PropertyChangeSupport(this);
        }
        propertyChangeSupport.addPropertyChangeListener(pcl);
    } 

    PropertyChangeListener propagateevents = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if (propertyChangeSupport == null) return;
            propertyChangeSupport.firePropertyChange(evt);
            /* If any property was set to a new value: for instance, a
             * new Url was added, or the entire Links.Url[] replaced,
             * we must make attach to all descendants of all newly
             * added objects as a change listener. */
            try {
                attachPropertyChangeListeners (evt.getNewValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    PropertyChangeListener autoSaver = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            try {
                if (readonly) {
                    MainWindowController.showStatus(StatusCode.WARNING, "changes not saved in read-only view.");
                } else {
                    save();
                    MainWindowController.showStatus(StatusCode.OK, "successfully saved.");
                }
            } catch(Exception e) {
                MainWindowController.showStatus(StatusCode.ERROR, 
                        " could not save edition state:" + e.getMessage());
                e.printStackTrace();
            }
        }
    };

    void save() throws Exception {
        String filename = Config.editionpath + "/" + this.path + "/" + configFileName;
        Utils.printLog("saving %s", filename);
        // new Throwable().printStackTrace(); 

        // marshal into a string buffer first, in order to overwrite
        // output file if an exception is thrown
        
        StringWriter xmlwriter = new StringWriter();
        Marshaller marshaller = new Marshaller(xmlwriter);
        marshaller.setDoctype(null,"http://libx.org/xml/libxconfig.dtd");
        marshaller.marshal(this.edition);
        
        FileWriter outfile = new FileWriter(filename);
        IOUtils.write(xmlwriter.getBuffer(), outfile);
        outfile.close();
    }

    /**
     * Create a readable representation of this model instance.
     */
    public String toString() {
        return "model [dir='" + getCurrentModelFSPath() + "']";
    }

    /**
     * Makes an edition's revision live. 
     * @param id
     * @param revision
     */
    public static void makeLive(final String id, final int revision) {
        try {
            EditionCache.invalidateEdition(id);
            changeSymlink(id, revision);     
          
            Model m = getCurrentModel();
            if (m != null && m.getEdition().getId().equals(id) && m.revision == revision) {
                // if the current model is made live, make it read-only.
                m.setReadonly(true);
            } else {
                // if a revision is made live that is not the current model, 
                // load that revision into a temporary Model instance
                m = new Model(id, revision, Autosave.FALSE);
            }
            // in both cases, add catalogs of this model to database
            if (isEditionPublic(id))
                m.addCatalogsToDatabase();

        } catch (Exception exc) {
            MainWindowController.showException(exc);
        }
    }

    /**
     * Check if given edition is public.
     * Does not require a current model instance, hence static.
     */
    public static boolean isEditionPublic(String id) throws Exception {

        /* After executed, toString() will report "true" or "false" depending
         * on whether the edition is public or not.  */
        DbUtils.ResultSetAction isPublicAction = new DbUtils.ResultSetAction() {
            private boolean isPublic = false;
            public void execute(final ResultSet rs) throws SQLException {
                isPublic = rs.getBoolean(1);
            }
            public String toString() {
                return Boolean.toString(isPublic);
            }
        };

        DbUtils.doSqlQueryStatement(
                "SELECT isPublic FROM editionInfo WHERE editionId = ?", 
                isPublicAction, 
                id);
        return Boolean.parseBoolean(isPublicAction.toString());
    }

    /**
     * Deletes the old symlink and shifts it to the edition pointed to by the given id 
     * and revision numbers.
     * @param id
     * @param revision
     */
    private static void changeSymlink(String id, int revision) throws IOException {
        File liveLink = new File(getLiveFSPath(id));
        File newLiveDir = new File(getFSPath(id, revision));

        if (liveLink.exists()) {
            // if the edition is already live, copy the latest version of the .htaccess
            // file into the about-to-be-live directory to preserve the current homepage
            FileUtils.copyFileToDirectory(new File(liveLink, ".htaccess"), newLiveDir);
            liveLink.delete();
        } else {
            // if this is the first time an edition is made live, create a new .htaccess file
            // pointing it at the default homepage.
            writeHtAccessFile(id, revision, getDefaultHomepage(id));
        }

        SymLink.symlink(id+"."+revision,getLiveFSPath(id));
    }

    /**
     * The default home page.
     */
    static String getDefaultHomepage(String id) {
        return Config.httpeditionpath + "/download.php?edition=" + id; // XXX don't hardwire
    }

    static void writeHtAccessFile(String id, int revision, String homepage) throws IOException {
        File newLiveDir = new File(getFSPath(id, revision));
        FileWriter htaccess = new FileWriter(new File(newLiveDir, ".htaccess"));
        htaccess.write(
            "AddType application/x-xpinstall .xpi\n" +
            "Redirect /editions/" + id2RelPath(id) + "/libx.html "  // XXX don't hardwire
                + homepage + "\n");
        htaccess.close();
    }

    /**
     * This method adds catalogs to the catalogsInfo database 
     * 
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws Exception
     * @throws MarshalException
     * @throws ValidationException
     */
    public void addCatalogsToDatabase() throws Exception {

        Edition ed = this.getEdition();
        String editionId = ed.getId();
        Catalogs c = ed.getCatalogs();
        
        CatalogsItem[] citems = c.getCatalogsItem(); 
        
        /*
         * Delete existing catalogs from the database.
         */
        deleteCatalogsFromDatabase(editionId);

        Utils.printLog("adding catalogs of edition %s #catalogs is %d", 
                    editionId, c.getCatalogsItemCount());

        for(CatalogsItem ci : citems) {            

            Object cat = ci.getChoiceValue(); 

            XMLClassDescriptor cdesc = (XMLClassDescriptor)Utils.getDescriptorClass(cat.getClass()).newInstance();

            String type = cdesc.getXMLName();
            
            // skip openurlresolver and scholar catalog types.
            if (type.equals("openurlresolver") || type.equals("scholar"))
                continue;

            String name = (String)Utils.getBeanProperty(cat,"name");
            String url = (String)Utils.getBeanProperty(cat, "url");

            StringWriter xml = new StringWriter();
            Marshaller.marshal(cat,xml);

            /*
             * This logic strips the xml header of the marshalled xml.
             */
            String xmlString = xml.toString();
            String[] xmlArr = xmlString.split("\\?>");
    
            String stmt = "INSERT INTO catalogInfo(url,name,type,xml,editionId) VALUES (?, ?, ?, ?, ?)";

            DbUtils.doSqlUpdateStatement(stmt, url, name, type, xmlArr[xmlArr.length-1], editionId);
        }
    }

    public static void deleteCatalogsFromDatabase(String id) throws Exception {
        Utils.printLog("deleting catalogs of edition %s", id);
        String delstmt = "DELETE FROM catalogInfo WHERE editionId = '"+id+"'";
        DbUtils.doSqlUpdateStatement(delstmt);
    }
    

    /**
     * Obtains the revisions from the edition id, sorted. 
     *
     * Could be moved to EditionCache as well.
     *
     * @param editionId
     * @return
     */
    static List<Integer> getRevisions(final String editionId) {
        final String prefix = Model.id2RelPath(editionId);

        FilenameFilter filter = new FilenameFilter() {          
            public boolean accept(File f,String name) {
                return name.startsWith(editionId+".");
            }
        };

        String str = new File(Config.editionpath+"/"+prefix).getParent();  
        String[] fnames = new File(str).list(filter);

        List<Integer> revisions =  new ArrayList<Integer>();
	if (fnames == null)
		return revisions;

        for (int i = 0; i < fnames.length; i++) {
            String[] arr = fnames[i].split("\\.");
            revisions.add(Integer.parseInt(arr[arr.length - 1]));
        }
        Collections.sort(revisions);
        return revisions;
    }

    /**
     * Retrieves the homepage link from the .htaccess file 
     * @param editionId
     * @return
     * @throws Exception
     */
    public static String getCurrentHomepageLink(String editionId) throws Exception {

        File file = new File(getLiveFSPath(editionId)+"/"+".htaccess");

        LineIterator it = FileUtils.lineIterator(file, "UTF-8");

        try {
            while(it.hasNext()) {
                String line = it.nextLine();
                if(line.startsWith("Redirect")) {
                    String[] strArr = line.split(" "); 
                    return strArr[strArr.length - 1];
                }
            }
            return "";
        } finally {
            LineIterator.closeQuietly(it);
        }
    }
}

