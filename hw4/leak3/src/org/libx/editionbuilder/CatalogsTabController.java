package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.File;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import java.util.concurrent.Future;

import java.util.regex.Pattern;

import javax.mail.MessagingException;

import org.exolab.castor.xml.XMLClassDescriptor;
import org.exolab.castor.xml.XMLFieldDescriptor;

import org.libx.xml.Bookmarklet;
import org.libx.xml.Catalogs;
import org.libx.xml.CatalogsItem;
import org.libx.xml.Openurl;
import org.libx.xml.Openurlresolver;
import org.libx.xml.Resolver;
import org.libx.xml.Searchoption;
import org.libx.xml.Xisbn;

import org.libx.xml.types.XisbnIncludeincontextmenuType;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.UiException;

import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;

import org.zkoss.zul.Button;
import org.zkoss.zul.Caption;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Vbox;
import org.zkoss.zul.Window;

/**
 * This class controls the tab where the catalogs are being configured.
 * Nested classes control various subelements in this tab.
 */
public class CatalogsTabController extends Hbox {
    //This controller will house all the catalogs as well  as the list of catalogs
    private CatalogList catalogList;
    private OptionsWidgetController optionsWidgetController;
    
    public CatalogsTabController() {
        try {
            this.optionsWidgetController = new OptionsWidgetController();
            Utils.setDesktopAttribute("optionsWidgetController", optionsWidgetController);
        } catch (Exception e) {
            e.printStackTrace();
            MainWindowController.showException(e);
        }
    }

    /* 
     * The 'Urlbox' handles the user inputting a URL for autodetection.
     * It also implements autodetection that's triggered  without user input.
     */
    public static class Urlbox extends Vbox {
        private static Pattern fullDomainName = Pattern.compile(".+\\..+\\..{2,4}$");
        private static Pattern shortDomainName = Pattern.compile(".+\\.(com|org|net)", Pattern.CASE_INSENSITIVE);
        private Vbox detectionOutput;
        private Label diagnosticsMessageLabel;
        public class UrlInputbox extends Textbox {
            private String lastProbed;
            {
                /* send "onChange" event if user presses enter */
                setWidgetListener("onKeyDown", "fireSubmitOnEnter(event,this);");
            }
            /* user types along... onChanging is called every time */
            public void onChanging(InputEvent e) {
                try {
                    String url = e.getValue();

                    /* If user types a name such as www.google.com or amazon.com,
                     * go ahead and probe this domain
                     */
                    if (fullDomainName.matcher(url).matches() ||
                        shortDomainName.matcher(url).matches()) {

                        // but don't probe for .ed, .go, or .edu/
                        if (!url.endsWith(".ed") && !url.endsWith(".go") && !url.endsWith("/")) {
                            startCatalogDetection(lastProbed = url, "");
                            return;
                        }
                    }
                    /* Otherwise, if user is about to type a longer URL, inform them
                     * that they have to press 'return' before we auto-detect
                     * Avoid giving this message for "http:/" or "http://", but allow
                     * http://somehost/
                     */
                    if (url.startsWith("http://"))
                        url = url.substring("http://".length());
                    if (!url.equals("http:/") && url.indexOf('/') != -1)
                        diagnosticsMessageLabel.setValue("(press 'return' to attempt auto-detection)");
                    clearCatalogDetectionResults();
                } catch (Exception ex) {
                    Utils.logUnexpectedException(ex);
                }
            }

            /* onChange is sent when user presses Enter 
             * Also via onblur DOM event when textbox loses focus - in which case we have 
             * already probed due to the last onChanging event.  Remember 'lastProbe' and
             * compare to eliminate that case.
             */
            public void onChange(InputEvent e) {
                try {
                    String url = e.getValue();
                    if (!url.trim().equals("") && !url.equals(lastProbed))
                        startCatalogDetection(lastProbed = url, "");
                } catch (Exception ex) {
                    MainWindowController.showException(ex);
                }
            }
        }

        private void clearCatalogDetectionResults() {
            detectionOutput.getChildren().clear();
        }

