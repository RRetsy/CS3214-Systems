/**
 * Handle Email.
 *
 * Placeholder for utility routines to support email.
 */
package org.libx.editionbuilder;

import java.io.UnsupportedEncodingException;

import java.util.Arrays;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailSystem {
    public static String fromAddr = "libx.editions@gmail.com";
    public static String fromName = "LibX Edition Builder";

    /**
     * A class representing an email message.
     */
    static class Message {
        private MimeMessage msg = new MimeMessage(emailSession);

        public Message() {
            try {
                InternetAddress from = new InternetAddress(fromAddr, fromName);
                msg.setFrom(from);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        }

        public Message(String recipient) throws MessagingException {
            this();
            msg.setRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(recipient));
        }

        public void setBody(String body) throws MessagingException {
            msg.setText(body);
        }

        public void setReplyTo(String replyTo) throws MessagingException {
            msg.setReplyTo(new Address [] { new InternetAddress(replyTo) });
        }

        public void setSubject(String subject) throws MessagingException {
            msg.setSubject(subject);
        }

        public void addRecipient(String recipient) throws MessagingException {
            msg.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(recipient));
        }

        public void addRecipientToCc(String recipient) throws MessagingException {
            msg.addRecipient(javax.mail.Message.RecipientType.CC, new InternetAddress(recipient));
        }

        public void sendEmail(String subject, String body) throws MessagingException {
            setBody(body);
            setSubject(subject);
            send();
        }

        public void send() throws MessagingException {
            Utils.printLog("sending email to ##%s %s", 
                    Arrays.asList(msg.getAllRecipients()), 
                    msg.getSubject());
            Transport.send(msg);
        }
    }

    //* Shared email session instance
    private static Session emailSession;
    static {
        // XXX J2EE says we should look up mail session via JNDI
        Properties props = System.getProperties();     
        emailSession = Session.getDefaultInstance(props);
    }
}
