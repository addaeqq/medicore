package com.medicore.notifications;

/**
 * Outbound-mail port (DD-08, mirroring DD-07's PaymentGateway pattern):
 * notification logic and the outbox worker are written against this contract;
 * only the adapter changes per provider (SMTP now; any API provider later).
 */
public interface MailPort {
    /** True when the adapter has enough configuration to attempt delivery. */
    boolean configured();

    /** Deliver one plain-text email; throws on transport failure (worker handles retry). */
    void send(String recipient, String subject, String bodyText);
}
