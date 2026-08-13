/** Integration test — the double-booking race (FR-APT-04, DD-04): two parallel bookings, exactly one winner. */
const { test, after } = require('node:test');
const assert = require('node:assert');
const db = require('../src/db');
const { bookAppointment, createScheduleWithSlots } = require('../src/services/scheduling');

after(async () => { await db.destroy(); });

test('parallel bookings of one slot: exactly one succeeds, loser gets 409', async () => {
  // Arrange: fresh doctor + schedule + two patients
  const [u] = await db('users').insert({ email: `race-doc-${Date.now()}@t.test`, password_hash: 'x', role: 'doctor' }).returning('*');
  const [dept] = await db('departments').select('*').limit(1);
  const [doc] = await db('staff').insert({ user_id: u.user_id, staff_type: 'doctor', full_name: 'Race Doc', department_id: dept.department_id }).returning('*');
  await createScheduleWithSlots({ doctorId: doc.staff_id, weekday: new Date().getUTCDay(), startTime: '23:00', endTime: '23:40', slotMinutes: 20 });
  const slot = await db('slots').where({ doctor_id: doc.staff_id, status: 'available' }).orderBy('starts_at').first();
  assert.ok(slot, 'slot generated');

  const mkPatient = async (i) => {
    const [pu] = await db('users').insert({ email: `race-p${i}-${Date.now()}@t.test`, password_hash: 'x', role: 'patient' }).returning('*');
    const [p] = await db('patients').insert({ user_id: pu.user_id, mrn: `MRN-RACE-${Date.now()}-${i}`, full_name: `P${i}`, dob: '1990-01-01', sex: 'other' }).returning('*');
    return p;
  };
  const [p1, p2] = await Promise.all([mkPatient(1), mkPatient(2)]);

  // Act: race
  const results = await Promise.allSettled([
    bookAppointment({ slotId: slot.slot_id, patientId: p1.patient_id }),
    bookAppointment({ slotId: slot.slot_id, patientId: p2.patient_id }),
  ]);

  // Assert: one fulfilled, one 409
  const wins = results.filter((r) => r.status === 'fulfilled');
  const losses = results.filter((r) => r.status === 'rejected');
  assert.strictEqual(wins.length, 1, 'exactly one booking wins');
  assert.strictEqual(losses.length, 1);
  assert.strictEqual(losses[0].reason.status, 409);

  const appts = await db('appointments').where({ slot_id: slot.slot_id });
  assert.strictEqual(appts.length, 1, 'DB holds exactly one appointment for the slot');
  const s = await db('slots').where({ slot_id: slot.slot_id }).first();
  assert.strictEqual(s.status, 'booked');
});

test('signed consultation is immutable at the database level (FR-EMR-03)', async () => {
  const doc = await db('staff').where({ staff_type: 'doctor' }).first();
  const pat = await db('patients').first();
  const [c] = await db('consultations').insert({
    doctor_id: doc.staff_id, patient_id: pat.patient_id,
    complaint: 'test', diagnosis: 'test', signed_at: db.fn.now(),
  }).returning('*');
  await assert.rejects(
    db('consultations').where({ consultation_id: c.consultation_id }).update({ diagnosis: 'tampered' }),
    /immutable/,
  );
});

test('audit log rejects UPDATE and DELETE (NFR-SEC-04)', async () => {
  const [row] = await db('audit_log').insert({ action: 'test.entry' }).returning('*');
  await assert.rejects(db('audit_log').where({ audit_id: row.audit_id }).update({ action: 'tampered' }), /append-only/);
  await assert.rejects(db('audit_log').where({ audit_id: row.audit_id }).del(), /append-only/);
});
