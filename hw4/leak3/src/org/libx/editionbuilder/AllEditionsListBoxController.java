package org.libx.editionbuilder;

import java.io.File;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

import org.zkoss.zk.ui.Component;

import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.Button;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Image;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Paging;
import org.zkoss.zul.Vbox;

import org.zkoss.zul.event.PagingEvent;

/**
 * This controller controls the all edition list.
 *
 * @author godmar
 */
public class AllEditionsListBoxController extends Listbox {
    static int totalNumberOfEditions;

    /**
     * Retrieve edition id of currently selected edition.
     */
    private String getSelectedEdition() {
        Listitem selected = getSelectedItem();
        return (String)selected.getValue();
    }

    /**
     * User selects entry - display "request ownership" (if applicable)
     * and "make a copy" button in the right side panel.
     *
     * Display information about the selected edition in the left-hand side.
     */
    public void onSelect(Event e) throws Exception {
        try {
        this.selectedEditionInfo.setVisible(true);
        String editionId = getSelectedEdition();
        String shortDesc = EditionCache.getShortDesc(editionId);
        UserInfo ui = UserInfo.getUserInfo();

        rightSide.getChildren().clear();

        // if a user has logged on, place "request ownership" for editions not already owned
        if (ui.getSessionState() == UserInfo.SessionState.LOGGEDON
                && DbUtils.getTableCount("editionMaintainer", 
                    "editionId = ? AND email = ?", editionId, ui.getUserId()) == 0) {
            final Button requestButton = new Button();
            requestButton.setHflex("1");
            requestButton.setLabel("Request Ownership for " + shortDesc);
            requestButton.addEventListener(Events.ON_CLICK, requestOwnership);
            rightSide.appendChild(new Hbox(new Component [] {
                requestButton,
                Utils.createHelpTextComponent("edition.requestownership")
            }));
        }
        final Button makeCopyButton = new Button("Make a copy of " + shortDesc); 
        makeCopyButton.setHflex("1");
        makeCopyButton.addEventListener(Events.ON_CLICK, makeCopy);
        rightSide.appendChild(new Hbox(new Component [] {
            makeCopyButton,
            Utils.createHelpTextComponent("edition.clone")
        }));

        leftSide.getChildren().clear();

        // display information about live revision, if any, or latest revision
        Html info = new Html();
        String liveInfo = "";
        boolean isLive = EditionCache.isLive(editionId);
        String xpiFile, xpiHttpPath;
        String exeFile, exeHttpPath;
        if (isLive) {
            liveInfo = "<a target=\"_new\" href=\"" + Model.getLiveHttpPath(editionId) + "/libx.html\">" + shortDesc + "</a>";
            liveInfo += "<br />";
            xpiFile = Model.getLiveBuildXpiPath(editionId);
            xpiHttpPath = Model.getLiveHttpXpiPath(editionId);
            exeFile = Model.getLiveBuildExePath(editionId);
            exeHttpPath = Model.getLiveHttpExePath(editionId);
        } else {
            // public editions that are not live are probably only legacy editions
            List<Integer> revisions = Model.getRevisions(editionId);
            int revision = revisions.get(revisions.size() - 1);
            liveInfo = "<a target=\"_new\" href=\"" + Model.getTestPageUrl(editionId, revision) + "\">" + shortDesc + "</a>";
            liveInfo += "<br />";
            xpiFile = Model.getBuildXpiPath(editionId, revision);
            xpiHttpPath = Model.getHttpXpiPath(editionId, revision);
            exeFile = Model.getBuildExePath(editionId, revision);
            exeHttpPath = Model.getHttpExePath(editionId, revision);
        }
        if (new File(xpiFile).exists())
            liveInfo += "Download for <a target=\"_new\" href=\"" + xpiHttpPath + "\">Firefox</a>";
        if (Config.ieactivated && new File(exeFile).exists())
            liveInfo += "&nbsp;<a target=\"_new\" href=\"" + exeHttpPath + "\">Internet Explorer</a>";

        liveInfo += "<br />";

        ArrayList<String> owners = EditionCache.getEditionOwners(editionId);
        info.setContent(liveInfo
               + "Maintainer(s): " + owners.toString()
        );
        leftSide.appendChild(info);
        selectedEditionInfo.setVisible(true);
        }
        catch(ArrayIndexOutOfBoundsException a) {
            this.leftSide.appendChild(new Html("This edition is not accessible because it exists only in the database but not in the filesystem.<br>(Error was: "+ a.getMessage()+")"));
        }
        catch(Exception ex) {
            MainWindowController.showException(ex);
        }
    }

