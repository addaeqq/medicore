/**
 * Minimal clinical + pharmacy read/write compat mirroring the Java backend's
 * endpoints, so the web app can be exercised end-to-end against the reference
 * server. No business rules beyond the Java service's essentials.
 */
const router = require('express').Router();
const db = require('../db');
const { authorize } = require('../policy/engine');
const { HttpError } = require('../middleware/errors');

async function patientOfConsultation(id) {
  const c = await db('consultations').where({ consultation_id: id }).first();
  if (!c) throw new HttpError(404, 'Consultation not found');
  return c;
}

// Start a consultation from a checked-in appointment
router.post('/consultations/start', async (req, res, next) => {
  try {
    const { appointmentId } = req.body;
    const appt = await db('appointments').where({ appointment_id: appointmentId }).first();
    if (!appt) throw new HttpError(404, 'Appointment not found');
    await new Promise((ok, bad) => authorize('emr.write', () => ({ patientId: appt.patient_id }))(req, res, e => e ? bad(e) : ok()));
    if (appt.status !== 'checked_in') throw new HttpError(422, 'Patient must be checked in first');
    const existing = await db('consultations').where({ appointment_id: appointmentId }).first();
    if (existing) return res.json({ consultationId: existing.consultation_id });
    const [c] = await db('consultations').insert({
      appointment_id: appointmentId, patient_id: appt.patient_id,
      doctor_id: req.session.user.staffId,
    }).returning('*');
    res.status(201).json({ consultationId: c.consultation_id });
  } catch (e) { next(e); }
});

router.get('/consultations/:id', async (req, res, next) => {
  try {
    const c = await patientOfConsultation(req.params.id);
    await new Promise((ok, bad) => authorize('emr.read', () => ({ patientId: c.patient_id }))(req, res, e => e ? bad(e) : ok()));
    const head = await db('consultations as c')
      .join('staff as s', 's.staff_id', 'c.doctor_id')
      .join('patients as p', 'p.patient_id', 'c.patient_id')
      .where('c.consultation_id', req.params.id)
      .select('c.consultation_id', 'c.patient_id', 'c.appointment_id', 'c.complaint', 'c.findings',
        'c.diagnosis', 'c.signed_at', 'c.created_at', 's.full_name as doctor', 's.staff_id as doctor_id',
        'p.full_name as patient_name', 'p.mrn', 'p.dob', 'p.sex').first();
    const addendums = await db('addendums as a').join('staff as s', 's.staff_id', 'a.author_id')
      .where('a.consultation_id', req.params.id)
      .select('a.addendum_id', 'a.body', 'a.created_at', 's.full_name as author').orderBy('a.created_at')
      .catch(() => []);
    res.json({ ...head, addendums });
  } catch (e) { next(e); }
});

router.patch('/consultations/:id', async (req, res, next) => {
  try {
    const c = await patientOfConsultation(req.params.id);
    await new Promise((ok, bad) => authorize('emr.write', () => ({ patientId: c.patient_id }))(req, res, e => e ? bad(e) : ok()));
    if (c.signed_at) throw new HttpError(409, 'Signed notes are locked');
    const { complaint, findings, diagnosis } = req.body;
    await db('consultations').where({ consultation_id: req.params.id }).update({ complaint, findings, diagnosis });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

router.post('/consultations/:id/sign', async (req, res, next) => {
  try {
    const c = await patientOfConsultation(req.params.id);
    await new Promise((ok, bad) => authorize('emr.write', () => ({ patientId: c.patient_id }))(req, res, e => e ? bad(e) : ok()));
    if (!c.diagnosis) throw new HttpError(422, 'A diagnosis is required to sign');
    await db('consultations').where({ consultation_id: req.params.id }).update({ signed_at: db.fn.now() });
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// Drug list for the prescriber picker and the pharmacy stock table
router.get('/inventory/drugs', authorize('inventory.read'), async (req, res, next) => {
  try {
    const drugs = await db('drugs as d')
      .leftJoin('stock_batches as b', 'b.drug_id', 'd.drug_id')
      .groupBy('d.drug_id')
      .select('d.drug_id', 'd.generic_name', 'd.strength', 'd.form', 'd.unit_price', 'd.reorder_level',
        db.raw('coalesce(sum(b.qty_on_hand),0) as total_on_hand'))
      .orderBy('d.generic_name');
    res.json({ drugs });
  } catch (e) { next(e); }
});

// Doctor writes a prescription against a consultation (FR-PHM-01)
router.post('/prescriptions', async (req, res, next) => {
  try {
    const { consultationId, items } = req.body;
    const c = await patientOfConsultation(consultationId);
    await new Promise((ok, bad) => authorize('rx.write', () => ({ patientId: c.patient_id }))(req, res, e => e ? bad(e) : ok()));
    if (!Array.isArray(items) || items.length === 0) throw new HttpError(422, 'At least one item is required');
    const [rx] = await db('prescriptions').insert({
      consultation_id: consultationId, patient_id: c.patient_id,
      doctor_id: req.session.user.staffId, status: 'open',
    }).returning('*');
    await db('prescription_items').insert(items.map(i => ({
      prescription_id: rx.prescription_id, drug_id: i.drugId,
      dose: i.dose, frequency: i.frequency, duration_days: i.durationDays ?? null, quantity: i.quantity,
    })));
    res.status(201).json({ prescriptionId: rx.prescription_id, status: rx.status });
  } catch (e) { next(e); }
});

// Pharmacist worklist
router.get('/prescriptions/open', authorize('rx.dispense'), async (req, res, next) => {
  try {
    const prescriptions = await db('prescriptions as rx')
      .join('patients as p', 'p.patient_id', 'rx.patient_id')
      .leftJoin('prescription_items as i', 'i.prescription_id', 'rx.prescription_id')
      .whereIn('rx.status', ['open', 'partially_dispensed'])
      .groupBy('rx.prescription_id', 'p.full_name', 'p.mrn')
      .select('rx.prescription_id', 'rx.status', 'rx.created_at',
        'p.full_name as patient', 'p.mrn', db.raw('count(i.rx_item_id)::int as item_count'))
      .orderBy('rx.created_at');
    res.json({ prescriptions });
  } catch (e) { next(e); }
});

module.exports = router;
