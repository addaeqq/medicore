const db = require('../db');
/** Append-only audit write (FR-EMR-06, NFR-SEC-04). Never throws into the request path. */
async function audit({ userId, patientId, action, entityRef, meta }, trx) {
  try {
    await (trx || db)('audit_log').insert({
      user_id: userId || null,
      patient_id: patientId || null,
      action,
      entity_ref: entityRef || null,
      meta: meta ? JSON.stringify(meta) : null,
    });
  } catch (e) {
    console.error('audit write failed', e.message);
  }
}
module.exports = { audit };
