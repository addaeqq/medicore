/** Unit tests — pure slot generation (FR-APT-02, NFR-MNT-02). */
const { test } = require('node:test');
const assert = require('node:assert');
const { generateSlotTimes } = require('../src/services/scheduling');

test('generates correct number of slots for a 3h window at 20 min', () => {
  const sched = { weekday: 1, start_time: '09:00', end_time: '12:00', slot_minutes: 20 };
  const from = new Date(Date.UTC(2026, 7, 10)); // Mon 10 Aug 2026
  const slots = generateSlotTimes(sched, from, 7); // one Monday in window
  assert.strictEqual(slots.length, 9); // 180 / 20
  assert.strictEqual(slots[0].starts_at.toISOString(), '2026-08-10T09:00:00.000Z');
  assert.strictEqual(slots[8].ends_at.toISOString(), '2026-08-10T12:00:00.000Z');
});

test('never emits a slot that would overrun the end time', () => {
  const sched = { weekday: 2, start_time: '09:00', end_time: '10:30', slot_minutes: 25 };
  const from = new Date(Date.UTC(2026, 7, 11)); // Tue
  const slots = generateSlotTimes(sched, from, 7);
  assert.strictEqual(slots.length, 3); // 09:00, 09:25, 09:50 — 10:15+25 would overrun
  for (const s of slots) assert.ok(s.ends_at <= new Date('2026-08-11T10:30:00Z'));
});

test('produces slots only on the schedule weekday across the horizon', () => {
  const sched = { weekday: 5, start_time: '08:00', end_time: '09:00', slot_minutes: 30 };
  const from = new Date(Date.UTC(2026, 7, 10));
  const slots = generateSlotTimes(sched, from, 28); // 4 Fridays
  assert.strictEqual(slots.length, 8);
  for (const s of slots) assert.strictEqual(s.starts_at.getUTCDay(), 5);
});
