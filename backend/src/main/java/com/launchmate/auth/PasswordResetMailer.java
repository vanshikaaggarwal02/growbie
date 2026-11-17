package com.launchmate.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the reset link. Until mail is switched on (MAIL_ENABLED=true with a working mailbox), the link is
 * written to the server log instead, so the flow can be tried in development. Never enable that log
 * fallback in production: it contains a working reset link.
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final boolean enabled;
    private final String from;
    private final String publicUrl;

    public PasswordResetMailer(ObjectProvider<JavaMailSender> sender,
                               @Value("${launchmate.mail.enabled:false}") boolean enabled,
                               @Value("${launchmate.mail.from:support@growbie.in}") String from,
                               @Value("${launchmate.public-url:http://localhost:5173}") String publicUrl) {
        this.sender = sender;
        this.enabled = enabled;
        this.from = from;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    public void sendReset(String to, String token) {
        String link = publicUrl + "/reset-password?token=" + token;
        JavaMailSender mail = sender.getIfAvailable();
        if (!enabled || mail == null) {
            log.info("Mail is off, so no email was sent. Development reset link for {}: {}", to, link);
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject("Reset your Growbie password");
        m.setText("Hello,\n\nSomeone asked to reset the password for your Growbie account. "
                + "Use this link within 30 minutes:\n\n" + link
                + "\n\nIf this wasn't you, you can ignore this email and your password stays the same.\n\n"
                + "Growbie\nsupport@growbie.in\n");
        try {
            mail.send(m);
        } catch (MailException e) {
            log.warn("Password reset email could not be sent: {}", e.getClass().getSimpleName());
        }
    }
}
