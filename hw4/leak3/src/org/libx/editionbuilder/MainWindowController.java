package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.text.DateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Image;
import org.zkoss.zul.Tab;
import org.zkoss.zul.Tabbox;
import org.zkoss.zul.Vbox;
import org.zkoss.zul.Window;

enum StatusCode {
    OK, WARNING, ERROR
}

public class MainWindowController extends Window {
    private Html statushtml;
    private Html morelabel;
    private Button morebutton;
    private Image statusimg;
    private List<Tab> tabs = new ArrayList<Tab>();
    ConsistencyChecker editionConsistencyChecker;
    
    /**
     * Adds a status bar to the desktop
     */
    public MainWindowController() {
        Utils.setDesktopAttribute("rootWindow", this);
    }
    
    
    /**
     * It adds a listener that listens for changes to the current model, 
     * and invokes the showTabs method.
     *  
     */
    public void initialize() {
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    MainWindowController.this.showTabs();
                } catch (Exception e) {
                    MainWindowController.showException(e); 
                }
            }
        });
    }

    private static final String moreString = "More...";
    private static final String lessString = "Less...";

    /**
     * Initialize main window controller.
     */
    public void initialize(Vbox statusbox) {
        Hbox firstline = new Hbox();

        this.statusimg = new Image();
        // see http://www.zkoss.org/javadoc/2.4.0/zul/org/zkoss/zul/Image.html
        this.statusimg.setMold("alphafix");
        firstline.appendChild(this.statusimg);

        this.statushtml = new Html();
        firstline.appendChild(this.statushtml);

        this.morebutton = new Button(moreString);
        this.morebutton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
            public void onEvent(Event e) {
                boolean isVisible = morelabel.isVisible();
                morebutton.setLabel(isVisible ? moreString : lessString);
                morelabel.setVisible(!isVisible);
            }
        });
        this.morebutton.setVisible(false);
        firstline.appendChild(this.morebutton);

        statusbox.appendChild(firstline);

        this.morelabel = new Html();
        this.morelabel.setVisible(false);
        statusbox.appendChild(this.morelabel);
        final Hbox editionConsistencyCheckMessages = new Hbox();
        this.editionConsistencyChecker = new EditionConsistencyChecker(editionConsistencyCheckMessages);
        statusbox.appendChild(editionConsistencyCheckMessages);
        UserInfo.getUserInfo().addSignOffListener(hideTabs);

        showStatus(StatusCode.OK, "LibX edition builder started");
    }

    public static void showStatus(StatusCode statuscode, String message) {
        showStatus(statuscode, message, null);
    }

    /**
     * Show status, including an long message.
     * The longmsg must be text, not html.
     */
    public static void showStatus(StatusCode statuscode, String htmlMessage, String longmsg) {
        MainWindowController mwc = (MainWindowController)Utils.getDesktopAttribute("rootWindow");
        if (longmsg != null) {
            longmsg = Utils.escapeHtmlEntities(longmsg).replaceAll("\\n", "<br />");
            mwc.morelabel.setContent(longmsg);
            mwc.morebutton.setVisible(true);
        } else {
            mwc.morelabel.setContent("");
            mwc.morebutton.setVisible(false);
        }
        mwc.updateStatusBox(statuscode, htmlMessage);
    }

    /**
     * Show information about an exception in the main window.
     */
    public static void showException(Throwable exc) {
        StringWriter s = new StringWriter();
        PrintWriter w = new PrintWriter(s);
        exc.printStackTrace(w);
        Throwable cause = exc.getCause();
        if (cause != null) {
            w.println("\nCaused by: " + cause);
            cause.printStackTrace(w);
        }
        w.close();
        showStatus(StatusCode.ERROR, exc.toString(), s.toString());

        if (Config.verbose) {
            Utils.printLog("%s", s.toString());
        } else {
            Utils.printLog("%s", exc.toString());
        }
    }

    /**
     * Update the status box with given code, message, and information about
     * current model, if any.
     */
    void updateStatusBox(StatusCode statuscode, String message) {
        DateFormat shortTime = DateFormat.getTimeInstance();
        DateFormat defaultDate = DateFormat.getDateInstance();
        Date now = new Date();
        String html = shortTime.format(now)+" "+defaultDate.format(now)+" ";

        Model m = Model.getCurrentModel();
        if (m != null) {
            String id = m.getEdition().getId();
            int rev = m.getRevision();
            html += "edition <a target=\"_new\" href=\"" 
                    + Model.getTestPageUrl(id, rev)
                    + "\">" + id + " rev " + rev + "</a> ";
            html += message;
            if (statuscode == StatusCode.OK && editionConsistencyChecker.wasRun() && !editionConsistencyChecker.hasErrors()) {
                html += " Click <a target=\"_new\" href=\"" 
                        + Model.getTestPageUrl(id, rev)
                        + "\">here</a> to try out this configuration.";
            }
        } else {
            html += message;
        }
        this.statushtml.setContent(html);

        switch (statuscode) {
        case ERROR: 
            statusimg.setSrc(System.getProperty("eb.errorimage"));
            break;
        case WARNING: 
            statusimg.setSrc(System.getProperty("eb.warningimage"));
            break;
        case OK: 
            statusimg.setSrc(System.getProperty("eb.okimage"));
            break;
        default:
            System.out.println("Illegal statuscode: " + statuscode);
        }
    }
    
    
    /**
     * Gets the array of tabs from the zul 
     * @param tbs
     */
    public void registerTabs(Tab[] tbs) {
        this.tabs.addAll(Arrays.asList(tbs));
    }

    public void registerTabbox(Tabbox tabbox) {
        tabbox.addEventListener(Events.ON_SELECT, new Utils.EventListenerAdapter(false) {
            public void onEvent(Event e) {  
                Utils.printLog("User visited the tab: %s", e.getTarget().getId().toString());
            }                                      
        });
    }

    public void registerTab(Tab tbs) {
        this.tabs.add(tbs);
    }
    
    /**
     * Un-hides the hidden tabs and selects the first one
     *
     */
    public void showTabs() {
        for (Tab t : this.tabs) {
            t.setVisible(true);
        }
        
        this.tabs.get(0).setSelected(true);        
    }
    
    public void hideTabs() {
        for (Tab t : this.tabs) {
            t.setVisible(false);
        }
    }
    
    public static MainWindowController getMainWindowController() {
        return (MainWindowController)Utils.getDesktopAttribute("rootWindow");
    }
    
    UserInfo.SignOffListener hideTabs = new UserInfo.SignOffListener() {
        public void onSignOff() throws Exception {
            hideTabs();
        }
    };
}
