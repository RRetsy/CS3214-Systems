package org.libx.editionbuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;

import javax.mail.MessagingException;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Vbox;

/**
 * Controller for UserInfo in zul/signon.zul.
 *
 * Implements authentication, user registration and deletion, and
 * password reminders.
 *
 * Other components interact by registering SignOn and SignOffListeners.
 */
public class UserInfo extends Vbox
{
    /**
     * Keeps track of the state the user is in at any point of time in the session.
     * @author tgaat
     */
    enum SessionState {
        LOGGEDOFF,LOGGEDON
    }

    /**
     * An action to be done when the user is signed on.
     * @author tgaat
     */
    interface SignOnListener {
        public void onSignOn(String email) throws Exception;
    }

    /**
     * An action to be done when the user signs off.
     * @author tgaat
     */
    interface SignOffListener {
        public void onSignOff() throws Exception;
    }

    //* ZUL elements
    private Textbox emailbox;
    private Textbox passwordbox;
    private Button logonbutton;
    private Vbox authBox;

    //* Internal state
    private String email;
    private String password;
    private SessionState state = SessionState.LOGGEDOFF;
    private Vector<SignOnListener> signOnListeners=new Vector<SignOnListener>();
    private Vector<SignOffListener> signOffListeners=new Vector<SignOffListener>();

    public UserInfo() {
        Utils.setDesktopAttribute("userinfo", this);
    }

    /**
     * Helper method to obtain that particular desktop instance of UserInfo.
     * @return
     */
    public static UserInfo getUserInfo() {
        return (UserInfo)Utils.getDesktopAttribute("userinfo");
    }

    SessionState getSessionState() {
        return this.state;
    }

    public boolean isAuthenticated() {
        return this.state == SessionState.LOGGEDON;
    }

    String getEmail() {
        return this.email;
    }

    String getUserId() {
        return this.email;
    }

    public void activateSessionUser() {
        Session session = Sessions.getCurrent();
        Object userName = session.getAttribute("username");
        if (userName != null) {
            loginAs((String)userName);
        }
    }

    public static String mailingListEmail = "libx-request@mozdev.org";

    /**
     * On clicking this button, a new user is allowed to register in this system.
     * @author tgaat
     */
    public class RegisterButton extends Button {
        private String forUser;
        private String userPassword;
        public RegisterButton(String forUser, String userPassword) {
            this.setLabel("Register");
            this.forUser = forUser;
            this.userPassword = userPassword;
        }

        public void onClick(Event e) {
            try {
                boolean sentEmail = false;

                try {
                    sendRegisterEmail(forUser, userPassword);
                    sentEmail = true;  
                } catch (MessagingException ex) {
                    /*
                     * If the email provided by the user is invalid, then the user is prompted whether
                     * s/he wants to register with the invalid email itself.
                     */
                    int rc = Messagebox.show("You have provided what appears to be an invalid email address: '" + forUser + "'.  You can use this id for the purpose of logging on to the edition builder, but you will be unable to receive email reminders of your password.  Would you like to register with this id? "
                            , "Confirm Registration with Invalid Email", 
                            Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION);
                    if (rc == Messagebox.NO) {
                        return;
                    }                        
                }
                enterUserInfoInDatabase(forUser, userPassword);
                boolean alsoSignedUpForMailinglist = false;
                if (sentEmail)
                    alsoSignedUpForMailinglist = showMailingListSignup(forUser);

                if (alsoSignedUpForMailinglist)
                    Utils.printLog("user %s signed up for mailing list", forUser);

                showRegistrationSuccess(forUser, sentEmail, alsoSignedUpForMailinglist);
            } catch (Exception e1) {    
                MainWindowController.showException(e1);
            }
        }

