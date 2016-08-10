package org.libx.editionbuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.File;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.libx.editionbuilder.UserInfo.SessionState;

import org.libx.xml.Edition;
import org.libx.xml.Name;
import org.libx.xml.Option;
import org.libx.xml.Options;
import org.libx.xml.types.OptionKeyType;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.HtmlBasedComponent;

import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.A;
import org.zkoss.zul.Box;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Html;
import org.zkoss.zul.Image;
import org.zkoss.zul.Label;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listhead;
import org.zkoss.zul.Listheader;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Vbox;

import org.zkoss.zul.ext.Paginal;

import org.libx.editionbuilder.Utils;


/**
 * This controller controls the functionality of authentication of the user, loading the revisions 
 * and the revision lists.
 * @author tgaat
 *
 */
public class EditionListVboxController extends Vbox {

    private EdList edlist;
    private Html noEditionsMsg;
    private HashMap<String, String> id2desc = new HashMap<String,String>();
    private RevisionListVboxController rlvc;
    private Vbox donateBox = new Vbox();
    private HomepageBoxController homepageBoxController;
    private Hbox selectEditionsLabel;
    private HelpDialog helpDialogWindow;
    private boolean sortedByDescription;

    public void initialize(RevisionListVboxController revBox, HomepageBoxController homepgBox, Hbox label, final A sortByButton, HelpDialog helpDialogWindow) {
        this.rlvc = revBox;
        this.homepageBoxController = homepgBox;     
        this.selectEditionsLabel = label;
        this.helpDialogWindow = helpDialogWindow;
        this.sortedByDescription = sortByButton.getLabel().equals("Sort By Id");
        sortByButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
            public void onEvent(Event e) {
                sortedByDescription = !sortedByDescription;
                sortByButton.setLabel(sortedByDescription ? "Sort By Id" : "Sort By Description");
                try {
                    reloadCurrentEditionList(null);
                } catch (Exception exc) {
                    MainWindowController.showException(exc);
                }
            }
        });
    }
    /**
     * Adds the SignOn listeners to a vector of listeners which is maintained by the UserInfo class.
     *
     */
    public EditionListVboxController() {

        UserInfo.getUserInfo().addSignOnListener(loadEdList);
        UserInfo.getUserInfo().addSignOnListener(newModelAppend);
        UserInfo.getUserInfo().addSignOffListener(signoffOperations);
        UserInfo.getUserInfo().addSignOffListener(hideLists);

        /**
         * This listener listens to loading of a new model. 
         * If the corresponding edition of the newly loaded model is not present in the database, 
         * then it is added into the database and the edition list is refreshed, only if the 
         * user is in the logged on state.
         * Also attaches a build button, even if the user is in a logged off state and a new model is loaded.
         *  
         */
        Model.addCurrentModelChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                try {
                    final Model m = (Model)evt.getNewValue();
                    addListenerToName(m);
                    addListenerToOptions(m);
                    
                    if (UserInfo.getUserInfo().getSessionState() == SessionState.LOGGEDON) {
                        enterNewEditionInDatabase(m.getEdition());

                        rlvc.setVisible(true);
                        rlvc.loadRevList(m.getEdition().getId());
                    }

                    if (UserInfo.getUserInfo().getSessionState() == SessionState.LOGGEDOFF) { 
                        rlvc.setVisible(true);
                        rlvc.attachBuildButton(m.getEdition().getId(), m.getRevision());                       
                    }

                } catch (Exception e) {
                    MainWindowController.showException(e); 
                }
            }


            /**
             * Adds a listener to the name property of the model, to update the description field in the 
             * database and consequently the edition list. 
             * @param m
             */

            private void addListenerToName(final Model m) {

                Name name = m.getEdition().getName();

                name.addPropertyChangeListener(new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        try {
                            if ("edition".equals(evt.getPropertyName())) {          
                                String editionId = m.getEdition().getId();
                                DbUtils.doSqlUpdateStatement(
                                        "UPDATE editionInfo SET shortDesc = ? WHERE editionId = ?", 
                                        (String)evt.getNewValue(),
                                        editionId);
                                EditionListVboxController.this.reloadCurrentEditionList(editionId);
                                EditionCache.invalidateEdition(editionId);
                            }
                        } catch (Exception exc) {
                            MainWindowController.showException(exc);
                        }
                    }
                });
            }


            /**
             * Adds a listener to the ICON option field, and if it changes this listener invalidates the 
             * EditionCache and reloads the Edition List to reflect the new icon.   
             * @param m
             */
            private void addListenerToOptions(final Model m){

                Options options = m.getEdition().getOptions();
                Option option = null;
                
                for (Option modelOpt : options.getOption()) {
                    if (modelOpt.getKey().equals(OptionKeyType.ICON)) {   
                        option = modelOpt;
                        break;
                    }
                }
                if (option == null)
                    throw new Error("Internal error: config.xml is missing option.icon");

                option.addPropertyChangeListener(new PropertyChangeListener(){
                    public void propertyChange(PropertyChangeEvent evt){
                        try {
                            if("value".equals(evt.getPropertyName())) {
                                String editionId = m.getEdition().getId();
                                EditionCache.invalidateEdition(editionId);
                                EditionListVboxController.this.reloadCurrentEditionList(editionId);
                            }
                        } catch(Exception exc) {
                            MainWindowController.showException(exc);
                        }   
                    }
                });
            }

        });

        this.edlist = new EdList();
        this.edlist.setVisible(false);

        edlist.setHflex("1");
        this.appendChild(edlist);
        this.appendChild(this.noEditionsMsg = returnNoEditionsHtmlMessage());
        this.noEditionsMsg.setVisible(false);
        donateBox.setHflex("1");
        this.setHflex("1");
        this.appendChild(donateBox);
    }

    void displaySelectedEditionInformation(String id) throws Exception {
        homepageBoxController.makeChangeLinkVisible(id);
    }

    /**
     * Helper class for Listbox with headers.
     */
    static class ListboxWithHeader extends Listbox {
        ListboxWithHeader(String [] headerLabels) {
            this.setHflex("1");
            this.setMold("paging");
            this.setVisible(true);

            Listhead lh = new Listhead();
            for (String headerLabel : headerLabels) {
                lh.appendChild(new Listheader(headerLabel));
            }
            lh.setHflex("1");
            ((HtmlBasedComponent) lh.getChildren().get(0)).setWidth("25px");
            ((HtmlBasedComponent) lh.getChildren().get(1)).setWidth("125px");

            this.appendChild(lh);
        }
    }

    /**
     * This class contains the list of editions and also loads the revisions of the edition
     * onClick
     * @author tgaat
     *
     */
    public class EdList extends ListboxWithHeader {

        EdList() {
            super(new String[] { " " /* icon */, "Id", "Description" });
            this.setHflex("1");
        }

        public void onSelect(Event e) {
            try {
                rlvc.setVisible(true);
                String id = (String)getSelectedItem().getValue();
                selectEdition(id);
            } catch (Exception ex) {
                MainWindowController.showException(ex);
            }
        }

        /**
         * Perform actions necessary when an edition is selected.
         */
        private void selectEdition(final String id) throws Exception {
            this.setHflex("1");
            donateBox.getChildren().clear();
            // display information about live revision, if any, or latest revision

            displaySelectedEditionInformation(id);
            Html info = new Html();
            ArrayList<String> owners = EditionCache.getEditionOwners(id);
            String escapedOwners = Utils.escapeHtmlEntities(owners.toString());
            final String description = Utils.escapeHtmlEntities(id2desc.get(id));
            info.setContent("Maintainer(s) for " + description + " are: " + "<br>" + escapedOwners );

            donateBox.appendChild(info);

            Button donate = new Button("Change Ownership for " + id2desc.get(id));
            Label donateStatus = new Label();
            addDonateListeners(id, donate, donateStatus);

            Button del = new Button("Release Ownership of " + id2desc.get(id));
            addDeleteListeners(id, del);

            final Button help = new Button("Help me with "+id2desc.get(id));
            help.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    helpDialogWindow.show(id);
                }
            });

            final Checkbox isBlessedCheckbox = new Checkbox(" Edition is endorsed");
            final Checkbox isPublicCheckbox = new Checkbox(" Edition is public");

            setFlags(id, isPublicCheckbox, "isPublic");
            setFlags(id,isBlessedCheckbox, "isBlessed");

            updateFlagsOnCheck(id, isBlessedCheckbox, "isBlessed");
            updateFlagsOnCheck(id, isPublicCheckbox, "isPublic");

            Component [] lines = new Component [] {
                    new Hbox(new Component[] { donate, Utils.createHelpTextComponent("edition.donate") }),
                    new Hbox(new Component[] { del, Utils.createHelpTextComponent("edition.delete") }),
                    new Hbox(new Component [] {
                            isBlessedCheckbox, 
                            Utils.createHelpTextComponent("edition.isblessed")
                    }),
                    new Hbox(new Component [] {
                            isPublicCheckbox,
                            Utils.createHelpTextComponent("edition.ispublic")
                    }),
                    new Hbox(new Component[] {
                            help, Utils.createHelpTextComponent("edition.help")
                    }),
                    donateStatus
            };

            for (Component c : lines) {
                if (c instanceof Hbox) {
                    Utils.setBeanProperty(c, "hflex", "1");
                    List hboxChildren = ((Hbox)c).getChildren();
                    Utils.setBeanProperty(hboxChildren.get(0), "hflex", "1");
                    Utils.setBeanProperty(hboxChildren.get(1), "hflex", "1");
                }
            }

            Vbox vb = new Vbox(lines);
            vb.setHflex("1");

            donateBox.appendChild(vb);

            donateBox.setVisible(true);

            rlvc.loadRevList(id);
        }

        /**
         * Sets the isBlessed and isPublic flag and appends a confirmation message to the vbox that is passed.
         * The flag is determined, when the concerned checkbox is checked.
         * @param id
         * @param checkbox
         * @param label
         * @param vbox
         */
        private void updateFlagsOnCheck(final String id, final Checkbox checkbox, final String label) {

            checkbox.addEventListener(Events.ON_CHECK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {  
                    try {
                        boolean flag = checkbox.isChecked();
                        String stmt = "UPDATE editionInfo SET "+ label +" = "+flag+" WHERE editionId = '"+id+"'";
                        DbUtils.doSqlUpdateStatement(stmt);

                        /* Fix this - move the doSqlUpdate in separate function and create separate event
                         * handlers for isBlessed and isPublic flags.
                         */
                        if (label.equals("isPublic")) {
                            if (EditionCache.getShortDesc(id).isEmpty()) {
                                Messagebox.show("Editions without description cannot be made public!");
                                checkbox.setChecked(false);
                                return;
                            }

                            if(EditionCache.isLive(id)) {
                                if(flag) {
                                    int liveRevisionNumber = EditionCache.getLiveRevisionNumber(id);
                                    Model m = new Model(id, liveRevisionNumber, Model.Autosave.FALSE);
                                    m.addCatalogsToDatabase();
                                }
                                else {
                                    Model.deleteCatalogsFromDatabase(id);
                                }
                            }
                        }
                        EditionCache.invalidateEdition(id);
                    } catch (Exception exc) {
                        MainWindowController.showException(exc);
                    }
                }      
            });
        }

        /**
         * It retrieves the values of isBlessed and isPublic and sets the checkboxes as checked or unchecked accordingly. 
         * @param id
         * @param checkbox
         * @param label
         * @throws Exception
         */
        private void setFlags(final String id, final Checkbox checkbox, String label) throws Exception {
            String stmt1 = "SELECT "+label+" FROM editionInfo WHERE editionId = '"+id+"'";
            DbUtils.ResultSetAction setFlagAction = new DbUtils.ResultSetAction(){
                public void execute(final ResultSet rs) throws SQLException {
                    checkbox.setChecked(rs.getBoolean(1)); 
                }
            };
            DbUtils.doSqlQueryStatement(stmt1, setFlagAction);
        }

        /**
         * Adds event listeners to the delete button
         * @param id
         * @param del
         * @throws Exception
         */

        private void addDeleteListeners(final String id, Button del) throws Exception {
            del.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {                
                    deleteEdition(id);                                     
                }      
            });
        }

        /**
         * Adds event listeners to the donate button. On Click of the button, a textbox is attached to the 
         * visual component and also a submit button. 
         * @param id
         * @param desc
         * @param donate
         * @throws Exception
         */
        private void addDonateListeners(final String id, Button donate, final Label donateBoxStatus) throws Exception{
            donate.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e) {
                    new DonateSubmitButton(id, donateBox, donateBoxStatus);
                }       
            });
        }
    }

    /**
     * Deletes an edition from the editionInfo table and editionMaintainer tables.   
     * @param id
     * @param flag
     */

    public void deleteEdition(final String id) {
        try {
            int rc = Messagebox.show("After releasing ownership you will not have access to "
                    + " any revision of this edition in the future."
                    + " If you were the last remaining owner of this edition, it will be deleted."
                    + " Are you sure you wish to release ownership?",
                    "Confirm Releasing Ownership/Delete", 
                    Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION);
            if (rc != Messagebox.YES) {
                MainWindowController.showStatus(StatusCode.OK, "Ownership was not released");
                return;
            }

            String stmt1="DELETE FROM editionMaintainer WHERE editionId = ? AND email = ?";
            DbUtils.doSqlUpdateStatement(stmt1, id, UserInfo.getUserInfo().getEmail());
            String status = "Ownership for edition " + id + " released.";

            if (DbUtils.getTableCount("editionMaintainer", "editionId = ?", id) == 0) {
                String stmt =  "DELETE FROM editionInfo WHERE editionInfo.editionId = '"+id+"'";
                DbUtils.doSqlUpdateStatement(stmt);   
                status += " Edition deleted.";
            }

            Model.deleteCatalogsFromDatabase(id);

            EditionCache.invalidateEdition(id);
            reloadCurrentEditionList(null);
            MainWindowController.showStatus(StatusCode.OK, status);
            EditionListVboxController.this.rlvc.setVisible(false);
        }
        catch (Exception exc) {
            MainWindowController.showException(exc);
        }
    }


    /**
     * On Click of the submit button, the edition is donated to  
     * the maintainer whose email is present in the appended textbox. 
     * Also, a checkbox allows a maintainer to indicate, 
     * if he/she wants to share the rights of the edition with another
     * maintainer.
     * @author tgaat
     *
     */
    public class DonateSubmitButton extends Button {
        private String editionId;
        private Textbox receiverEmail;
        private Checkbox sharedOwnershipCheckbox;
        private Vbox form;
        private Label status;

        DonateSubmitButton(String editionId, Box donateBox, Label status) {

            this.setLabel("Submit");
            this.editionId = editionId;
            this.status = status;

            this.form = new Vbox(new Component [] {
                    new Label("Grant ownership of "+id2desc.get(editionId)+" to user:"),
                    new Hbox(new Component [] {
                            this.receiverEmail = new Textbox(),
                            this
                    }),
                    this.sharedOwnershipCheckbox = new Checkbox(" Retain shared ownership"),
            });

            this.receiverEmail.setWidgetListener("onKeyDown", "fireSubmitOnEnter(event,this,this.$f('"+this.getId()+"'))" );
            sharedOwnershipCheckbox.setChecked(true);
            donateBox.appendChild(form);
        }

        public void onClick(Event e) {  
            try {
                String donatedTo = receiverEmail.getValue();
                if (donatedTo.equals("")) {
                    status.setValue("Please enter a valid email");
                    return;
                }

                int i = DbUtils.getTableCount("userInfo", "email = ?", donatedTo);
                if (i == 0) {
                    status.setValue("User '" + donatedTo + "' is not present in the system");
                    return;
                }

                int n = DbUtils.getTableCount("editionMaintainer", 
                        "email = ? AND editionId = ?", donatedTo, editionId);
                if (n > 1) {
                    /* (email,editionId) should form a primary key.  Please fix this by adding actual keys. */
                    Utils.printLog("Warning: DB inconsistent. Multiple editionMaintainer entries for %s/%s", 
                            donatedTo, editionId);
                }
                if (n == 1) {
                    status.setValue(donatedTo + " already has ownership of " + editionId);
                    return;
                }

                final ArrayList<String> owners = EditionCache.getEditionOwners(editionId);
                MailSystem.Message msys = new MailSystem.Message();
                try {
                    if (sharedOwnershipCheckbox.isChecked()) {
                        DbUtils.doSqlUpdateStatement(
                                "INSERT INTO editionMaintainer(email,editionId) VALUES (?, ?)",
                                donatedTo, editionId);
                    } else {
                        msys.addRecipient(UserInfo.getUserInfo().getEmail());
                        msys.sendEmail("Ownership Released", "You have released ownership for " + editionId);
                        DbUtils.doSqlUpdateStatement(
                                "UPDATE editionMaintainer SET email = ? WHERE editionId = ? AND email = ?",
                                donatedTo, editionId, UserInfo.getUserInfo().getEmail());
                        EditionListVboxController.this.rlvc.setVisible(false);
                    }
                    EditionCache.invalidateEdition(editionId);
                    status.setValue("Ownership for " + editionId + " was granted to " + donatedTo);
                    reloadCurrentEditionList(editionId);

                    for (String owner: owners) {
                        if (owner.equals(donatedTo))
                            msys.addRecipient(donatedTo);
                        else
                            msys.addRecipientToCc(owner);
                        }
                    msys.sendEmail("LibX Edition Builder: ownership granted to a new user",
                            "ownership to the edition " + editionId + " has been granted to " + donatedTo);
                } catch(Exception ex){
                    MainWindowController.showException(ex);
                } finally {
                    this.form.getChildren().clear();
                }
                EditionListVboxController.this.rlvc.setVisible(false);
            } catch (Exception ex) {
                MainWindowController.showException(ex);
            }     
        }
    }

    /**
     * Populates the list of edtions from the database. 
     * If non-null, selects the edition with id selectedEditionId
     * @param email
     * @return
     * @throws Exception
     */
    private void populateList(String email, final String selectedEditionId) throws Exception {

        clearEditionList();
        selectEditionsLabel.setVisible(true);
        DbUtils.ResultSetAction populatelistAction = new DbUtils.ResultSetAction() {

            public void execute(final ResultSet rs) throws SQLException {
                try {
                    final String id = rs.getString(1);
                    final String desc = rs.getString(2);
                    id2desc.put(id,desc);

                    Listitem l = new Listitem();

                    l.setValue(id);

                    Listcell iconcell = new Listcell();
                    Image icon = new Image(EditionCache.getIconPath(id));
                    icon.setHeight("16px");
                    icon.setWidth("20px");
                    iconcell.appendChild(icon);
                    l.appendChild(iconcell);

                    l.appendChild(new Listcell(id));
                    Listcell lc = new Listcell(desc);
                    l.appendChild(lc);
                    edlist.appendChild(l);

                    if (id.equals(selectedEditionId)) {
                        l.setSelected(true);
                        try {
                            edlist.selectEdition(id);
                        } catch (Exception ex) {
                            MainWindowController.showException(ex);
                        }
                    }
                } catch (EditionCache.NotFoundError nfe) {
                    Utils.logUnexpectedException(nfe);
                }
            }
        };

        String stmt = "SELECT editionInfo.editionId,editionInfo.shortDesc FROM editionInfo,editionMaintainer"
            + " WHERE editionMaintainer.email = ? AND editionMaintainer.editionId = editionInfo.editionId"
            + " ORDER BY editionInfo." + (sortedByDescription ? "shortDesc" : "editionId");
        DbUtils.doSqlQueryStatement(stmt, populatelistAction, email);

        edlist.setVisible(edlist.getItemCount() > 0);
        noEditionsMsg.setVisible(edlist.getItemCount() == 0);
        // make sure that selected edition is visible
        int selectedIndex = edlist.getSelectedIndex();
        Paginal p = edlist.getPaginal();
        if (selectedIndex >= 0 && p != null)
            p.setActivePage(selectedIndex/p.getPageSize());
    }
    private void clearEditionList() {
        edlist.getItems().clear();
        this.selectEditionsLabel.setVisible(false);
    }

    /**
     * Reload list of editions for given user id.
     */
    private void reloadEditionList(String email, String selectedEditionId) throws Exception {
        rlvc.clearrevBox();   
        EditionListVboxController.this.setVisible(true);
        this.populateList(email, selectedEditionId);
    }

    private void reloadCurrentEditionList(String selectedEditionId) throws Exception {
        reloadEditionList(UserInfo.getUserInfo().getEmail(), selectedEditionId);
    }

    public Html returnNoEditionsHtmlMessage() {
        return new Html("<b>No editions for this user in the database</b>");
    }

    /**
     * It adds the new edition to the database (unless the edition is already in the database)
     * @param e
     */
    private void enterNewEditionInDatabase(Edition e) throws Exception {
        int count = DbUtils.getTableCount("editionInfo", "editionId = ?", e.getId());
        if (count > 0)
            return;

        UserInfo ui = UserInfo.getUserInfo();

        DbUtils.doSqlUpdateStatement("INSERT INTO editionInfo (editionId, shortDesc, isBlessed, isPublic) VALUES(?, ?, false, false)", 
                e.getId(), e.getName().getEdition());
        DbUtils.doSqlUpdateStatement("INSERT INTO editionMaintainer (email, editionId) VALUES(?, ?)",
                ui.getEmail(), e.getId());
        EditionCache.invalidateEdition(e.getId());

        reloadCurrentEditionList(e.getId());
    }

    /**
     * Appends the name of the new model that the user has been working on to the edition list,
     * and also adds the same edition to the database of editions.
     */
    UserInfo.SignOnListener newModelAppend = new UserInfo.SignOnListener() {
        public void onSignOn(String email) throws Exception {
            Model currentModel = Model.getCurrentModel();
            if (currentModel != null) {
                enterNewEditionInDatabase(currentModel.getEdition());
            }
        }
    };

    /**
     * Load edition list when user signs on. 
     */
    UserInfo.SignOnListener loadEdList = new UserInfo.SignOnListener() {
        public void onSignOn(String email) throws Exception {
            reloadEditionList(email, null);
        }   
    };

    UserInfo.SignOffListener signoffOperations = new UserInfo.SignOffListener() {
        public void onSignOff() throws Exception {
            for (Object c : EditionListVboxController.this.getChildren())
                ((Component)c).setVisible(false);
            homepageBoxController.getParent().setVisible(false);
            rlvc.clearrevBox();
            Model.setCurrentModel(null);
        }
    };


    UserInfo.SignOffListener hideLists = new UserInfo.SignOffListener() {
        public void onSignOff() throws Exception {
            EditionListVboxController.this.setVisible(false);     
            EditionListVboxController.this.rlvc.setVisible(false);    
            EditionListVboxController.this.selectEditionsLabel.setVisible(false);
        }
    };    



    /**
     * Controller for the Vbox that contains revision list.
     * @author tgaat
     */
    public static class RevisionListVboxController extends Vbox {

        private EditionListVboxController elvc;
        private Vbox openButtonBox;
        private Vbox buildButtonBox;

        private Button makeLive;
        private Label needsRebuild = new Label("Revision needs to be rebuilt");
        private Html status;
        private HashMap<Integer,RevisionHistory> rev2history = new HashMap<Integer, RevisionHistory>();
        private Html selectRevisionsLabel;

        public void initialize(EditionListVboxController editionBox, Html lab) {
            this.elvc = editionBox; 
            this.selectRevisionsLabel = lab;
        }

        private void clearrevBox() {
            this.selectRevisionsLabel.setVisible(false);
            this.getChildren().clear();
        }

        /**
         * Reload revision list for given edition.
         * If selectRevision != -1, select given revision.
         */
        void loadRevList(String editionId, int selectRevision) {
            clearrevBox();
            this.selectRevisionsLabel.setVisible(true);
            openButtonBox = new Vbox();
            buildButtonBox = new Vbox();
            RevList revList = new RevList(editionId);
            int revNumber = Model.getRevisions(editionId).size();
            revList.attachBuildButton(revNumber);

            revList.setHflex("1");
            this.appendChild(revList);
            this.setHflex("1");
            this.appendChild(openButtonBox);
            this.appendChild(buildButtonBox);

            if (selectRevision != -1) {
                revList.performRevisionSelection(selectRevision);
                // assumes 1:1 mapping of revisions to indices
                revList.setSelectedIndex(selectRevision - 1);
            }
        }

        void loadRevList(String editionId) {
            loadRevList(editionId, -1);
        }

        enum RevisionHistory {
            TEST, ARCHIVED, LIVE
        }

        /**
         * Creates and attaches a hyperlink to the revision test page. 
         * @param revNum
         * @param editionId
         * @return
         */
        Hbox createRevisionInformationDisplay(int revNum, String editionId) {
            Html info = new Html(
                    "<a target=\"_blank\" href=\"" + Model.getTestPageUrl(editionId, revNum) + "\">Revision Test Page</a>");
            return new Hbox(new Component [] {
                    info,
                    Utils.createHelpTextComponent("build.revisiontestpage")
            });
        }


        /**
         * Attaches a build button, even when a user is not logged on when a new model is loaded.
         * @param editionId
         * @param revNumber
         */
        public void attachBuildButton(final String editionId, final int revNumber) {
            Button buildButton = new Button("Build Revision #" + revNumber);
            final Checkbox fullInstallCheckbox = new Checkbox("Bundle auxiliary DLLs for LibXIE");
            
            
            final Html status = new Html();
            buildButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                public void onEvent(Event e){       
                    try {

                        String mpath = Model.getFSPath(editionId, revNumber);
                        Builder.build(mpath, fullInstallCheckbox.isChecked());       

                        status.setContent("Revision #" + revNumber + " built - " 
                                + "use the Revision Test Page to download and test.");

                        /*
                         * For the purposes of usability study
                         */
                        Utils.printLog("Built edition %s revision %s", editionId, revNumber);                        

                    } catch (Exception exc) {
                        MainWindowController.showException(exc);
                    }      
                } 
            });

            this.appendChild(new Hbox(new Component [] {
                    buildButton,
                    Utils.createHelpTextComponent("edition.build"),     
            }));
            this.appendChild(new Hbox(new Component[] {fullInstallCheckbox, Utils.createHelpTextComponent("build.ie.fullinstall")}));
            this.appendChild(status);
            this.appendChild(createRevisionInformationDisplay(revNumber, editionId));
        }



        public class RevList extends ListboxWithHeader {
            private String editionId;

            /**
             * Constructs a new revision list.
             *
             * The maximum revision number is assigned the label working, if a symlink exists,appropriate
             * revision number is assigned LIVE. All others are assigned archived. 
             * Also displays the date, when the particular revision was last modified and built. 
             */
            public RevList (String editionId) {
                super(new String[] { "#", "Status", "Last Changed", "Last Built" });

                this.setHflex("1");
                List<Integer> revisions =  Model.getRevisions(editionId);
                this.editionId = editionId;

                // assume all revisions are archived
                for (int rev : revisions)
                    rev2history.put(rev, RevisionHistory.ARCHIVED);

                // assume last revision is test
                rev2history.put(revisions.get(revisions.size() - 1), RevisionHistory.TEST);

                // check for live edition (if it exists)
                if (EditionCache.isLive(editionId)) {
                    rev2history.put(EditionCache.getLiveRevisionNumber(editionId), RevisionHistory.LIVE);
                }

                for (int rev : revisions)
                    attachListItem(editionId, rev);
            }

            /**
             * Attaches the revision history as per the revision number. 
             * @param id
             * @param rev
             */

            public void attachListItem(String id, int rev) {
                Listitem li = new Listitem();
                Listcell lc = new Listcell();

                lc.appendChild(new Label(Integer.toString(rev)));
                li.appendChild(lc);

                Listcell lce = new Listcell();
                Label l = null;
                switch (rev2history.get(rev)) {
                case LIVE:
                    l = new Label("live");
                    l.setStyle("color: red");
                    break;

                case TEST:
                    l = new Label("testing");
                    break;

                case ARCHIVED:
                    l = new Label("archived");
                    l.setStyle("color: gray");
                    break;
                }
                lce.appendChild(l);
                li.appendChild(lce);

                Listcell lcell1 = new Listcell();
                lcell1.appendChild(new Label(Utils.formatDate(Model.getLastModifiedDateOfConfig(id, rev))));
                li.appendChild(lcell1);

                Listcell lcell2 = new Listcell();
                lcell2.appendChild(new Label(Utils.formatDate(Model.getLastModifiedDateOfXpi(id, rev))));
                li.appendChild(lcell2);

                li.setValue(rev);
                li.setWidth("100%");
                this.appendChild(li);
            }

            /**
             * Handler for when user selects a revision.
             * Listitems are associated with the revision number.
             * @param e
             */
            public void onSelect(Event e) {
                Listitem li = this.getSelectedItem();
                int revNumber = ((Integer)li.getValue()).intValue();
                performRevisionSelection(revNumber);
            }

            /**
             * Handle revision selection by creating and display appropriate elements.
             */
            void performRevisionSelection(int revNumber) {
                try {
                    openButtonBox.getChildren().clear();
                    buildButtonBox.getChildren().clear();
                    RevisionHistory type = rev2history.get(revNumber);

                    openButtonBox.appendChild(createRevisionInformationDisplay(revNumber, editionId));
                    Model m = Model.getCurrentModel();
                    if (m != null && editionId.equals(m.getEdition().getId()) && m.getRevision() == revNumber) {
                        openButtonBox.appendChild(new Html("<b>Revision #" + revNumber + " is being worked on</b>"));
                    } else {
                        if (type == RevisionHistory.TEST) 
                            attachOpenButton(revNumber, "(Modify)", /* autosave= */true);
                        else
                            attachOpenButton(revNumber, "(Read Only)", /* autosave= */false);
                    }

                    if(type != RevisionHistory.TEST) {
                        attachMakeWorkingButton(revNumber);
                    }
                    attachBuildButton(revNumber);

                    int liveRevisionNumber = EditionCache.getLiveRevisionNumber(editionId);
                    makeLive = null;
                    if (revNumber > liveRevisionNumber) {
                        File fconf = new File(Model.getConfigFilePath(editionId, revNumber)); 
                        File xpifile = new File(Model.getBuildXpiPath(editionId, revNumber)); 
                        if (xpifile.exists()) {
                            if (fconf.lastModified() <= xpifile.lastModified()) {
                                attachMakeLiveButton(revNumber);
                            } else {
                                openButtonBox.appendChild(new Hbox(new Component [] {
                                        needsRebuild
                                }));
                            }
                        }
                    }

                    openButtonBox.appendChild(status = new Html());
                } catch (Exception exc) {
                    MainWindowController.showException(exc);
                }
            }

            private void attachMakeWorkingButton(final int revNum) {
                Button makeWorking = new Button("Copy Revision #" + revNum + " Forward");
                makeWorking.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                    public void onEvent(Event e){       
                        try {

                            String mpath = Model.getFSPath(editionId, revNum);
                            Builder.makeNewRevision(mpath);
                            loadRevList(editionId, revNum);
                            status.setContent("Revision #" + revNum + " copied - "
                                    + "to make changes, open revision labeled 'testing'");
                        } catch (Exception exc) {
                            MainWindowController.showException(exc);
                        }      
                    }

                });

                openButtonBox.appendChild(new Hbox(new Component [] {
                        makeWorking,
                        Utils.createHelpTextComponent("build.makeworking")
                }));
            }

            /*
             * This button will allow the user to build the edition that is being tested. 
             */
            public void attachBuildButton(final int revNumber) {
                Button buildButton = new Button("Build Revision #" + revNumber);
                final Checkbox fullInstallCheckbox = new Checkbox("Bundle auxiliary DLLs for LibXIE");
                
                buildButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                    public void onEvent(Event e){       
                        try {

                            RevisionHistory revStatus = rev2history.get(revNumber);
                            if (revStatus != RevisionHistory.TEST) {
                                String msg = "This revision is frozen - you can only build a revision in 'testing'.";
                                msg += " Use 'Copy forward' to create a new, buildable test revision ";
                                if (revStatus == RevisionHistory.LIVE) {
                                    msg += " that is based on the current live revision.";
                                } else {
                                    msg += " that is based on this archived revision.";
                                }
                                Messagebox.show(msg);
                                return;
                            }

                            String mpath = Model.getFSPath(editionId, revNumber);
                            Builder.build(mpath, fullInstallCheckbox.isChecked());
                            loadRevList(editionId, revNumber);
                            status.setContent("Revision #" + revNumber + " built - " 
                                    + "use the Revision Test Page to download and test.");
                            Utils.printLog("Built edition %s revision %s", editionId, revNumber);
                        } catch (Exception exc) {
                            MainWindowController.showException(exc);
                        }      
                    } 
                });

                buildButtonBox.appendChild(new Hbox(new Component [] {
                        buildButton,
                        Utils.createHelpTextComponent("edition.build")
                }));
                buildButtonBox.appendChild(new Hbox(new Component[] {fullInstallCheckbox, Utils.createHelpTextComponent("build.ie.fullinstall")}));
            }

            /*
             * Make an edition live, then update revision list.
             */
            private void attachMakeLiveButton(final int revNumber) {

                makeLive = new Button("Make Revision #" + revNumber + " Live");
                makeLive.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {

                    public void onEvent(Event e) {
                        try {
                            if (revNumber > 1) {
                                int rc = Messagebox.show("Making revision #" + revNumber + " live will "
                                        + "offer an update to all users who have a previous revision "
                                        + "of this edition installed.  You should be certain that revision #"
                                        + revNumber + " works by having built and tested it yourself.\n"
                                        + "Are you sure you wish to make this revision live?",
                                        "Confirm Making Revision Live", 
                                        Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION);
                                if (rc != Messagebox.YES) {
                                    status.setContent("Revision #" + revNumber + " was not made live.");
                                    return;
                                }
                            }

                            Model.makeLive(editionId, revNumber);          
                            loadRevList(editionId, revNumber);
                            elvc.displaySelectedEditionInformation(editionId);
                            Utils.printLog("Made Live edition %s revision %s", editionId, revNumber);

                        } catch (Exception exc) {
                            MainWindowController.showException(exc);
                        }
                    }
                });

                openButtonBox.appendChild(new Hbox(new Component [] {
                        makeLive,
                        Utils.createHelpTextComponent("build.makelive")
                }));
            }

            private void attachOpenButton(int revNum, String label, boolean autosaveFlag) {

                Button open = new Button("Open Revision #" + revNum + " " + label);
                addModelOpenListeners(open, revNum, autosaveFlag);

                openButtonBox.appendChild(new Hbox(new Component [] {
                        open,
                        autosaveFlag? Utils.createHelpTextComponent("open.normal") 
                                : Utils.createHelpTextComponent("open.readonly")
                }));
            }

            /**
             * Activates autosave only for working models and not for LIVE or archived. 
             * @param openReadOnly
             * @param autosaveFlag
             */
            private void addModelOpenListeners(Button button, final int revNum, final boolean autosaveFlag) {
                button.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                    public void onEvent(Event e){       
                        try {
                            Model.Autosave au;
                            if (autosaveFlag)
                                au = Model.Autosave.TRUE;
                            else 
                                au = Model.Autosave.FALSE;

                            final Model m = new Model(editionId, revNum, au);
                            Model.setCurrentModel(m); 
                            // reload revision list
                            loadRevList(editionId, revNum);

                            // If this revision is subsequently changed, we must disable the live button.
                            if (autosaveFlag) {
                                m.addPropertyChangeListener(new PropertyChangeListener() {
                                    public void propertyChange(PropertyChangeEvent evt) {
                                        // makeLive may revert back to null if revision list is refreshed
                                        if (makeLive == null)
                                            return;

                                        // remove surrounding hbox
                                        Hbox p = (Hbox)makeLive.getParent(); 
                                        p.getParent().insertBefore(needsRebuild, p);
                                        p.setParent(null);

                                        // only needed once
                                        makeLive = null;
                                        m.removePropertyChangeListener(this);
                                    }
                                });
                            }

                        }  catch (Exception exc) {
                            MainWindowController.showException(exc);
                        }      
                    } 
                });
            }
        }      
    } 

    /**
     * This vbox will contain the current home page link and the logic required to change it 
     * in the .htaccess file.
     * @author tgaat
     *
     */
    public static class HomepageBoxController extends Box {

        private Html currentLink;
        private Html downloadInfo;
        private Toolbarbutton changeHomePageLinkButton;
        private Vbox changeSubmitBox;
        private String editionId;
        private int liveRevision;

        public void initialize(Html currentLink, Toolbarbutton changeButton, EditionListVboxController eListVboxController) {
            this.currentLink = currentLink;
            this.changeHomePageLinkButton = changeButton; 
            this.appendChild(downloadInfo = new Html());
            changeSubmitBox = new Vbox();
            changeSubmitBox.setWidth("100%");
            this.appendChild(changeSubmitBox);
        } 

        /**
         * Adds the necessary ZUL components that are necessary to change the home page link.  
         */
        void makeChangeLinkVisible(String id) throws Exception {

            editionId = id;

            this.getParent().setVisible(true);

            liveRevision = EditionCache.getLiveRevisionNumber(id);
            if (liveRevision == -1) {
                this.getParent().setVisible(false);
                return;
            }

            setVisible(true);

            String curLink = Model.getCurrentHomepageLink(editionId);
            displayCurrentLinkMessage(editionId, curLink);

            changeHomePageLinkButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {

                public void onEvent(Event e) {
                    try {
                        final String curLink = Model.getCurrentHomepageLink(editionId);
                        displayCurrentLinkMessage(editionId, curLink);
                        changeSubmitBox.getChildren().clear();
                        final Textbox t = new Textbox();                        
                        t.setWidth("100%");
                        Button submitChangeLinkbutton = new Button("Submit");
                        submitChangeLinkbutton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter() {
                            public void onEvent(Event e){
                                try {
                                    HomepageBoxController.this.changeHomePageLink(t.getValue());
                                    changeSubmitBox.getChildren().clear();
                                } catch(Exception exc) {
                                    MainWindowController.showException(exc);
                                }
                            }
                        });
                        t.setValue(curLink);
                        changeSubmitBox.appendChild(t);
                        changeSubmitBox.appendChild(submitChangeLinkbutton);                      
                    }  catch (Exception exc) {
                        MainWindowController.showException(exc);
                    }      
                } 
            });

        }

        private void displayCurrentLinkMessage(String editionId, String link) {
            String permUrl = Model.getLiveHttpPath(editionId) + "/libx.html";
            String xpiDownload = Model.getLiveHttpXpiPath(editionId);
            String exeDownload = Model.getLiveHttpExePath(editionId);
            currentLink.setContent(
                    "Your permanent edition page is located at "
                    + "<a target=\"_blank\" href=\"" + permUrl + "\">" + permUrl + "</a>."
                    + " This URL is currently being redirected to " 
                    + "<a target=\"_blank\" href = \""+link+"\">"+link+"</a>.<br />"
            );
            downloadInfo.setContent(
                    "The permanent URL for the Firefox extension is "
                    + "<a target=\"_blank\" href = \""+xpiDownload+"\">"+xpiDownload+"</a>"
                    + (Config.ieactivated ?
                            " and for the IE plugin is "
                            + "<a target=\"_blank\" href = \""+exeDownload+"\">"+exeDownload+"</a>" : "")
                            + ".<br />"
            );
        }

        /**
         * Changes home page link and displays new one.
         * @param newLink
         * @throws Exception
         */
        private void changeHomePageLink(String newLink) throws Exception {
            Model.writeHtAccessFile(editionId, liveRevision, newLink);
            displayCurrentLinkMessage(editionId, newLink);
        }
    }        
}
