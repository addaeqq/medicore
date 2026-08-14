/**
 * Read-only directory endpoints mirroring the Java backend's UI-support API
 * (added with the frontend milestone so the reference server can drive the web app).
 */
const router = require('express').Router();
const db = require('../db');
const { authorize } = require('../policy/engine');

router.get('/departments', authorize('slot.list'), async (req, res, next) => {
  try {
    const departments = await db('departments')
      .select('department_id', 'name', 'dept_type', 'consult_fee').orderBy('name');
    res.json({ departments });
  } catch (e) { next(e); }
});

router.get('/doctors', authorize('slot.list'), async (req, res, next) => {
  try {
    const doctors = await db('staff as s')
      .leftJoin('departments as d', 'd.department_id', 's.department_id')
      .where('s.staff_type', 'doctor')
      .select('s.staff_id', 's.full_name', 'd.department_id', 'd.name as department')
      .orderBy('s.full_name');
    res.json({ doctors });
  } catch (e) { next(e); }
});

router.get('/patients/search', authorize('patient.read_profile'), async (req, res, next) => {
  try {
    const q = `%${String(req.query.q || '').trim()}%`;
    const patients = await db('patients')
      .whereILike('full_name', q).orWhereILike('mrn', q)
      .select('patient_id', 'mrn', 'full_name', 'dob', 'sex', 'phone')
      .orderBy('full_name').limit(20);
    res.json({ patients });
  } catch (e) { next(e); }
});

router.get('/me/profile', async (req, res, next) => {
  try {
    const user = req.session.user;
    if (!user) return res.status(401).json({ error: 'Not signed in' });
    const out = { user };
    if (user.staffId) {
      out.staff = await db('staff as s')
        .leftJoin('departments as d', 'd.department_id', 's.department_id')
        .where('s.staff_id', user.staffId)
        .select('s.full_name', 's.staff_type', 'd.department_id', 'd.name as department').first();
    }
    if (user.patientId) {
      out.patient = await db('patients').where({ patient_id: user.patientId })
        .select('full_name', 'mrn', 'dob', 'sex').first();
    }
    res.json(out);
  } catch (e) { next(e); }
});

module.exports = router;
