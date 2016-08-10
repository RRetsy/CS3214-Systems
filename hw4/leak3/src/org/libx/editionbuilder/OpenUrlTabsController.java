package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import java.util.concurrent.Future;

import org.libx.editionbuilder.Utils.BeanPropertyComponentCreator;

import org.libx.xml.Openurl;
import org.libx.xml.Resolver;

import org.zkoss.zk.ui.Component;

import org.zkoss.zk.ui.event.DropEvent;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.Button;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.Rows;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Vbox;

public class OpenUrlTabsController extends Hbox {
    private ResolverList resolverList;
    private Vbox leftList;
    private Label errorLabel;

    /**
     * When the edition builder starts up, a background thread will attempt to
     * retrieve information about which OpenURL resolver is registered for the
     * IP from which the client connects.
     * After 10 seconds, a timer will go off to retrieve the results of the
     * query.
     * This scheme is used because only the GUI thread can create components.
     */
    public static int detectResolverTimeout = 10000;
    enum DetectorMode {
        SYNCHRONOUS, ASYNCHRONOUS
    };

    public final class Detector extends Timer implements Runnable {
        private String clientIP;
        private String institutionId; // OCLC institutionId;
        private Future<ArrayList<String>> opacBaseList;
        private List<Resolver> detectedResolvers;

        String getInstitutionId() {
            return institutionId;
        }

        Future<ArrayList<String>> getOpacBaseList() {
            return opacBaseList;
        }

        Detector(String clientIP, DetectorMode mode) {
            super(detectResolverTimeout);
            this.clientIP = clientIP;
            if (mode == DetectorMode.SYNCHRONOUS) {
                detectedResolvers = detectResolvers(clientIP);
                errorLabel.setValue(detectedResolvers.size() + " resolver(s) found.");
                showResolvers(detectedResolvers);
            } else {
                // attach and start timer
                leftList.appendChild(this);
                start();
                new Thread(this).start();
            }
        }

        /**
         * Return list of detected OpenURL resolvers.
         * List may be empty, but is never null.
         */
        private List<Resolver> detectResolvers(String ipAddr) {
            try {
                OpenurlDetector detector = new OpenurlDetector();
                List<Resolver> r = detector.detect(ipAddr);
                this.opacBaseList = detector.getOpacBaseList();
                this.institutionId = detector.getInstitutionId();
                return r;
            } catch (Exception e) { 
                /* If the error msg here says:
                 * unable to find FieldDescriptor for 'head' in ClassDescriptor of records
                 * then this probably means OCLC returns some HTML error message that
                 * castor attempted to parse.
                 */
                System.out.println("Error occurred while contacting OCLC for IP: " + ipAddr);
                if (Config.verbose)
                    e.printStackTrace();
                else
                    System.out.println(e);
            }
            return Collections.emptyList();
        }

        /**
         * Background thread running the openurl detection.
         */
        public void run() {
            this.detectedResolvers = detectResolvers(clientIP);
        }

