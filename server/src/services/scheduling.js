/**
 * Scheduling & Queue domain service.
 *  - generateSlotTimes(): PURE function (unit-testable, NFR-MNT-02).
 *  - createScheduleWithSlots(): materialises slots on save (DD-04; sync generation is TD-01).
 *  - bookAppointment(): transactional booking; the DB unique index resolves races (FR-APT-04).
 */
const db = require('../db');
const { HttpError } = require('../middleware/errors');
const { audit } = require('./audit');

/**
 * Pure: expand a weekly schedule into concrete slot times over a horizon.
 * @param {{weekday:number, start_time:string, end_time:string, slot_minutes:number}} schedule
 * @param {Date} from  inclusive start of horizon (UTC)
 * @param {number} days horizon length
 * @returns {{starts_at: Date, ends_at: Date}[]}
 */
function generateSlotTimes(schedule, from, days = 28) {
  const out = [];
  const [sh, sm] = schedule.start_time.split(':').map(Number);
  const [eh, em] = schedule.end_time.split(':').map(Number);
  const dayStartMin = sh * 60 + sm;
  const dayEndMin = eh * 60 + em;
  for (let d = 0; d < days; d++) {
    const day = new Date(Date.UTC(from.getUTCFullYear(), from.getUTCMonth(), from.getUTCDate() + d));
    if (day.getUTCDay() !== schedule.weekday) continue;
    for (let m = dayStartMin; m + schedule.slot_minutes <= dayEndMin; m += schedule.slot_minutes) {
      const starts = new Date(day.getTime() + m * 60000);
      const ends = new Date(starts.getTime() + schedule.slot_minutes * 60000);
      out.push({ starts_at: starts, ends_at: ends });
    }
  }
  return out;
}

/** FR-APT-01/02: create schedule and materialise its slots (idempotent per doctor+time via unique index). */
async function createScheduleWithSlots({ doctorId, weekday, startTime, endTime, slotMinutes, room }, horizonDays = 28) {
  return db.transaction(async (trx) => {
    const [schedule] = await trx('schedules')
      .insert({ doctor_id: doctorId, weekday, start_time: startTime, end_time: endTime, slot_minutes: slotMinutes, room })
      .returning('*');
    const times = generateSlotTimes(
      { weekday, start_time: startTime, end_time: endTime, slot_minutes: slotMinutes },
      new Date(), horizonDays,
    );
    if (times.length) {
      await trx('slots')
        .insert(times.map((t) => ({ schedule_id: schedule.schedule_id, doctor_id: doctorId, ...t })))
        .onConflict(['doctor_id', 'starts_at']).ignore(); // overlapping schedules cannot double-generate (FR-APT-02)
    }
    return { schedule, slotsCreated: times.length };
  });
}

/** FR-APT-03/04: book a slot. Concurrency-safe by construction — see seq. diagram Fig. 6. */
async function bookAppointment({ slotId, patientId, bookedByUserId }) {
  try {
    return await db.transaction(async (trx) => {
      const slot = await trx('slots').where({ slot_id: slotId }).first();
      if (!slot) throw new HttpError(404, 'Slot not found');
      if (slot.status !== 'available') throw new HttpError(409, 'Slot no longer available');
      if (new Date(slot.starts_at) < new Date()) throw new HttpError(422, 'Slot is in the past');

      const dept = await trx('staff').where({ staff_id: slot.doctor_id }).first('department_id');

      // UNIQUE(appointments.slot_id): under a race, exactly one INSERT wins (DD-04).
      const [appt] = await trx('appointments')
        .insert({ slot_id: slotId, patient_id: patientId, department_id: dept.department_id })
        .returning('*');
      await trx('slots').where({ slot_id: slotId }).update({ status: 'booked' });
      await audit({ userId: bookedByUserId, patientId, action: 'appointment.book', entityRef: `appointments:${appt.appointment_id}` }, trx);
      return appt;
    });
  } catch (e) {
    if (e.code === '23505') throw new HttpError(409, 'Slot was just taken — please pick another'); // unique_violation
    throw e;
  }
}

/** FR-APT-05: cancel own upcoming appointment before the cutoff; frees the slot. */
async function cancelAppointment({ appointmentId, cutoffHours = 2 }) {
  return db.transaction(async (trx) => {
    const appt = await trx('appointments').where({ appointment_id: appointmentId }).first();
    if (!appt) throw new HttpError(404, 'Appointment not found');
    if (!['booked'].includes(appt.status)) throw new HttpError(422, 'Only booked appointments can be cancelled');
    const slot = await trx('slots').where({ slot_id: appt.slot_id }).first();
    if (new Date(slot.starts_at).getTime() - Date.now() < cutoffHours * 3600 * 1000)
      throw new HttpError(422, `Cancellation cutoff is ${cutoffHours}h before the slot`);
    await trx('appointments').where({ appointment_id: appointmentId }).update({ status: 'cancelled' });
    await trx('slots').where({ slot_id: appt.slot_id }).update({ status: 'available' });
    return { cancelled: true };
  });
}

/** FR-APT-07: reception check-in → queue entry (priority default 100; triage lowers it, DD-06). */
async function checkIn({ appointmentId }) {
  return db.transaction(async (trx) => {
    const appt = await trx('appointments').where({ appointment_id: appointmentId }).first();
    if (!appt) throw new HttpError(404, 'Appointment not found');
    if (appt.status !== 'booked') throw new HttpError(422, `Cannot check in from status '${appt.status}'`);
    await trx('appointments').where({ appointment_id: appointmentId }).update({ status: 'checked_in' });
    const [entry] = await trx('queue_entries').insert({ appointment_id: appointmentId }).returning('*');
    return entry;
  });
}

/** FR-APT-08: live queue for a department, ordered by (priority, checked_in_at). */
async function departmentQueue(departmentId) {
  return db('queue_entries as q')
    .join('appointments as a', 'a.appointment_id', 'q.appointment_id')
    .join('patients as p', 'p.patient_id', 'a.patient_id')
    .where('a.department_id', departmentId)
    .whereIn('q.status', ['waiting', 'in_consultation'])
    .orderBy([{ column: 'q.priority' }, { column: 'q.checked_in_at' }])
    .select('q.queue_entry_id', 'q.status', 'q.checked_in_at', 'q.priority', 'p.full_name', 'p.mrn');
}

module.exports = { generateSlotTimes, createScheduleWithSlots, bookAppointment, cancelAppointment, checkIn, departmentQueue };