    private EventListener makeCopy = new Utils.EventListenerAdapter() {
        public void onEvent(Event e) {
            try {
                String editionId = getSelectedEdition();
                Model m = Model.makeNewModelFromEdition(editionId);
                if (m != null)
                    Model.setCurrentModel(m);
            } catch (Exception ex) {
                MainWindowController.showException(ex);
            }
        }
    };

    private Vbox leftSide, rightSide;
    private Hbox selectedEditionInfo;   // placed by ZUL
    private Html searchResults;         // placed by ZUL
    private Paging paginal;            // placed by ZUL

    private EventListener requestOwnership = new Utils.EventListenerAdapter() {
        public void onEvent(Event e) {
            UserInfo ui = UserInfo.getUserInfo();
            String editionId = getSelectedEdition();
            String shortDesc = EditionCache.getShortDesc(editionId);
            try {
                String user = ui.getUserId();
                // user may have logged off
                if (user == null) {
                    MainWindowController.showStatus(StatusCode.WARNING, 
                        "You must log on before requesting ownership");
                    return;
                }

                ArrayList<String> owners = EditionCache.getEditionOwners(editionId);
                MailSystem.Message msg = new MailSystem.Message();
                for (String owner : owners)
                    msg.addRecipient(owner);

                msg.sendEmail("LibX Edition Ownership Request",
                    user + " has requested ownership of LibX '" + shortDesc
                    + "' (id=" + editionId + ")\n"
                    + "To grant the request, log on to the edition builder, select the edition, and select 'Change Ownership'.  You also have the option of using a shared ownership by selecting 'Retain Shared Ownership'");

                Button requestButton = (Button)e.getTarget();
                requestButton.getParent().insertBefore(
                    new Label("Ownership Requested"),
                    requestButton);
            } catch (Exception ex) {
                MainWindowController.showException(ex);
            }
        }
    };

