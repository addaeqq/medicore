package com.medicore.notifications;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP implementation of MailPort (DD-08). Vendor-neutral on purpose: Resend,
 * SendGrid, Gmail and Mailtrap all speak SMTP, so switching providers is an
 * env-var change, not a code change. Unconfigured installations stay functional:
 * the worker marks rows 'skipped' instead of failing business flows.
 */
@Component
public class SmtpMailAdapter implements MailPort {
    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;

    public SmtpMailAdapter(ObjectProvider<JavaMailSender> sender,
                           @Value("${spring.mail.host:}") String host,
                           @Value("${medicore.mail.from:}") String from) {
        this.sender = sender; this.host = host; this.from = from;
    }

    @Override
    public boolean configured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank()
            && sender.getIfAvailable() != null;
    }

    @Override
    public void send(String recipient, String subject, String bodyText) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(recipient);
        msg.setSubject(subject);
        msg.setText(bodyText);
        sender.getObject().send(msg); // MailException propagates; the worker owns retry
    }
}
