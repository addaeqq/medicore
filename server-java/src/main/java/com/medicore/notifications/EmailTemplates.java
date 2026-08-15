package com.medicore.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Pure, framework-free email content (FR-APT-06). Plain text by design: reliable across clients. */
public final class EmailTemplates {
    private EmailTemplates() {}

    public record Email(String subject, String body) {}

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final DateTimeFormatter WHEN =
        DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm").withZone(ACCRA);

    public static String formatWhen(Instant at) { return WHEN.format(at); }
    public static String formatMoney(BigDecimal amount) { return "GHS " + amount.setScale(2).toPlainString(); }

    public static Email bookingConfirmation(String patientName, String doctor, String department, Instant startsAt) {
        return new Email(
            "Appointment confirmed - " + formatWhen(startsAt),
            patientName + ",\n\nYour appointment is confirmed.\n\n"
            + "  Doctor:     " + doctor + "\n"
            + "  Clinic:     " + department + "\n"
            + "  When:       " + formatWhen(startsAt) + "\n\n"
            + "Please arrive 15 minutes early and report to reception to check in.\n"
            + "Need to cancel? Use My appointments in the MediCore portal.\n\n"
            + "MediCore HMS");
    }

    public static Email cancellation(String patientName, String doctor, Instant startsAt) {
        return new Email(
            "Appointment cancelled - " + formatWhen(startsAt),
            patientName + ",\n\nYour appointment with " + doctor + " on " + formatWhen(startsAt)
            + " has been cancelled.\n\nYou can book a new time in the MediCore portal.\n\nMediCore HMS");
    }

    public static Email reminder(String patientName, String doctor, String department, Instant startsAt) {
        return new Email(
            "Reminder: your appointment " + formatWhen(startsAt),
            patientName + ",\n\nA reminder of your upcoming appointment.\n\n"
            + "  Doctor:     " + doctor + "\n"
            + "  Clinic:     " + department + "\n"
            + "  When:       " + formatWhen(startsAt) + "\n\n"
            + "Please arrive 15 minutes early to check in at reception.\n\nMediCore HMS");
    }

    public static Email paymentReceipt(String patientName, BigDecimal amount, String invoiceRef) {
        return new Email(
            "Payment received - " + formatMoney(amount),
            patientName + ",\n\nWe have received your payment of " + formatMoney(amount)
            + " on invoice " + invoiceRef + ". Thank you.\n\n"
            + "The full invoice is available under My bills in the MediCore portal.\n\nMediCore HMS");
    }
}