    public void initialize(final Hbox selectedEditionInfo, final Paging paginal, final Html searchResults) throws Exception {
        this.selectedEditionInfo = selectedEditionInfo;
        this.searchResults = searchResults;

        this.rightSide = new Vbox();
        this.leftSide = new Vbox();
        this.rightSide.setHflex("1");
        this.leftSide.setHflex("1");
        selectedEditionInfo.appendChild(leftSide);
        selectedEditionInfo.appendChild(rightSide);
        selectedEditionInfo.setHflex("1");

        this.paginal = paginal;
        paginal.setAutohide(true);
        paginal.setDetailed(true);
        paginal.addEventListener("onPaging", new Utils.EventListenerAdapter() {
            public void onEvent(Event e) {
                PagingEvent pe = (PagingEvent)e;
                int desiredPage = pe.getActivePage();
                int visibleBegin = desiredPage * paginal.getPageSize();

                try {
                    renderRange(visibleBegin, visibleBegin + paginal.getPageSize());
                    hideButtonsAndClearSelection();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        refreshInternal();

        UserInfo ui = UserInfo.getUserInfo();
        ui.addSignOnListener(new UserInfo.SignOnListener() {
            public void onSignOn(String email) throws Exception {
                // replay onSelect
                if (getSelectedItem() != null)
                    onSelect(null);
            }
        });
    }

    public AllEditionsListBoxController() {
    }

    private void hideButtonsAndClearSelection() {
        selectedEditionInfo.setVisible(false);
        clearSelection();
    }

    /**
     * Render items in range (vb, ve) into visible items.
     */
    private void renderRange(int vb, int ve) throws Exception {
        ve = Math.min(ve, totalNumberOfEditions);
        List<String> editions = loadRecords(vb, ve);

        // make sure there are enough Listitems to hold records.
        for (int i = getItems().size(); i < editions.size(); i++)
            appendChild(new Listitem());

        int i = 0;
        for (String edition : editions) {
            Listitem row = getItemAtIndex(i++);
            try {
                fillInListItem(row, edition);
            } catch (EditionCache.NotFoundError nfe) {
                row.appendChild(new Listcell(edition + " files are not available"));
            }
        }

        // remove any left-over listitems
        int unused = getItems().size() - i;
        while (unused-- > 0)
            removeItemAt(getItems().size() - 1);
    }

    /*
     * Create necessary listcell for a given listitem corresponding to a given record
     */
    void fillInListItem(Listitem row, String editionId) {
        final String shortDesc = EditionCache.getShortDesc(editionId);
        final String versionString = EditionCache.getVersionString(editionId);
        final String lastBuildDate = Utils.formatDate(EditionCache.getLastBuildDate(editionId));
        
        // associate edition id with listitem.value
        row.setValue(editionId);    
        row.getChildren().clear();
        Image img = new Image(EditionCache.getIconPath(editionId));
        img.setHeight("16px");
        Listcell l = new Listcell();
        l.appendChild(img);
        row.appendChild(l);
        row.appendChild(new Listcell(editionId));
        row.appendChild(new Listcell(versionString));
        row.appendChild(new Listcell(lastBuildDate));
        row.appendChild(new Listcell(shortDesc));
    }
    
    private String whereClause = "isPublic = true AND (shortDesc LIKE ? or editionId LIKE ?)";
    private String searchTerm;
    
    public void search(String searchTerm) {
        try {
            this.searchTerm = "%" + searchTerm + "%";
            int hits = DbUtils.getTableCount("editionInfo", whereClause, this.searchTerm, this.searchTerm);
            if ("".equals(searchTerm.trim()))
                searchResults.setContent("");
            else {
                searchResults.setContent("found " + hits + " hits for " + searchTerm);
                Utils.printLog("alleditiontabsearch: hits=%d term=%s", hits, this.searchTerm);
            }
            paginal.setTotalSize(hits);
            paginal.setActivePage(0);
            renderRange(0, paginal.getPageSize());

            // clear selection and edition information after search
            hideButtonsAndClearSelection();
            leftSide.getChildren().clear();
            rightSide.getChildren().clear();
            this.setSelectedItem(null);
        } catch (Exception e) {
            MainWindowController.showException(e);
        }
    }

    /**
     * Reread list of editions from edition database and repopulate listbox.
     */
    private void refreshInternal() throws Exception {
        searchTerm = "%";
        searchResults.setContent("");
        getItems().clear();
        totalNumberOfEditions = DbUtils.getTableCount("editionInfo", whereClause, searchTerm, searchTerm);
        paginal.setTotalSize(totalNumberOfEditions);
        renderRange(0, paginal.getPageSize());
        paginal.setActivePage(0);
    }

    public void refresh() {
        try {
            refreshInternal();
        } catch (Exception e) {
            MainWindowController.showException(e);
        }
    }

    /*
     * Load editions in a given range and cache them.
     */
    private ArrayList<String> loadRecords(final int vb, final int ve) throws Exception {
        String selectPublicEditions = 
            "SELECT editionId, shortDesc FROM editionInfo"
         + " WHERE " + whereClause
         + " ORDER BY shortDesc "
         + " LIMIT " + vb + "," + (ve-vb);

        final ArrayList<String> editions = new ArrayList<String>();
        DbUtils.ResultSetAction addPublicEdition = new DbUtils.ResultSetAction() {
            public void execute(final ResultSet rs) throws SQLException {
                String editionId = rs.getString(1);
                String shortDesc = rs.getString(2);
                EditionCache.addEdition(editionId, shortDesc);
                editions.add(editionId);
            }
        };

        DbUtils.doSqlQueryStatement(selectPublicEditions, addPublicEdition, searchTerm, searchTerm);
        return editions;
    }
}
