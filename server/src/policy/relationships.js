/**
 * ReBAC resolvers (FR-AUTH-05, AC-03): a role is necessary but NOT sufficient —
 * clinical access additionally requires an active care relationship.
 */
const db = require('../db');

/** Doctor ⇄ patient: booked/active appointment, active admission under their care, or referral (Phase 2). */
async function doctorHasActiveRelationship(doctorStaffId, patientId) {
  const appt = await db('appointments')
    .where({ patient_id: patientId })
    .whereIn('status', ['booked', 'checked_in', 'in_consultation'])
    .whereIn('slot_id', db('slots').select('slot_id').where({ doctor_id: doctorStaffId }))
    .first();
  if (appt) return true;
  const adm = await db('admissions')
    .where({ patient_id: patientId, admitting_doctor: doctorStaffId, status: 'active' })
    .first();
  return !!adm;
}

/** Nurse ⇄ patient: patient actively admitted to a bed in the nurse's assigned ward (AC-03). */
async function nurseWardMatches(nurseStaffId, patientId) {
  const row = await db('admissions as a')
    .join('beds as b', 'b.bed_id', 'a.bed_id')
    .join('rooms as r', 'r.room_id', 'b.room_id')
    .join('staff as s', 's.assigned_ward_id', 'r.ward_id')
    .where({ 'a.patient_id': patientId, 'a.status': 'active', 's.staff_id': nurseStaffId })
    .first('a.admission_id');
  return !!row;
}

/** Family grantee: unexpired, unrevoked grant covering the requested scope (FR-FAM-01/02). */
async function grantCovers(granteeUserId, patientId, scopeNeeded) {
  const g = await db('access_grants')
    .where({ grantee_user_id: granteeUserId, patient_id: patientId })
    .whereNull('revoked_at')
    .where('expires_at', '>', db.fn.now())
    .whereRaw('? = ANY(scope)', [scopeNeeded])
    .first();
  return !!g;
}

/** Patient owns the resource: the resource's patient row is linked to their user account. */
async function patientOwns(userId, patientId) {
  const p = await db('patients').where({ patient_id: patientId, user_id: userId }).first();
  return !!p;
}

module.exports = { doctorHasActiveRelationship, nurseWardMatches, grantCovers, patientOwns };