        /**
         * Shows the user a dialog, prompting whether s/he wants to sign up for the LibX Mailing
         * List.
         * @throws InterruptedException
         * @throws MessagingException
         */
        private boolean showMailingListSignup(String userEmail) throws InterruptedException, MessagingException {
            int mailingListSignup = Messagebox.show("Would you like to sign up for the LibX mailing list?"
                    +"\nIt is a low-traffic mailing list used for announcements and discussion related to LibX."
                    , "Sign up for the LibX Mailing List", 
                    Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION);

            if (mailingListSignup== Messagebox.YES) {
                MailSystem.Message msys = new MailSystem.Message(mailingListEmail);
                msys.setReplyTo(userEmail);

                msys.sendEmail("subscribe address="+userEmail,"");
                return true;
            }
            return false;
        }

        private void sendRegisterEmail(String toUser, String password) throws MessagingException {
            MailSystem.Message msys = new MailSystem.Message(toUser);
            msys.sendEmail("Welcome to Libx Edition Builder",
                    "Your username is "+toUser+"\nYour password is "+password);
        }

        private void showRegistrationSuccess(String username, boolean sentEmail, boolean alsoSignedUpForMailinglist) {
            showInAuthbox(
                    new Label("You have successfully registered!"),
                    sentEmail ?
                            new Label("An email has been sent to you with your registration information.") 
                    : null,
                    alsoSignedUpForMailinglist ?
                            new Label("You were signed up for the mailing list.") 
                            : null
            );
            loginAs(username);
        }

        private void enterUserInfoInDatabase(String email, String password) throws Exception {
            DbUtils.doSqlUpdateStatement("DELETE FROM userInfo WHERE email = ?", email);

            DbUtils.doSqlUpdateStatement(
                    "INSERT INTO userInfo (email,password,isSignedUpForStudy) VALUES (?, ?, ?)",
                    email, password, "1");
        }
    }

    /**
     * On clicking this button, an email reminder is sent to the user.
     * @author tgaat
     */
    public class ReminderButton extends Button {

        private String retrievedPass;
        private String forUser;

        ReminderButton(String forUser) {
            this.forUser = forUser;
            this.setLabel("Send Reminder");
        }

        public void onClick(Event e) {
            try {
                DbUtils.ResultSetAction passwordCheckAction = new DbUtils.ResultSetAction(){
                    public void execute(ResultSet rs) throws SQLException{
                        retrievedPass = rs.getString(1);
                    }
                };

                DbUtils.doSqlQueryStatement(
                    "SELECT password FROM userInfo WHERE email = ?", 
                    passwordCheckAction, forUser);

                MailSystem.Message msys = new MailSystem.Message(forUser);
                msys.sendEmail("Password Reminder", "Your password is "+retrievedPass);

                showInAuthbox(new Label("A reminder email has been sent to you with your password!"));
            } catch (Exception exc) {
                MainWindowController.showException(exc);
            }
        }
    }

    /**
     * onClick event handler for "Log On" button. Called from ZUL.
     * Passes current values of email/password textboxes.
     *
     * @param email
     * @param password
     * @throws Exception
     */
    public void logonButtonPressed(String email, String password) {
        try {
            if (state == SessionState.LOGGEDOFF) {
                if (email.equals("") || password.equals("")) { 
                    showInAuthbox(new Label("Please enter valid email and password"));
                    return;
                }
                new AuthenticateAction(email, password).authenticate();
            } else {
                String currentUser = getUserId();
                doLogoff();
                showInAuthbox(new Label("User '" + currentUser + "' logged off."));
            }
        } catch (Exception ex) {
            MainWindowController.showException(ex);
        }
    }

    /**
     * Helper class for authentication. 
     * This class localizes checking the userInfo database for an email/user.
     *
     * @author tgaat
     */
    private class AuthenticateAction implements DbUtils.ResultSetAction {
        private boolean userFound = false;
        private final String username, password;

        AuthenticateAction(String username, String password) {
            this.username = username;
            this.password = password;
        }

        /**
         * Perform authentication.  Queries database. 
         * If user is not found, offer registration.
         * If user is found, and password is correct, sign on.
         * If user is found, and password is incorrect, offer reminder.
         */
        void authenticate() throws Exception {
            String stmt = "SELECT email,password from userInfo WHERE email = ?";
            DbUtils.doSqlQueryStatement(stmt, this, username);

            if (!userFound) {
                Hbox hb = new Hbox();
                hb.appendChild(new Label("You may register '" + username + "' using the password you provided."));
                hb.appendChild(new RegisterButton(username, password));
                showInAuthbox(new Label("User '" + username + "' not found.") , hb);
            }
        }

