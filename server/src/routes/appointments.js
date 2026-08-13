/** Slots, booking, cancellation, check-in, live queue (FR-APT-03..08). */
const router = require('express').Router();
const { z } = require('zod');
const db = require('../db');
const { authorize } = require('../policy/engine');
const { bookAppointment, cancelAppointment, checkIn, departmentQueue } = require('../services/scheduling');
const { HttpError } = require('../middleware/errors');

// FR-APT-03: browse available slots
router.get('/slots', authorize('slot.list'), async (req, res, next) => {
  try {
    const q = db('slots as sl')
      .join('staff as st', 'st.staff_id', 'sl.doctor_id')
      .leftJoin('departments as d', 'd.department_id', 'st.department_id')
      .where('sl.status', 'available')
      .where('sl.starts_at', '>', db.fn.now())
      .orderBy('sl.starts_at')
      .limit(200)
      .select('sl.slot_id', 'sl.starts_at', 'sl.ends_at', 'st.full_name as doctor', 'd.name as department', 'd.consult_fee');
    if (req.query.doctorId) q.where('sl.doctor_id', req.query.doctorId);
    if (req.query.departmentId) q.where('st.department_id', req.query.departmentId);
    res.json({ slots: await q });
  } catch (e) { next(e); }
});

// FR-APT-03/04: book — patients book for themselves; reception may book for any patient.
router.post('/',
  authorize('appointment.book', (req) => ({ patientId: req.body.patientId || req.session?.user?.patientId })),
  async (req, res, next) => {
    try {
      const { slotId } = z.object({ slotId: z.string().uuid() }).parse(req.body);
      const patientId = req.session.user.role === 'patient'
        ? req.session.user.patientId
        : z.string().uuid().parse(req.body.patientId);
      const appt = await bookAppointment({ slotId, patientId, bookedByUserId: req.session.user.userId });
      res.status(201).json({ appointment: appt });
    } catch (e) {
      if (e.name === 'ZodError') return next(new HttpError(422, 'Validation failed', e.issues));
      next(e);
    }
  });

// FR-APT-05
router.post('/:id/cancel',
  authorize('appointment.cancel', () => ({})), // ownership re-checked below against the row
  async (req, res, next) => {
    try {
      const appt = await db('appointments').where({ appointment_id: req.params.id }).first();
      if (!appt) throw new HttpError(404, 'Appointment not found');
      const u = req.session.user;
      if (u.role === 'patient' && appt.patient_id !== u.patientId) throw new HttpError(403, 'Not permitted');
      res.json(await cancelAppointment({ appointmentId: req.params.id }));
    } catch (e) { next(e); }
  });

// FR-APT-07
router.post('/:id/checkin', authorize('queue.checkin'), async (req, res, next) => {
  try { res.status(201).json({ queueEntry: await checkIn({ appointmentId: req.params.id }) }); }
  catch (e) { next(e); }
});

// FR-APT-08
router.get('/queue/:departmentId', authorize('queue.view'), async (req, res, next) => {
  try { res.json({ queue: await departmentQueue(req.params.departmentId) }); }
  catch (e) { next(e); }
});

module.exports = router;
