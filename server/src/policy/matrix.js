/**
 * The SRS §4 permission matrix as data (DD-03).
 * Scopes:
 *   'any'          — role may act on any resource of this kind
 *   'own'          — resource must belong to the requesting user (their own patient record)
 *   'relationship' — requires an ACTIVE care relationship, resolved in relationships.js (ReBAC, FR-AUTH-05)
 *   'ward'         — staff's assigned ward must match the resource's ward (AC-03)
 *   'grant'        — requires an unexpired, unrevoked access grant covering the scope (FR-FAM-01/02)
 * Absent role ⇒ DENIED. Absent action ⇒ DENIED (deny-by-default, NFR-SEC-03).
 */
const MATRIX = {
  // --- Patients & registration ---
  'patient.register_self':   { public: true },
  'patient.register_walkin': { receptionist: 'any', sys_admin: 'any' },
  'patient.read_profile':    { patient: 'own', receptionist: 'any', doctor: 'relationship', nurse: 'ward', sys_admin: 'any' },
  'patient.update_profile':  { patient: 'own', receptionist: 'any' },

  // --- Scheduling & appointments ---
  'schedule.manage':   { sys_admin: 'any' },
  'slot.list':         { patient: 'any', receptionist: 'any', doctor: 'any', sys_admin: 'any' },
  'appointment.book':  { patient: 'own', receptionist: 'any' },
  'appointment.cancel':{ patient: 'own', receptionist: 'any' },
  'appointment.list':  { patient: 'own', receptionist: 'any', doctor: 'relationship' },
  'queue.checkin':     { receptionist: 'any' },
  'queue.manage':      { receptionist: 'any' },
  'queue.view':        { receptionist: 'any', doctor: 'any', nurse: 'any' },

  // --- EMR (clinical core) ---
  'emr.read':          { doctor: 'relationship', patient: 'own' },
  'emr.write':         { doctor: 'relationship' },
  'vitals.write':      { nurse: 'ward', doctor: 'relationship' },
  'vitals.read':       { doctor: 'relationship', nurse: 'ward', patient: 'own' },
  'allergy.read':      { doctor: 'relationship', nurse: 'ward', pharmacist: 'any', patient: 'own' },
  'allergy.write':     { doctor: 'relationship', nurse: 'ward' },

  // --- Pharmacy ---
  'rx.write':          { doctor: 'relationship' },
  'rx.read':           { doctor: 'relationship', pharmacist: 'any', patient: 'own' },
  'rx.dispense':       { pharmacist: 'any' },
  'inventory.manage':  { pharmacist: 'any' },
  'inventory.read':    { pharmacist: 'any', doctor: 'any', management: 'any' },

  // --- Laboratory ---
  'lab.order':         { doctor: 'relationship' },
  'lab.process':       { lab_tech: 'any' },
  'lab.release':       { doctor: 'relationship' },
  'lab.read_released': { patient: 'own', doctor: 'relationship' },

  // --- Billing ---
  'invoice.read':      { billing_clerk: 'any', management: 'any', patient: 'own', family: 'grant' },
  'invoice.manage':    { billing_clerk: 'any' },
  'invoice.void':      { management: 'any' },       // FR-BIL-07
  'payment.record':    { billing_clerk: 'any' },
  'payment.pay_online':{ patient: 'own', family: 'grant' },

  // --- Facility ---
  'bed.view_ward':     { nurse: 'ward', doctor: 'any', management: 'any', sys_admin: 'any' },
  'admission.create':  { doctor: 'relationship' },
  'admission.manage':  { doctor: 'relationship', nurse: 'ward' },
  'occupancy.aggregate': { management: 'any', sys_admin: 'any' },

  // --- Family access ---
  'grant.manage':      { patient: 'own', sys_admin: 'any' },  // FR-FAM-01/03
  'granted.read':      { family: 'grant' },

  // --- Administration ---
  'admin.users':       { sys_admin: 'any' },
  'admin.catalogues':  { sys_admin: 'any' },
  'reports.aggregate': { management: 'any' },                 // AC-02: aggregates only
  'audit.read':        { management: 'any', sys_admin: 'any' },
};

/** Clinical actions whose every access is audit-logged (FR-EMR-06). */
const AUDITED_ACTIONS = new Set([
  'emr.read','emr.write','vitals.read','vitals.write','allergy.read','allergy.write',
  'rx.read','rx.write','rx.dispense','lab.read_released','lab.release','granted.read',
]);

module.exports = { MATRIX, AUDITED_ACTIONS };