        public void execute(ResultSet rs) throws SQLException {
            userFound = true;

            String dbPassword = rs.getString(2);
            boolean passwordMatches = dbPassword.equals(password);

            if (passwordMatches) {
                showInAuthbox(new Component [] { });
                loginAs(this.username);
            } else {
                Hbox h = new Hbox(new Component [] {
                    new Label("Would you like to be sent a reminder email at "+username+"?"),
                    new ReminderButton(this.username)
                });
                showInAuthbox(new Label("Incorrect password, try again."), h);
            }
        }
    }

    /**
     * Perform actions on successful login.  
     * Called after successful authentication or registration.
     */
    private void loginAs(String username) {
        this.email = username;
        Utils.printLog("%s logged in successfully", this.email); 
        Session session = Sessions.getCurrent();
        session.setAttribute("username", this.email);

        state = SessionState.LOGGEDON;
        emailbox.setValue(username);
        emailbox.setReadonly(true); 
        passwordbox.setValue("********");
        passwordbox.setReadonly(true);
        logonbutton.setLabel("Log Off");

        Button dub = new Button("Unregister '" + username + "'");
        dub.addEventListener(Events.ON_CLICK, deleteUser);
        authBox.appendChild(dub);
        authBox.appendChild(attachHboxForChangingPassword());
        authBox.appendChild(attachCheckboxForUserStudy());
        

        try {
            fireSignOnListeners();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Attaches a checkbox that allows users to sign up for the user study.
     * @param label
     * @return
     */
    private Hbox attachCheckboxForUserStudy() {

        final Checkbox checkbox = new Checkbox();

        Component helpComponent;

        checkbox.setLabel("It's okay to contact me for a planned user study.");
        final String isSignedUpForStudyField = "isSignedUpForStudy";
        helpComponent = Utils.createHelpTextComponent("user.study");

        String stmt = "SELECT " + isSignedUpForStudyField + " FROM userInfo WHERE email = ?";

        DbUtils.ResultSetAction signUpFlagRetrieveAction  = new DbUtils.ResultSetAction(){
            public void execute(ResultSet rs) throws SQLException{
                checkbox.setChecked(rs.getBoolean(1));         
            }
        };

        try {
            DbUtils.doSqlQueryStatement(stmt, signUpFlagRetrieveAction, this.email);
        } catch (Exception exc) {
            MainWindowController.showException(exc);
        }

        checkbox.addEventListener(Events.ON_CHECK, new Utils.EventListenerAdapter(true) {
            public void onEvent(Event e) {
                try {
                    String stmt = "UPDATE userInfo SET " + isSignedUpForStudyField + " = "
                    + checkbox.isChecked() + " WHERE email = ?";
                    DbUtils.doSqlUpdateStatement(stmt, UserInfo.this.email);
                } catch (Exception ex) {
                    MainWindowController.showException(ex);
                }
            }
        });
        Hbox hb = new Hbox(new Component[] {checkbox,helpComponent});
        return hb;
    }


    private Hbox attachHboxForChangingPassword() {

        final Button changePasswordButton = new Button("Change Password");
        final Hbox hb = new Hbox(new Component[] {changePasswordButton});

        changePasswordButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter(true){
            public void onEvent(Event e) {
                changePasswordButton.setDisabled(true);

                final Textbox passwordBox = new Textbox();
                passwordBox.setType("password");
                final Button submitButton = new Button("Submit");
                final Button cancelButton = new Button("Cancel");

                final Hbox submitBox = new Hbox(new Component[] {passwordBox,submitButton, cancelButton});
                hb.appendChild(submitBox);

                submitButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter(true){
                    public void onEvent(Event e){
                        try {
                            if(passwordBox.getValue().equals("")) {
                                Messagebox.show("You cannot enter a blank password");
                            }
                            else {
                                String stmt = "UPDATE userInfo SET password = ? WHERE email = ?";
                                DbUtils.doSqlUpdateStatement(stmt, passwordBox.getValue(), UserInfo.this.email);
                                Label l = new Label("Password changed.");
                                hb.appendChild(l);
                                Utils.removeComponentAfter(l, 5000);

                                changePasswordButton.setDisabled(false);
                                submitBox.setParent(null);
                            }
                        } catch (Exception exc) {
                            MainWindowController.showException(exc);
                        }
                    }
                });
                
                cancelButton.addEventListener(Events.ON_CLICK, new Utils.EventListenerAdapter(true) {
                    public void onEvent(Event e) {
                        changePasswordButton.setDisabled(false);
                        submitBox.setParent(null);
                    }
                });
            }
        });      
        return hb;
    }