        /**
         * onTimer event: this event if executed after 5 seconds by the
         * main GUI thread, hence it has the ability to create and manipulate
         * GUI components.
         */
        public synchronized void onTimer(Event e) {
            showResolvers(this.detectedResolvers);
        }
        
        
        private synchronized void showResolvers(List<Resolver> detectedResolvers) {
            if (detectedResolvers == null)
                return;

            for (Resolver r : detectedResolvers) {
                synchronized (this) {
                    final Vbox box = new Vbox();
                    box.appendChild(new Html("<hr />")); // may not be necessary once hbox is styled.
                    String displayUrl = r.getUrl();
                    if (displayUrl.length() > 25)
                        displayUrl = displayUrl.substring(0, 25) + "...";
                    String msg = "IP address " + clientIP + " may have access to '" 
                        + r.getName() 
                        + "' ";
                    if (r.getImage() != null)
                        msg += "<img src=\"" + r.getImage() + "\" />";
                    msg += " at <a title=\"" + r.getUrl() + "\" target=\"_new\" href=\"" 
                        + r.getUrl() + "\">" + displayUrl + "</a>";
                    box.appendChild(new Html(msg));
                    Button b = new Button("Import This Resolver");
                    final Resolver resolvertoadd = r;
                    b.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                        public void onEvent(Event e) {
                            try {
                                // download openurl.image (if one is specified) and 
                                // transform openurl.image into chrome:// form.
                                String imgURL = resolvertoadd.getImage();
                                if (imgURL != null) {
                                    imgURL = FileTabController.addNewFile("chrome/libx/skin/libx", imgURL);
                                    resolvertoadd.setImage(imgURL);
                                }
                                Utils.printLog("user added detected openurl resolver: %s", resolvertoadd.getUrl());
                                resolverList.addResolver(resolvertoadd);
                                box.setVisible(false);
                            } catch (Exception ex1) {
                                MainWindowController.showException(ex1);
                            }
                        }
                    });
                    Button b2 = new Button("No thanks");
                    b2.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                        public void onEvent(Event e) {
                            box.setVisible(false);
                        }
                    });
                    Hbox bhbox = new Hbox();
                    bhbox.appendChild(b);
                    bhbox.appendChild(b2);
                    box.appendChild(bhbox);
                    leftList.appendChild(box);
                }
            }
            Utils.setDesktopAttribute("oclcInstitutionId", this.institutionId);
        }
    }

    public void initialize(Vbox llist, Vbox rlist, Label errorLabel) {
        this.resolverList = (ResolverList)rlist;
        this.leftList = llist;
        this.errorLabel = errorLabel;

        try {
            org.zkoss.zk.ui.Session session = org.zkoss.zk.ui.Sessions.getCurrent();
            String clientIP = session.getRemoteAddr();
            // a VT address to test 
            // clientIP = "128.173.236.138";
            // clientIP = "128.230.18.35"; // syracuse
            // clientIP = "62.134.197.1"; // Humboldt

            new Detector(clientIP, DetectorMode.ASYNCHRONOUS);
        } catch (Exception e) {
            // MainWindowController.showException does not work here.
            e.printStackTrace();
        }

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

    private void loadModel(Model m) throws Exception {
        resolverList.loadModel(m);
    }

    /**
     * Search OCLC registery (called from ZUL GUI)
     */
    public void searchRegistry(String ipAddr) {
        try {
            Utils.printLog("openurlregistry search: ipaddrentered=%s", ipAddr);
            Detector d = new Detector(ipAddr, DetectorMode.SYNCHRONOUS);

            /* If an institution id was obtained as part of the search, initiate
             * a catalog probe here.
             */
            String id = d.getInstitutionId();
            if (id != null && d.getOpacBaseList() != null) {
                // wait for opac base request to complete and be cached
                // (startOCLCProbe will retrieve it from the cache)
                d.getOpacBaseList().get();  
                CatalogsTabController.Urlbox urlbox = 
                    (CatalogsTabController.Urlbox)Utils.getDesktopAttribute("autodetecturlbox");
                urlbox.startOCLCProbe(id);
            }
        } catch (Exception e) {
            MainWindowController.showException(e);
        }
    }

    /**
     * Add a new resolver (called from ZUL GUI)
     */
    public void addResolver() {
        try {
            resolverList.addResolver(new Resolver());
        } catch (Exception e) {
            MainWindowController.showException(e);
        }
    }

    public static class ResolverList extends Vbox implements Utils.CanRestoreModel {

        public static String resolverFileName = "/resolvers.default";

        /**
         * Loads the model and populates the list of resolvers each time the model is loaded.
         * @param model
         * @throws Exception
         */
        private void loadModel(final Model model) throws Exception {
            populateList(model);

            model.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("resolverList".equals(evt.getPropertyName())) {
                        try {
                            populateList(model);
                        } catch (Exception e) {
                            MainWindowController.showException(e);
                        }
                    }
                }
            });
        }

        /**
         * Populates the initial list of Resolvers
         * @param model
         * @throws Exception
         */
        private void populateList(Model model) throws Exception {
            getChildren().clear();
            Openurl openurl = model.getEdition().getOpenurl();
            Resolver[] res = openurl.getResolver();

            for(Resolver modelres : res) {
                appendChild(new ResolverController(this, modelres, resolverComponentCreatorList));
            }

            // set up/down button visibility
            for (Object c : getChildren()) {
                ((ResolverController)c).setUpDownButtonVisibility();
            }
            this.setVisible(openurl.getResolverCount() > 0);
        }

        /**
         * Adds a resolver to the model.
         * Also sets the values of the required attributes if necessary 
         */
        void addResolver(Resolver resolver) throws Exception {
            Utils.setRequiredAttributes(resolver);
            // will trigger call to populateList
            Model.getCurrentModel().getEdition().getOpenurl().addResolver(resolver);
        }

        /**
         * Initialize a set of bean property component creators from configuration file.
         */
        static private ArrayList<BeanPropertyComponentCreator> resolverComponentCreatorList;
        static {
            ReadObjectConfiguration conf = new ReadObjectConfiguration();
            try {
                Scanner scanner = new Scanner(new File(Config.xmlpath + resolverFileName));
                ArrayList<Object> creatorList = conf.read(scanner);
                resolverComponentCreatorList = new ArrayList<BeanPropertyComponentCreator>(creatorList.size());
                for (Object o : creatorList) {
                    if (!(o instanceof BeanPropertyComponentCreator)) {
                        System.out.println("Error: " + o.getClass() + " must implement BeanPropertyComponentCreator");
                        continue;
                    }
                    resolverComponentCreatorList.add((BeanPropertyComponentCreator)o);
                }
            } catch (Exception e) {
                Utils.logUnexpectedException(e);
            }
        }

        /**
         * Restores the model if the order of resolvers is rearranged, 
         * as in case of drag and drop.
         */
        public void restoreModel(){    
            Openurl openurl = Model.getCurrentModel().getEdition().getOpenurl();
            int children = this.getChildren().size();
            Resolver [] res = new Resolver[children];

            for (int i = 0; i < children; i++) {
                res[i] = ((ResolverController)this.getChildren().get(i)).getResolver();
            }
            openurl.setResolver(res);
        }

        /**
         * Adds an Vbox component that contains the Resolver catalog
         * @param resolver
         * @param displayResolverType
         * @return
         */

        public static class  ResolverController extends Vbox {

            private Resolver res;
            private ResolverList parent;
            private Button downbutton, upbutton;
            private Html topline;
            
            /**
             * The delete button deletes a resolver from the view as well as the model
             * @author tgaat
             *
             */
            public static class Delbutton extends Button {
                private ResolverList clist;
                public Delbutton(ResolverList grandparent) {
                    clist = grandparent;
                }    
                public void onClick(Event e) throws Exception {  
                    Delbutton d = (Delbutton)e.getTarget();
                    ((ResolverController)d.getParent()).setParent(null);
                    clist.restoreModel();
                    clist.setVisible(Model.getCurrentModel().getEdition().getOpenurl().getResolverCount() > 0);
                }
            }

            public ResolverController(ResolverList parent, final Resolver resolver, 
                    List<BeanPropertyComponentCreator> componentCreators) throws Exception {

                Grid g = new Grid();
                g.setWidth(Config.zkGridWidth);
                
                this.res = resolver;
                this.parent = parent;
                setDroppable("true");
                setDraggable("true");
                Rows rr = new Rows();
                Columns cc = new Columns();

                Column col = new Column("Option");
                cc.appendChild(col);
                col = new Column("Value");
                cc.appendChild(col);
                g.appendChild(cc);

                // XXX this code is too similar to drawGrid.  Refactor.
                for (BeanPropertyComponentCreator restypeentry : componentCreators) {
                    Row r = new Row();
                    String whichkey = restypeentry.getPropertyName().toString();

                    Component help = Utils.createHelpText(Resolver.class, whichkey);
                    r.appendChild(help);

                    Component c = restypeentry.createComponentForProperty(resolver);
                    r.appendChild(c);

                    rr.appendChild(r);
                    g.appendChild(rr);
                }
                g.setWidth(Config.zkGridWidth);

                Delbutton d = new Delbutton(this.parent);
                Utils.bindDependentBeanProperty(resolver, "name", d, "label", "Delete Resolver '%s'");

                final Html html = new Html();
                describeResolver(html, resolver);
                Utils.addBeanPropertyChangeListener(resolver, new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        describeResolver(html, resolver);
                    }
                });
                upbutton = new Utils.UpButton(this);
                downbutton = new Utils.DownButton(this);

                this.appendChild(topline = new Html("<hr width=\"95%\" />"));
                Hbox descriptionAndButtons = new Hbox(new Component[] { html, new Vbox(new Component[] { upbutton, downbutton }) });
                this.appendChild(descriptionAndButtons);
                descriptionAndButtons.setHflex("1");
                Utils.setHflexOnChildren(descriptionAndButtons, "9", "1");
                html.setWidth("100%");

                Utils.addBeanPropertyChangeListener(Model.getCurrentModel().getEdition().getOpenurl(), 
                    new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            ResolverController.this.setUpDownButtonVisibility();
                        }
                    });
                this.appendChild(g);
                this.appendChild(d);
                this.setHflex("1");
            }

            private void setUpDownButtonVisibility() {
                topline.setVisible(getPreviousSibling() != null);
                downbutton.setVisible(getNextSibling() != null);
                upbutton.setVisible(getPreviousSibling() != null);
            }

            /**
             * Place a description of this resolver into provided html element
             */
            void describeResolver(Html html, Resolver resolver) {
                String name = resolver.getName();
                String url = resolver.getUrl();
                html.setContent("<table width=\"98%\">"
                    + "<tr><td align=\"left\"><b>" + name + "</b></td>"
                    + "<td align=\"right\">"
                    + "<i>(" + resolver.getType() + ")</i>"
                    + "</td><tr>"
                    + "<tr colspan=\"2\"><td><small>" + url
                    + "</small></td></tr>"
                    + "</table>"
                );
            }

            /**
             * Helper method that returns the model Resolver object bound to this controller 
             * @return
             */

            public Resolver getResolver() {
                return this.res;
            }

            /**
             * Implements the drag and drop of Resolvers.
             * @param de
             */
            public void onDrop(DropEvent de) {
                try {
                    Utils.dragAndDrop(de.getDragged(), this, parent);
                } catch (Exception e) {
                    Utils.logUnexpectedException(e);
                }
                parent.restoreModel();
            }
        }
    }
}
