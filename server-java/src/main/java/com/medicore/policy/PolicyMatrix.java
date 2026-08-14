package com.medicore.policy;

import java.util.Map;
import java.util.Set;

import static com.medicore.policy.Scope.*;
import static java.util.Map.entry;

/**
 * The SRS §4 permission matrix as data (Design DD-03). Framework-free.
 * Absent role => DENIED. Absent action => DENIED (deny-by-default, NFR-SEC-03).
 * PUBLIC_ACTIONS require no authentication; AUDITED_ACTIONS log every access,
 * allowed or denied (FR-EMR-06).
 */
public final class PolicyMatrix {
    private PolicyMatrix() {}

    public static final Set<String> PUBLIC_ACTIONS = Set.of("patient.register_self");

    public static final Map<String, Map<String, Scope>> MATRIX = Map.ofEntries(
        // --- Patients & registration ---
        entry("patient.register_walkin", Map.of("receptionist", ANY, "sys_admin", ANY)),
        entry("patient.read_profile",    Map.of("patient", OWN, "receptionist", ANY, "doctor", RELATIONSHIP, "nurse", WARD, "sys_admin", ANY)),
        entry("patient.update_profile",  Map.of("patient", OWN, "receptionist", ANY)),

        // --- Scheduling & appointments ---
        entry("schedule.manage",    Map.of("sys_admin", ANY)),
        entry("slot.list",          Map.of("patient", ANY, "receptionist", ANY, "doctor", ANY, "sys_admin", ANY)),
        entry("appointment.book",   Map.of("patient", OWN, "receptionist", ANY)),
        entry("appointment.cancel", Map.of("patient", OWN, "receptionist", ANY)),
        entry("appointment.list",   Map.of("patient", OWN, "receptionist", ANY, "doctor", RELATIONSHIP)),
        entry("queue.checkin",      Map.of("receptionist", ANY)),
        entry("queue.manage",       Map.of("receptionist", ANY)),
        entry("queue.view",         Map.of("receptionist", ANY, "doctor", ANY, "nurse", ANY)),

        // --- EMR (clinical core) ---
        entry("emr.read",      Map.of("doctor", RELATIONSHIP, "patient", OWN)),
        entry("emr.write",     Map.of("doctor", RELATIONSHIP)),
        // Addendums attach to completed encounters where the active relationship may have
        // ended; the service layer enforces author == consultation.doctor (FR-EMR-04).
        entry("emr.addendum",  Map.of("doctor", ANY)),
        entry("vitals.write",  Map.of("nurse", WARD, "doctor", RELATIONSHIP)),
        entry("vitals.read",   Map.of("doctor", RELATIONSHIP, "nurse", WARD, "patient", OWN)),
        entry("allergy.read",  Map.of("doctor", RELATIONSHIP, "nurse", WARD, "pharmacist", ANY, "patient", OWN)),
        entry("allergy.write", Map.of("doctor", RELATIONSHIP, "nurse", WARD)),

        // --- Pharmacy ---
        entry("rx.write",         Map.of("doctor", RELATIONSHIP)),
        entry("rx.read",          Map.of("doctor", RELATIONSHIP, "pharmacist", ANY, "patient", OWN)),
        entry("rx.dispense",      Map.of("pharmacist", ANY)),
        entry("inventory.manage", Map.of("pharmacist", ANY)),
        entry("inventory.read",   Map.of("pharmacist", ANY, "doctor", ANY, "management", ANY)),

        // --- Laboratory ---
        entry("lab.order",         Map.of("doctor", RELATIONSHIP)),
        entry("lab.process",       Map.of("lab_tech", ANY)),
        entry("lab.release",       Map.of("doctor", RELATIONSHIP)),
        entry("lab.read_released", Map.of("patient", OWN, "doctor", RELATIONSHIP)),

        // --- Billing (gateway-agnostic; ITC adapter at M3, DD-07) ---
        entry("invoice.read",       Map.of("billing_clerk", ANY, "management", ANY, "patient", OWN, "family", GRANT)),
        entry("invoice.manage",     Map.of("billing_clerk", ANY)),
        entry("invoice.void",       Map.of("management", ANY)),                       // FR-BIL-07
        entry("payment.record",     Map.of("billing_clerk", ANY)),
        entry("payment.pay_online", Map.of("patient", OWN, "family", GRANT)),

        // --- Facility ---
        entry("bed.view_ward",       Map.of("nurse", WARD, "doctor", ANY, "management", ANY, "sys_admin", ANY)),
        entry("admission.create",    Map.of("doctor", RELATIONSHIP)),
        entry("admission.manage",    Map.of("doctor", RELATIONSHIP, "nurse", WARD)),
        entry("occupancy.aggregate", Map.of("management", ANY, "sys_admin", ANY)),

        // --- Family access ---
        entry("grant.manage", Map.of("patient", OWN, "sys_admin", ANY)),              // FR-FAM-01/03
        entry("granted.read", Map.of("family", GRANT)),

        // --- Administration ---
        entry("admin.users",       Map.of("sys_admin", ANY)),
        entry("admin.catalogues",  Map.of("sys_admin", ANY)),
        entry("reports.aggregate", Map.of("management", ANY)),                        // AC-02
        entry("audit.read",        Map.of("management", ANY, "sys_admin", ANY))
    );

    /** Clinical actions whose every access is audit-logged (FR-EMR-06). */
    public static final Set<String> AUDITED_ACTIONS = Set.of(
        "emr.read", "emr.write", "emr.addendum", "vitals.read", "vitals.write", "allergy.read", "allergy.write",
        "rx.read", "rx.write", "rx.dispense", "lab.read_released", "lab.release", "granted.read"
    );
}
