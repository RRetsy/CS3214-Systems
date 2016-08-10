package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URL;
import java.net.URLConnection;

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import org.libx.xml.Additionalfiles;

import org.zkoss.util.media.Media;

import org.zkoss.zk.ui.Component;

import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.UploadEvent;

import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Image;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;

/**
 * Manage the files included with one edition.
 *
 * @author Godmar Back
 */
public class FileTabController extends Vbox {
    /**
     * Construct new file tab controller and listen for new models
     */
    public FileTabController() {
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    loadModel((Model)evt.getNewValue());    
                } catch (Exception e) {
                    MainWindowController.showException(e);
                }
            }
        });
    }

    /**
     * Exclude internal files.
     * XXX make this more robust, and support it on a per FileTabController basis
     */
    private static HashSet<String> dontlist = new LinkedHashSet<String>();
    static {
        dontlist.add("config.xml");
        dontlist.add("defaultprefs.xml");
    }

    /**
     * Returns true if a file entry is internal (such as config.xml)
     * internal file entries do not have a filebox entry.
     */
    static boolean isInternal(org.libx.xml.File f) {
        return dontlist.contains(f.getName());
    }

    /**
     * Create filebox entries for all non-internal files.
     */
    private void loadModel(Model m) throws Exception {
        org.libx.xml.File [] files = m.getEdition().getAdditionalfiles().getFile();
        getChildren().clear();
        for (org.libx.xml.File f : files) {
            if (!isInternal(f)) {
                Filebox fbox = new Filebox(f);
                appendChild(fbox);
            }
        }
    }

    /**
     * Add a new entry to additionalfiles; set the directory as given.
     * The initial filename is empty.
     * Called from ZUL code.
     */
    public Filebox addNewFile (String directory) {
        try {
            org.libx.xml.File f = new org.libx.xml.File();
            f.setDirectory(directory);
            f.setName("");
            Model.getCurrentModel().getEdition().getAdditionalfiles().addFile(f);
            Filebox fbox = new Filebox(f);
            appendChild(fbox);
            return fbox;
        } catch (Exception ex) {
            MainWindowController.showException(ex);
        }
        return null;
    } 

    /**
     * Download a new file; return chrome path.
     * Used by image box/openurl resolver import to learn the property 
     * setting that needs to be put in .image property.
     * This will create a new entry under additionalfiles/
     */
    static String addNewFile (String directory, String url) throws Exception {
        Component comp = (Component)Utils.getDesktopAttribute("rootWindow");
        FileTabController fTab = (FileTabController)comp.getFellow("fileController");
        FileTabController.Filebox fBox = fTab.addNewFile(directory); //"chrome/libx/skin/libx"
        org.libx.xml.File file = fBox.downloadFileFromURL(url);
        Utils.printLog("automatically downloaded image: url=%s", url);
        return "chrome://libx/skin/" + file.getName();
    }

    /**
     * Implement an input element that allows a user to upload a file.
     * Each instance corresponds to an additional file entry.
     */
    public static class Filebox extends Vbox {
        final Image img;    // if image
        final Textbox url;
        final Html error;
      
        final Html chromename;
        final org.libx.xml.File fileentry;  

        Filebox (org.libx.xml.File fileentry) throws Exception {
            this.fileentry = fileentry;
            this.chromename = new Html("Please upload a file or specify a URL.");
            this.img = new Image();
            this.url = new Textbox();

            Button downloadButton = new Button("Download File from URL");
            downloadButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    String v = url.getValue();
                    if (v == null || v.equals("")) {
                        error.setContent("Please enter a URL first.");
                        return;
                    }

                    try {
                        downloadFileFromURL(v);
                        Utils.printLog("user downloaded image: url=%s", v);
                    } catch (FileNotFoundException fnfe) {
                        error.setContent("Download Failed: Incorrect URL <a href=\""+fnfe.getMessage()+"\" target=\"_new\">"+fnfe.getMessage()+"</a>");
                    }catch (Exception ex) {
                        error.setContent("Download failed:" + ex.getMessage());
                        MainWindowController.showException(ex);
                    } 
                }
            });

            /* Setting upload=true causes a different rendering for this button,
             * compared to the other buttons.  Cosmetic problem.
             */
            Button uploadButton = new Button("Upload Local File...");
            uploadButton.setUpload("true");
            uploadButton.addEventListener(Events.ON_UPLOAD, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    try {
                        UploadEvent fe = (UploadEvent)e;
                        Media media = fe.getMedia();
                        if (media == null)
                            throw new InterruptedException("file upload did not complete "
                                    +"(did you abort? - please try again or contact us)");
                        String name = media.getName();  
                        InputStream inputf = media.getStreamData();  
                        copyFileFromStream(inputf, name);
                        Utils.printLog("user uploaded image: name=%s", name);
                    } catch (InterruptedException ie) {
                        MainWindowController.showException(ie);
                    }
                }
            });

            Button deleteButton = new Button("Delete File");
            deleteButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    try {
                        Filebox.this.setParent(null);
                        Model.getCurrentModel().getEdition().getAdditionalfiles()
                        .removeFile(Filebox.this.fileentry);
                        // XXX also delete the actual file here.
                    } catch (Exception ex) {
                        MainWindowController.showException(ex);
                    }
                }
            });

            Hbox enterUrlBox = new Hbox();
            enterUrlBox.setWidth("100%");
            Label test = new Label("Enter URL:");
            enterUrlBox.appendChild(test);
            enterUrlBox.appendChild(url);
            Utils.setPropertyOnAllChildren(enterUrlBox, "width", "100%");

            Hbox buttonBox = new Hbox(new Component[] {
                downloadButton, 
                uploadButton, 
                deleteButton 
            });

            Vbox left = new Vbox();
            left.appendChild(new Html(
                    "You may enter a URL (such as www.vt.edu/favicon.ico) or "
                    +"you may upload a file saved on your machine."));
            left.appendChild(enterUrlBox);
            left.appendChild(buttonBox);
            left.appendChild(this.error = new Html());
          
            Vbox right = new Vbox();
            right.appendChild(new Label("Preview:"));
            right.appendChild(this.img);

            Hbox lowerH = new Hbox();
            lowerH.appendChild(left);
            lowerH.appendChild(right);
            Utils.setPropertyOnAllChildren(lowerH, "width", "100%");

            this.appendChild(this.chromename); 
            this.appendChild(lowerH);
            displayImage();
        }

        org.libx.xml.File downloadFileFromURL(String fileurl) throws Exception {
            if (!fileurl.startsWith("http://"))
                fileurl = "http://" + fileurl;

            URL url = new URL(fileurl);
            URLConnection conn = url.openConnection();
            conn.connect();
            InputStream inputf = conn.getInputStream();
            String name = url.getPath().replaceFirst(".*/", "");
            return copyFileFromStream(inputf, name);
        }

        /*
         * find unused filename
         */
        private String findUnusedFilename(String name) {
            int i = 1;
            int suffixOffset = name.lastIndexOf(".");
            String base = name.substring(0, suffixOffset);
            String suffix = name.substring(suffixOffset);
            while (new File(Model.getCurrentModelFSPath() + name).exists()) {
                // append a number so that favicon.ico becomes favicon_1.ico
                name = base + "_" + i + suffix;
                i++;
            }
            return name;
        }

        /**
         * Write a file with name 'basename', copying data from the InputStream provided.
         * Sets fileentry.name to basename, but does not change the fileentry.directory.
         */
        private org.libx.xml.File copyFileFromStream(InputStream inputf, String basename) {
            try {
                /* If the current entry's name matches the suggested basename, overwrite. 
                 * For instance, the user uploads a new "favicon.ico" via the file upload box.
                 * In all other cases, find an unused filename to avoid overwriting
                 * already uploaded files.
                 */
                if (!basename.equals(fileentry.getName()))
                    basename = findUnusedFilename(basename);

                String fullpathname = Model.getCurrentModelFSPath() + basename;
                Utils.printLog("saving uploaded file to: %s", fullpathname);
                OutputStream outputf = new FileOutputStream(fullpathname);

                IOUtils.copy(inputf, outputf);
                outputf.close();
                inputf.close();

                // java.beans.PropertyChangeSupport requires that old != new
                if (basename.equals(fileentry.getName()))
                    fileentry.setName("");
                fileentry.setName(basename);
                displayImage();
            } catch (IOException ioe) {
                error.setContent("I/O exception: " + ioe);
            } catch (Exception ex) {
                MainWindowController.showException(ex);
            }
            return fileentry;
        }

        private void displayImage() {
            this.chromename.setContent("<b>File: " + fileentry.getName() + "</b><br />"
                    + "<i>Directory: " + fileentry.getDirectory() + "</i>");
            this.error.setContent("");

            // XXX handle files other than images here.
            // XXX handle URL maybe here
            // if (fileentry.getDirectory().startsWith("http://")) {
            // ----
            // force reload even if URL did not change - this can happen
            // if the old & new image have the same name, such as favicon.ico
            img.setSrc(Model.getCurrentModelHttpPath() + "/" + fileentry.getName() + "?" + new Date());
        }
    }

    private final static Pattern chromePattern = Pattern.compile("chrome://libx/(.*)/.*");

    /**
     * Returns fileentry corresponding to chromePath.
     * Returns null if chromePath is null or "".
     * Throws an error if chromePath is neither null, "", nor a valid chrome path.
     */
    static org.libx.xml.File getAdditionalFileEntry(String chromePath) {
		if (chromePath == null || "".equals(chromePath))
			return null;
        // convert 'chrome://libx/skin/virginiatech.ico' 
        // to 'chrome/libx/skin/libx', 'virginiatech.ico'
        if (chromePath != null) {
            String base = chromePath.replaceFirst(".*/","");
            Matcher m = chromePattern.matcher(chromePath);
            if (m.matches()) {
                return getAdditionalFileEntry("chrome/libx/" + m.group(1) + "/libx", base);
            }
        }
        if (!chromePath.equals(""))
            throw new Error("Ill-formed chrome path " + chromePath);
        return null;
    }

    /**
     * Find existing additional file entry that matches directory/name
     * @return entry found, null if no match
     */
    private static org.libx.xml.File getAdditionalFileEntry(String directory, String name) {
        Additionalfiles afiles = Model.getCurrentModel().getEdition().getAdditionalfiles();
        for (org.libx.xml.File f : afiles.getFile()) {
            if (f.getName().equals(name) && f.getDirectory().equals(directory))
                return f;
        }
        return null;
    }
}