    /**
     * Perform log off actions. 
     * Called after user pressed logout button or when account is deleted.
     */
    private void doLogoff() {        
        Session session = Sessions.getCurrent();
        session.removeAttribute("username");
        Utils.printLog("%s logged off successfully", this.email);

        state = SessionState.LOGGEDOFF;
        emailbox.setReadonly(false);
        emailbox.setValue("");
        passwordbox.setReadonly(false);
        passwordbox.setValue("");
        logonbutton.setLabel("Log On");

        try {
            fireSignOffListeners();
            MainWindowController.showStatus(StatusCode.OK, "Edition Builder Ready.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // for safety.
        this.email = null;
        this.password = null;
    }

    /**
     * Unregister the current user.
     */
    final EventListener deleteUser = new Utils.EventListenerAdapter() {
        public void onEvent(Event e) {
            try {
                int rc = Messagebox.show("Would you like to unregister user '" + email + "'?\n"
                        + "You will lose access to all editions for which you are the sole maintainer.", "Confirm User Deletion", Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION);
                if (rc != Messagebox.YES)
                    return;

                String stmt = "DELETE FROM userInfo WHERE email = ?";
                DbUtils.doSqlUpdateStatement(stmt, email);

                stmt = "DELETE FROM editionMaintainer WHERE email = ?";
                DbUtils.doSqlUpdateStatement(stmt, email);

                String username = getUserId();
                doLogoff();
                showInAuthbox(new Label("User '" + username + "' deleted"));
                // TBD: delete editions of this user
            } catch (Exception ex) {
                MainWindowController.showException(ex);
            }
        }
    };

    /**
     * Clear the authentication box and then append the provided children.
     */
    private void showInAuthbox(Component... components) {
        authBox.getChildren().clear();
        for (Component c : components) {
            if (c != null) {
                authBox.appendChild(c);
            }
        }
    }

    /**
     * Fires the onSignOn listeners 
     * @throws Exception
     */
    private void fireSignOnListeners() throws Exception {
        for (int i=0; i < signOnListeners.size(); i++) {
            SignOnListener l = signOnListeners.get(i);
            l.onSignOn(this.email);
        }       
    }

    /**
     * Fires the onSignOff listeners 
     * @throws Exception
     */
    private void fireSignOffListeners() throws Exception {
        for (int i=0; i < signOffListeners.size(); i++) {
            SignOffListener l = signOffListeners.get(i);
            l.onSignOff();
        }
    }

    /**
     * Adds a SignOn listener to the vector of listeners maintained by this class. 
     * @param l
     */
    public void addSignOnListener(SignOnListener l) {
        this.signOnListeners.add(l);
    }

    /**
     * Adds a SignOff listener to the vector of listeners maintained by this class. 
     * @param l
     */
    public void addSignOffListener(SignOffListener l) {
        this.signOffListeners.add(l);        
    }

    /**
     * Registers various elements placed by zul.
     */
    public void registerAuthenticationFields(Vbox authenticationBox, Textbox email, Textbox password, Button regbutton) {
        this.authBox = authenticationBox;
        this.emailbox = email;
        this.passwordbox = password;
        this.logonbutton = regbutton;
    }
}