        /**
         * Start detecting a catalog at a given url.
         * Show message msg (which is either empty, or a message that
         * tells user why we are probing.)
         * The actual detection is done on a separate thread.
         * If a catalog was detected, a timer calls the 'found' callback.
         */
        public void startCatalogDetection(String _url, String msg) {
            // ignore http: if given
            final String url = _url.replaceFirst("^http://", "");

            diagnosticsMessageLabel.setValue(msg);
            clearCatalogDetectionResults();
            final Label status = new Label();
            detectionOutput.appendChild(status);

            CatalogDetector.detectCatalog(url, status, new CatalogDetector.CatalogFoundCallback() {
                private void addDetectedCatalog(CatalogDetector.CatalogProbe.Result r) {
                    try {
                        Utils.printLog("user added detected catalog %s", r.getMessage());
                        for (Searchoption so : r.getNeededOptions()) {
                            OptionsWidgetController optionsWidgetController =
                                (OptionsWidgetController)Utils.getDesktopAttribute("optionsWidgetController");
                            // add needed options, but only if they aren't already
                            // defined in current model.
                            if (!optionsWidgetController.hasCode(so.getValue())) {
                                Model.getCurrentModel().getEdition().getSearchoptions().addSearchoption(so);
                            }
                        }
                        catalogList.addCatalog(r.getResult(), true);
                    } catch (Exception ex) {
                        MainWindowController.showException(ex);
                    }
                }

                public void foundCatalogs(CatalogDetector.CatalogProbe.Result [] catlist) {

                    // for each catalog detected, create a label and an 'add' button
                    // that adds the catalog when clicked.
                    
                    for (final CatalogDetector.CatalogProbe.Result r : catlist) {

                        Component addCatalogButton = null;
                        if (r.getResult() != null) {
                            addCatalogButton = new Button("Add");
                            addCatalogButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                                public void onEvent(Event e) {
                                    addDetectedCatalog(r);
                                    // Remove entry if user adds it.
                                    // Otherwise, user could add it again, resulting in undefined
                                    // behavior if the same catalog bean appears twice in the 
                                    // edition's XML tree.
                                    // assumes Button's parent is an Hbox appended to detectionoutput,
                                    // see below.
                                    e.getTarget().getParent().setParent(null);
                                }
                            });
                        } else {
                            addCatalogButton = new Label("");
                        }

                        detectionOutput.appendChild(new Hbox(new Component [] {
                            Utils.createHelpTextComponent("catalog.detection"),
                            new Html(r.getMessage()), addCatalogButton
                        }));
                    }
                }

                public void done(int totalcatalogsfound) {
                    if (totalcatalogsfound == 0) {
                        Button reportFailure = new Button("Let us know about this!");
                        reportFailure.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                            public void onEvent(Event e) {
                                try {
                                    MailSystem.Message msg = new MailSystem.Message(MailSystem.fromAddr);
                                    msg.setSubject("autoprobe failure notification");
                                    msg.setBody("received 0 results when probing:\n" + url);
                                    msg.send();
                                    status.setValue("Sent email to " + MailSystem.fromAddr);
                                } catch (MessagingException ex) {
                                    MainWindowController.showException(ex);
                                }
                            }
                        });
                        detectionOutput.appendChild(reportFailure);
                    }
                }
            });
        }

        /**
         * Look if any catalog hostnames are associated with this OCLC institution id
         * If so, start autodetection for first entry listed.
         */
        void startOCLCProbe(String id) {
            ArrayList<String> opacList = CatalogDetector.opacBaseList.get(id);
            if (opacList != null) {
                String baseOpac = opacList.get(0);
                startCatalogDetection(baseOpac, "OCLC reports that your OPAC may be located at " 
                    + baseOpac + ".");

                if (Config.verbose) {
                    Utils.printLog("startOCLCProbe[%s]: %s", id, baseOpac);
                }
            }
        }

        public Urlbox() {
            // when the user opens a revision, see if the automatic detection mechanism
            // found an OCLC institution id. If so, initiate catalog detection based
            // on the opac base url found in the OCLC institution profile.
            //
            Model.addCurrentModelChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String id = (String)Utils.getDesktopAttribute("oclcInstitutionId");
                    if (id == null)
                        return;
                    
                    startOCLCProbe(id);
                }
            });

            // register so that OpenurlTabController can call startOCLCProbe to 
            // initiate OCLC probe. 
            Utils.setDesktopAttribute("autodetecturlbox", this);

            appendChild(new Hbox(new Component [] {
                new Label("Enter URL:"),
                new UrlInputbox()
            }));
            appendChild(this.diagnosticsMessageLabel = new Label());
            appendChild(this.detectionOutput = new Vbox());
        }

        private CatalogList catalogList;
        public void initialize(CatalogList catalogList) {
            this.catalogList = catalogList;   
        }

        /**
         * Search OCLC Worldcat registry and display result in Popup Window.
         */
        private Window oclcResultsWin;
        public void searchWorldcatRegistry(String sterm) {
            try {                
                List<SearchWorldcat.Record> results = SearchWorldcat.search(sterm);
                if (results.size() == 0) {
                    MainWindowController.showStatus(StatusCode.WARNING, "No results found.");
                    return;
                }

                Utils.printLog("worldcatregistry search: hits=%d term=%s", results.size(), sterm);
                
                if (oclcResultsWin == null) {
                    oclcResultsWin = new Window();
                    oclcResultsWin.setWidth("90%");
                    oclcResultsWin.setClosable(true);
                    oclcResultsWin.setPosition("center");
                }

                Columns cc = new Columns();
                cc.appendChild(new Column("OCLC Id"));
                cc.appendChild(new Column("Brief Description"));
                cc.appendChild(new Column("Score"));
                cc.appendChild(new Column(""));
                Rows rr = new Rows();

                final HashMap<SearchWorldcat.Record, Html> record2Label 
                    = new HashMap<SearchWorldcat.Record, Html>();
                final HashMap<SearchWorldcat.Record, Future> record2Future 
                    = new HashMap<SearchWorldcat.Record, Future>();
                final HashMap<SearchWorldcat.Record, Toolbarbutton> record2ImportButton 
                    = new HashMap<SearchWorldcat.Record, Toolbarbutton>();

                for (final SearchWorldcat.Record record : results) {
                    Row r = new Row();
                    r.appendChild(new Html("<a href=\"" + 
                        String.format(CatalogDetector.worldcatProfileURLFormat, record.oclcId) 
                        + "\" target=\"_new\">" + record.oclcId + "</a>"));
                    Html h = new Html(Utils.escapeHtmlEntities(record.briefName));
                    r.appendChild(h);
                    record2Label.put(record, h);

                    r.appendChild(new Label(String.format("%.2f", record.similarity)));
                    Toolbarbutton b = new Toolbarbutton("Import this profile"); 

                    /* While the window is showing, we start retrieving the profiles, all in parallel. 
                     * A timer checks after 2, 4, and 6 seconds which profiles have been retrieved
                     * and updates the information being displayed, filling in found catalog URLs. 
                     */
                    final Future future;
                    future = CatalogDetector.getOpacBaseListFromOCLCInstitutionRepository(record.oclcId);
                    record2Future.put(record, future);
                    record2ImportButton.put(record, b);

                    b.addEventListener(Events.ON_CLICK, new EventListener() {
                        public void onEvent(Event e) {
                            try {
                                /* wait for institution profile retrieval to complete */
                                future.get();

                                Utils.printLog("user imported oclc id: %s", record.oclcId);
                                Urlbox.this.startOCLCProbe(record.oclcId);
                                oclcResultsWin.detach();
                            } catch (Exception ex) {
                                MainWindowController.showException(ex);
                            }
                        }
                    });
                    r.appendChild(b);
                    rr.appendChild(r);
                }

                Grid g = new Grid();
                g.appendChild(rr);
                g.appendChild(cc);

                oclcResultsWin.getChildren().clear();
                oclcResultsWin.appendChild(g);
                Caption c = new Caption("Search found " + results.size() + " hits.");
                oclcResultsWin.appendChild(c);
                appendChild(oclcResultsWin);

                final int tries = 3;
                final int checkperiod = 2000;

                final Timer update = new Timer(checkperiod);
                update.setRepeats(true);
                update.addEventListener(Events.ON_TIMER, new Utils.EventListenerAdapter(true) {
                    private int repeats = 0;
                    public void onEvent(Event e) {
                        if (++repeats > tries)
                            update.stop();

                        /* See which probes have completed and update display */
                        for (Map.Entry<SearchWorldcat.Record, Html> me : record2Label.entrySet()) {
                            SearchWorldcat.Record r = me.getKey();
                            Future f = record2Future.get(r);
                            if (f == null || !f.isDone())
                                continue;
                            record2Future.remove(r);
                            Toolbarbutton importButton = record2ImportButton.remove(r);

                            ArrayList<String> opacList = CatalogDetector.opacBaseList.get(r.oclcId);
                            String c = Utils.escapeHtmlEntities(r.briefName) + " <small>";
                            if (opacList != null) {
                                c += "<a target=\"_new\" href=\"http://" + opacList.get(0) + "\">"
                                    + Utils.escapeHtmlEntities(opacList.get(0)) + "</a>";
                            } else {
                                c += "(<i><a target=\"_new\" href=\""
                                    + String.format(CatalogDetector.worldcatProfileURLFormat, r.oclcId) 
                                    + "\">profile " + r.oclcId 
                                    + "</a> does not contain catalog information</i>)";
                                importButton.setParent(null);
                            }
                            c += "</small>";
                            Html h = me.getValue();
                            h.setContent(c);
                        }
                    }
                });
                oclcResultsWin.appendChild(update);
                oclcResultsWin.doModal();
            } catch (Exception e) {
                MainWindowController.showException(e);
            }
        }
    }

    /**
     * CatalogDefault is instantiated by ReadObjectConfiguration.
     */
    public static class CatalogDefault {
        public CatalogDefault(String className, String propName, String propValue) {
            try {
                Class c = Class.forName(className);
                HashMap<String, Object> defaults = defaultValues.get(c);
                if (defaults == null)
                    defaultValues.put(c, defaults = new HashMap<String, Object>());

                Object defValue = propValue;
                Class<?> propType = Utils.beanPropertyGetter(c, propName).getReturnType();
                if (propType != String.class) {
                    Method valueConverter = propType.getMethod("valueOf", java.lang.String.class);
                    defValue = valueConverter.invoke(null, propValue);
                }
                defaults.put(propName, defValue);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static final String catalogDefaultFile = "/catalogs.default";
    static HashMap<Class, HashMap<String, Object>> defaultValues;
    static {
        /* read default values. */
        defaultValues = new HashMap<Class, HashMap<String, Object>>();
        ReadObjectConfiguration conf = new ReadObjectConfiguration();
        try {
            Scanner scanner = new Scanner(new File(Config.xmlpath + catalogDefaultFile));
            conf.read(scanner);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Set default values for a catalog.
     * The default values are inferred from the catalog type.
     */
    static void setDefaults(Object catalog) throws Exception {
        Class type = catalog.getClass();
        if (!defaultValues.containsKey(type))
            return;

        HashMap<String, Object> defaults = defaultValues.get(type);
        for (Map.Entry<String, Object> e : defaults.entrySet())
            Utils.setBeanProperty(catalog, e.getKey(), e.getValue());
    }

    /**
     * This class controls the catalogs currently being configured.
     * Children are of type CatalogController, with drag-n-drop used
     * to change order.
     * See catalogTabRightPanel in .zul
     */
    public static class CatalogList extends Vbox implements Utils.CanRestoreModel {

        /* Record the long description for each catalog type from the ZUL file
         * in a HashMap.  This maps, for instance, 
         * org.libx.xml.Millenium.class to 'III Millennium'
         */
        private HashMap<Class, String> catClass2Description = new HashMap<Class, String>();
        private void recordCatalogDescriptions(Combobox cbox) throws ClassNotFoundException {
            for (Object _ci : cbox.getItems()) {
                Comboitem ci = (Comboitem)_ci;
                Class c = Class.forName("org.libx.xml." + Utils.upperCaseName((String)ci.getValue()));
                catClass2Description.put(c, ci.getLabel());
            }
        }

        /**
         * Appends a controller for the catalog, in the view.
         * @param catalog
         * @throws Exception
         */
        void addCatalogController(Object catalog, boolean showRequired) throws Exception {
            appendChild(new CatalogController(this, catalog, showRequired));
        }

        /**
         * Adds a new Catalog object to the model.
         * Also invokes the method that will update the view. 
         * * @param catalog
         * @throws Exception
         */
        private void addCatalog(Object catalog, boolean showRequired) throws Exception {
            this.setVisible(true);

            CatalogsItem citem = new CatalogsItem();
            String pname = catalog.getClass().getName().replaceFirst(".*\\.", "");
            Method setter = Utils.beanPropertySetter(citem, pname, catalog.getClass());
            setter.invoke(citem, catalog);
            Utils.setRequiredAttributes(catalog);
            
            addCatalogController(catalog, showRequired);
            Model.getCurrentModel().getEdition().getCatalogs().addCatalogsItem(citem);
        }
        
        /**
         * Populates the list of catalogs each time the model is loaded.
         * @param model
         * @throws Exception
         */
        private void populateList(Model model) throws Exception {
            getChildren().clear();     // removes all existing catalog controllers
            Catalogs catalogs = (Catalogs)model.getEdition().getCatalogs();
            for (CatalogsItem i : catalogs.getCatalogsItem()) {
                addCatalogController(i.getChoiceValue(),false);
            }
            // set up/down button visibility
            for (Object c : getChildren()) {
                ((CatalogController)c).setUpDownButtonVisibility();
            }
            
            this.setVisible(catalogs.getCatalogsItemCount() > 0);
        }

        /**
         * Loads the model and also invokes the method that will display the corresponding catalogs list.
         * @param model
         * @throws Exception
         */
        private void loadModel(Model model) throws Exception {
            populateList(model);
        }

        /**
         * Adds a new Catalog depending on the type selected from the dropdown in Zul.
         * @param catType
         * @throws Exception
         */
        private void addCatalogByType(String catType, boolean showRequired) throws Exception {
            Class c = Class.forName("org.libx.xml." + Utils.upperCaseName(catType));
            Object catalog = c.newInstance();
            setDefaults(catalog);
            addCatalog(catalog, showRequired);
        }

       /**
        * Restores the model when the catalogs are rearranged as in the case of drag and drop.
        * @throws Exception
        */
        public void restoreModel() throws Exception {
            Catalogs catalogs = Model.getCurrentModel().getEdition().getCatalogs();
            int children = this.getChildren().size();

            CatalogsItem[] citems = new CatalogsItem[children];

            for (int i = 0; i < children; i++) {

                CatalogsItem ci = new CatalogsItem();
                Object cat = ((CatalogController)this.getChildren().get(i)).getCatalogObject();
                String pname = cat.getClass().getName().replaceFirst(".*\\.", "");

                Method setter = Utils.beanPropertySetter(ci, pname, cat.getClass());

                setter.invoke(ci, cat);
                citems[i] = ci;  
            }
            catalogs.setCatalogsItem(citems);
        }
    }

    /**
     * Called on every visit. 
     * Model is not yet loaded - register for future model changes.
     */
    public void initialize(Vbox rightpanel, Combobox catalogChoice) {
        this.catalogList = (CatalogList)rightpanel;
        rightpanel.setHflex("1");

        try {
            catalogList.recordCatalogDescriptions(catalogChoice);
        } catch (ClassNotFoundException cne) {
            Utils.logUnexpectedException(cne);
        }

        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    loadModel((Model)evt.getNewValue());    
                } catch (Exception e) {
                    Utils.logUnexpectedException(e);
                }
            }
        });
    }

    private void loadModel(Model m) throws Exception {
        Utils.printLog("new model loaded edition=%s revision=%d", 
                m.getEdition().getId(), m.getRevision());
        optionsWidgetController.resetOptionLabelList();
        catalogList.loadModel(m);
    }

    //* onClick handler for ZUL "Add" button.
    // The flag indicates whether the catalog is being newly added so as to display the
    // required fields
    public void addCatalog (Combobox catalogChoice) {
        try {
           
            this.setVisible(true);
            Comboitem selectedItem = catalogChoice.getSelectedItem();
            if (selectedItem == null) {
                MainWindowController.showStatus(StatusCode.ERROR, 
                        "No catalog type was selected - please select catalog type first");
                return;
            }
            String catType = (String)selectedItem.getValue();
            catalogList.addCatalogByType(catType, true);
            Utils.printLog("The catalog of type %s is added",catType);
        } catch (Exception e) {
            MainWindowController.showException(e);
        }
    }

    /**
     * CatalogController controls individual row in catalog list.
     */
    public static class CatalogController extends Vbox {

        private Object cat;    
        private CatalogList parent;
        private Button downbutton, upbutton;
        private Html topline;
        private Hbox messageBox;

        /**
         * Toggles the visibility of the required and optional attributes.
         * @author tgaat
         *
         */
        public static class ToggleVisibilityCheckbox extends Checkbox {
            private Hbox box;
            
            ToggleVisibilityCheckbox(Hbox box, String label, boolean initiallyvisible) { 
                this.box = box;
                this.setLabel(label); 
                this.setChecked(initiallyvisible);
            }

            public void onCheck(Event e) throws UiException {
                box.setVisible(isChecked());
            }
        }

        /**
         * Deletes the catalog controller from the view and calls the restore model method 
         * to update the model after the catalog deletion
         * @author tgaat
         *
         */
        
        public class Delbutton extends Button {
            private CatalogList cataloglist;
            public Delbutton(CatalogList cataloglist) {
                this.cataloglist = cataloglist;
            }    
            public void onClick(Event e) throws Exception {  
                CatalogController.this.setParent(null);
                cataloglist.restoreModel();
                
                cataloglist.setVisible(Model.getCurrentModel().getEdition().getCatalogs().getCatalogsItemCount() > 0);
            }
        }

        Utils.AttributeFilter requiredAttributes = new Utils.AttributeFilter() {
            public boolean include(XMLFieldDescriptor fd) {
                return fd.isRequired();
            }
        };

        Utils.AttributeFilter notRequiredAttributes = new Utils.AttributeFilter() {
            public boolean include(XMLFieldDescriptor fd) {
                return !fd.isRequired();
            }
        };

        public static int maxCharOfCatalogUrlToShow = 70;

        /**
         * Place a description of this catalog into provided html element
         */
        void describeCatalog(Html html, Object catalog) {
            try {
                Object name = Utils.getBeanProperty(catalog, "name");
                String url = "";
                if (catalog instanceof Openurlresolver) {
                    try {
                        int rnum = Integer.parseInt((String)Utils.getBeanProperty(catalog, "resolvernum"));
                        Openurl openurl = Model.getCurrentModel().getEdition().getOpenurl();
                        if (openurl.getResolverCount() <= rnum) {
                            url = "Refers to OpenURL resolver #" + rnum 
                                + " which is not configured. Please change Resolver Number!";
                        } else {
                            Resolver r = openurl.getResolver(rnum);
                            url = r.getUrl() + " (via OpenURL resolver #" + rnum + ")";
                        }
                    } catch (NumberFormatException nfe) {
                        url = "Resolver Number must be a valid number.";
                    }
                } else {
                    url = (String)Utils.getBeanProperty(catalog, "url");
                }

                if (url.length() > maxCharOfCatalogUrlToShow)
                    url = url.substring(0, maxCharOfCatalogUrlToShow) + "...";

                String desc = parent.catClass2Description.get(catalog.getClass());
                if (catalog instanceof Bookmarklet) {
                    Bookmarklet b = (Bookmarklet)catalog;
                    if (b.getPostdata() != null) {
                        desc += " via POST";
                    } else {
                        desc += " via GET";
                    }
                }

                html.setContent("<table width=\"98%\">"
                    + "<tr><td align=\"left\"><b>" + name + "</b></td>"
                    + "<td align=\"right\">"
                    + "<i>(" + desc + ")</i>"
                    + "</td><tr>"
                    + "<tr colspan=\"2\"><td><small>" + url
                    + "</small></td></tr>"
                    + "</table>"
                );
            } catch (Exception e) {
                e.printStackTrace();
                html.setContent(e.getMessage());
            }
        }

        private static String [] catalogAttributeSortOrder = new String [] { "name", "url" };

        /** 
         *   This method appends a new catalog to the Manage Catalogs Tab    
         *   It takes the help of the drawGrid method in Utils.class 
         * @param flag 
         */
        CatalogController(CatalogList parent, final Object catalog, boolean showRequired) throws Exception {

            this.cat = catalog;
            this.parent = parent;

            Grid g_required = Utils.drawGrid(catalog, requiredAttributes, "Required Settings", catalogAttributeSortOrder);
            Grid g_optional = Utils.drawGrid(catalog, notRequiredAttributes, "Optional Settings", catalogAttributeSortOrder);
            g_required.setHflex("1");

            Hbox xbox = new Hbox(); //Hbox to append the xisbn
            xbox.setVisible(false);

            xbox.setHflex("1");
            Xisbn xisbn=null;

            // as of Castor 1.1, descriptor classes are in subpackage "descriptor"
            Class catClass = catalog.getClass();
            final XMLClassDescriptor cdesc = (XMLClassDescriptor)Utils.getDescriptorClass(catClass).newInstance();
            XMLFieldDescriptor [] child = cdesc.getElementDescriptors();        

            /* Handle xISBN */
            for (XMLFieldDescriptor cd : child) {
                if (!"xisbn".equals(cd.getXMLName()))
                    continue;

                xisbn = (Xisbn)Utils.getBeanProperty(catalog, "xisbn");

                if (xisbn == null) {
                    xisbn = new Xisbn();
                    Utils.setBeanProperty(catalog, "xisbn", xisbn);
                }

                // includeincontextmenu was added later.
                // initialize it to true for the first catalog, false for all others
                if (xisbn.getIncludeincontextmenu() == null) {
                    xisbn.setIncludeincontextmenu(parent.getChildren().size() == 0 ? 
                            XisbnIncludeincontextmenuType.TRUE : XisbnIncludeincontextmenuType.FALSE);
                }

                /* The default help system assumes that the help text depends only on the xisbn.getClass()
                 * and the xmlName.  However, this is not true here where the help text depends on the
                 * catalog class and the xmlName.
                 * We handle this by providing our own HelpTextCreator implementation.
                 */
                Utils.HelpTextCreator htc = new Utils.HelpTextCreator() {
                    public Component createHelpTextComponent(String xmlName) {
                        return Utils.createHelpTextComponent("edition.catalogs." + cdesc.getXMLName() + ".xisbn." + xmlName);
                    }
                };
                Grid g_xisbn_req = Utils.drawGrid(xisbn, Utils.acceptAll, "XISBN", htc, 
                        new String [] { "cues", "includeincontextmenu", "res_id", "opacid", "siteparam" });

                if (g_xisbn_req != null) {
                    g_xisbn_req.setHflex("1");
                    xbox.appendChild(g_xisbn_req);  
                }
                break;
            }

            Hbox obox = new Hbox();
            Hbox rbox = new Hbox();
            obox.setHflex("1");
            rbox.setHflex("1");

            ToggleVisibilityCheckbox vb = new ToggleVisibilityCheckbox(rbox, "Required Settings", showRequired);
            ToggleVisibilityCheckbox ob = new ToggleVisibilityCheckbox(obox, "Optional Settings", false);
            ToggleVisibilityCheckbox xb = null;
            if (xisbn != null)
                xb = new ToggleVisibilityCheckbox(xbox, "Xisbn Settings", false);

            Delbutton del = new Delbutton(parent);
            Utils.bindDependentBeanProperty(cat, "name", del, "label", "Delete '%s'");

            final Html html = new Html();
            describeCatalog(html, catalog);
            Utils.addBeanPropertyChangeListener(catalog, new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    describeCatalog(html, catalog);
                }
            });

            /*
             * To change the positions of catalogs, drag and drop only the HTML
             * areas in which the catalog descriptions are displayed.
             */
            html.setDraggable("true");
            html.setDroppable("true");
            html.addEventListener(Events.ON_DROP, new EventListener() {
                public void onEvent(Event e) {
                    DropEvent de = (DropEvent)e;
                    /* The HTML elements being dragged/dropped are direct children of
                     * their respective CatalogControllers.
                     */
                    CatalogList controller = (CatalogList)CatalogController.this.getParent();
                    CatalogController target = (CatalogController)de.getTarget().getParent().getParent();
                    CatalogController source = (CatalogController)de.getDragged().getParent().getParent();

                    try {
                        Utils.dragAndDrop(source, target, controller);
                        controller.restoreModel();
                    } catch (Exception ex) {
                        MainWindowController.showException(ex);
                    }
                }
            });

            if (g_optional != null) {
                g_optional.setHflex("1");
                obox.appendChild(g_optional);
                obox.setVisible(false);
            }

            rbox.appendChild(g_required);
            rbox.setVisible(showRequired);

            upbutton = new Utils.UpButton(this);
            downbutton = new Utils.DownButton(this);

            appendChild(topline = new Html("<hr width=\"95%\" />"));
            Vbox buttons = new Vbox(new Component[] { upbutton, downbutton });
            Hbox descriptionAndButton = new Hbox(new Component[] { html, buttons } );
            descriptionAndButton.setHflex("1");
            appendChild(descriptionAndButton);
            html.setHflex("9");
            buttons.setHflex("1");

            Utils.addBeanPropertyChangeListener(Model.getCurrentModel().getEdition().getCatalogs(), 
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        CatalogController.this.setUpDownButtonVisibility();
                    }
                });

            // add support for consistency checking here.
            Utils.addBeanPropertyChangeListener(cat, new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    checkConsistency();
                }
            });

            // use dummy labels instead of checkboxes when there are no optional attributes
            // or xISBN to display, respectively.
            Hbox h = new Hbox(new Component [] {
                    new Hbox(new Component [] {
                            vb, 
                            g_optional != null ? ob : new Label(), 
                                    xb != null ? xb : new Label()
                    }),
                    del
            });
            h.setHflex("1");
            appendChild(h);

            appendChild(rbox);
            appendChild(obox);
            appendChild(xbox); 
            this.messageBox = new Hbox();
            appendChild(messageBox);
            this.setHflex("1");

            // perform initial consistency check for newly added catalogs
            checkConsistency();
        }
        
        private void checkConsistency() {
            CatalogConsistencyChecker checker = new CatalogConsistencyChecker();
            checker.checkConsistency(getCatalogObject());

            boolean showWarningMessage = checker.hasErrors();
            if (showWarningMessage)
                Utils.showWarningMessage(checker.getErrorMessageHtml(), messageBox);
            messageBox.setVisible(showWarningMessage);
        }

        private void setUpDownButtonVisibility() {
            topline.setVisible(getPreviousSibling() != null);
            downbutton.setVisible(getNextSibling() != null);
            upbutton.setVisible(getPreviousSibling() != null);
        }

        /**
         * Helper method to get the catalog object bound to the Catalogs Controller.
         * @return
         */
        public Object getCatalogObject() {
            return this.cat;
        }
    }
}
