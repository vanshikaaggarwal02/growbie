package com.launchmate.contact;

import com.launchmate.common.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * The public contact form. A message is always saved first; emailing it to support is best effort. Message
 * text and sender details are never written to the log.
 */
@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> sender;
    private final boolean mailEnabled;
    private final String supportAddress;
    private final RateLimiter limiter = new RateLimiter(5, Duration.ofHours(1));

    public ContactService(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> sender,
                          @Value("${launchmate.mail.enabled:false}") boolean mailEnabled,
                          @Value("${launchmate.mail.from:support@growbie.in}") String supportAddress) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.mailEnabled = mailEnabled;
        this.supportAddress = supportAddress;
    }

    public void submit(String clientKey, String name, String email, String business, String message, String trap) {
        if (trap != null && !trap.isBlank()) {
            return; // a bot filled the hidden field: accept quietly, store nothing
        }
        if (!limiter.tryAcquire(clientKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many messages have been sent from your connection in the last hour. Please try again a little later, or write to " + supportAddress + ".");
        }
        String cleanName = oneLine(name);
        String cleanEmail = oneLine(email);
        String cleanBusiness = business == null || business.isBlank() ? null : oneLine(business);
        UUID id = jdbc.queryForObject("""
                insert into contact_message (name, email, business, message) values (?, ?, ?, ?) returning id
                """, UUID.class, cleanName, cleanEmail, cleanBusiness, message.trim());

        JavaMailSender mail = sender.getIfAvailable();
        if (!mailEnabled || mail == null) {
            log.info("Contact message {} saved; mail is off so it was not emailed.", id);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(supportAddress);
            m.setTo(supportAddress);
            m.setReplyTo(cleanEmail);
            m.setSubject("Growbie contact: " + cleanName);
            m.setText("From: " + cleanName + " <" + cleanEmail + ">\n"
                    + (cleanBusiness == null ? "" : "Business: " + cleanBusiness + "\n")
                    + "\n" + message.trim() + "\n");
            mail.send(m);
            jdbc.update("update contact_message set emailed = true where id = ?", id);
        } catch (MailException e) {
            log.warn("Contact message {} saved but could not be emailed: {}", id, e.getClass().getSimpleName());
        }
    }

    /** Collapses line breaks so a value can never add extra mail headers. */
    static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }
}
