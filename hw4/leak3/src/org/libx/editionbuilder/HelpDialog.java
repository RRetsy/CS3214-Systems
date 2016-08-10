package org.libx.editionbuilder;

import java.util.Collections;
import java.util.List;

import javax.mail.MessagingException;

import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

/**
 * Controller for help window.
 */
public class HelpDialog extends Window {
    public void initialize(final Textbox helpQuestionTextbox, final Checkbox helpShareOwnershipCheckbox, final Button helpSubmitButton) {
        helpSubmitButton.addEventListener(Events.ON_CLICK, new EventListener() {
            public void onEvent(Event e) {
                try {
                    final boolean transferOwnership = helpShareOwnershipCheckbox.isChecked();
                    final String userMessage = helpQuestionTextbox.getValue();

                    // hide window
                    HelpDialog.this.setVisible(false);

                    if (transferOwnership)
                        transferEditionOwnership(editionId);

                    sendEmailToHelpteam(editionId, userMessage, transferOwnership);
                    MainWindowController.showStatus(StatusCode.OK, "Email sent to LibX Team.");
                } catch (Exception ex) {
                    MainWindowController.showException(ex);
                }
            }
        });

        /** 
         * The default behavior when the close button is pressed is to detach the window.
         * Prevent that and hide it instead.  Follows documentation for org.zkoss.zul.Window.
         */
        this.addEventListener(Events.ON_CLOSE, new EventListener() {
            public void onEvent(Event e) {
                setVisible(false);
                e.stopPropagation();
            }
        });
    }

    /**
     * Transfer ownership if not already transferred.
     */
    private void transferEditionOwnership(final String editionId) throws Exception {
        int count = DbUtils.getTableCount("editionMaintainer", "editionId = ? AND email = ?", editionId, Config.helpemail);

        if (count == 0) {
            DbUtils.doSqlUpdateStatement(
                    "INSERT INTO editionMaintainer(email,editionId) VALUES (?, ?)",
                    Config.helpemail, editionId);
        } else {
            /* I think it's safe to ignore repeated requests here. */
            Utils.printLog("ignored repeated help request for edition %s", this.editionId);
        }
    }

    /**
     * Send email to help team and to user.
     */
    private void sendEmailToHelpteam(final String id, final String userMessage, final boolean transferOwnership) throws MessagingException {
        MailSystem.Message msg = new MailSystem.Message(Config.helpemail);

        String user = UserInfo.getUserInfo().getEmail();
        String body = user+" has requested help with edition "+id+".\n";

        List<Integer> revisions = Model.getRevisions(id);
        body += "At the time of the help request, this edition had the following revisions: " + revisions + "\n";

        int rev;
        Model m = Model.getCurrentModel();
        if (m != null) {
            rev = m.getRevision();
            body += "The user was currently working on revision #" + rev + ".\n";
        } else {
            rev = Collections.max(revisions);
            body += "The user was not currently working on any revision.\n";
        }
        body += "The test URL page for revision #" + rev + " is at " + Model.getTestPageUrl(id, rev) + "\n";

        if (transferOwnership) {
            body += "The user transferred shared ownership.\n";
        } else {
            body += "The user did not transfer shared ownership.\n";
        }

        body += "The user included the following message:\n\n" + userMessage;
        msg.setReplyTo(user);

        String subject = "Help Request for edition "+id;
        msg.sendEmail(subject, body);

        MailSystem.Message msys = new MailSystem.Message(UserInfo.getUserInfo().getEmail());
        msys.sendEmail(subject,
                "You have requested help for edition '"+id+"'."
                +"A LibX team member will get back to you shortly. "
                +"Below is a copy of the email we have sent:\n\n" + body);
        MainWindowController.showStatus(StatusCode.OK, "You have requested help for edition "+id);
    }

    /**
     * Show modal help dialog.
     */
    void show(String editionId) {
        try {
            this.editionId = editionId;
            this.getCaption().setLabel("Request Help for LibX Edition " + editionId);
            this.doModal();
        } catch (InterruptedException ie) {
            MainWindowController.showStatus(StatusCode.WARNING, "No help was requested");
        }
    }

    //* remember edition id between show() and submit button action.
    private String editionId;
}
