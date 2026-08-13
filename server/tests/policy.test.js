/** Unit tests — Policy Engine decisions against the SRS §4 matrix (negative tests per SRS §8). */
const { test } = require('node:test');
const assert = require('node:assert');
const { decide } = require('../src/policy/engine');

// Injected resolver stubs: relationship truth is controlled per test.
const deps = (o = {}) => ({
  patientOwns: async () => o.owns ?? false,
  doctorHasActiveRelationship: async () => o.rel ?? false,
  nurseWardMatches: async () => o.ward ?? false,
  grantCovers: async () => o.grant ?? false,
});

const P = '11111111-1111-1111-1111-111111111111';

test('deny-by-default: unknown action is denied even for sys_admin (NFR-SEC-03)', async () => {
  const v = await decide({ action: 'not.a.rule', user: { role: 'sys_admin' }, ctx: {} }, deps());
  assert.strictEqual(v.allow, false);
});

test('management can NEVER read EMR (AC-02, matrix denied cell)', async () => {
  const v = await decide({ action: 'emr.read', user: { role: 'management', userId: 'u' }, ctx: { patientId: P } }, deps({ owns: true, rel: true }));
  assert.strictEqual(v.allow, false);
});

test('doctor with active relationship may read EMR (FR-AUTH-05)', async () => {
  const v = await decide({ action: 'emr.read', user: { role: 'doctor', staffId: 's' }, ctx: { patientId: P } }, deps({ rel: true }));
  assert.strictEqual(v.allow, true);
});

test('doctor WITHOUT active relationship is denied EMR — role alone is not enough (ReBAC)', async () => {
  const v = await decide({ action: 'emr.read', user: { role: 'doctor', staffId: 's' }, ctx: { patientId: P } }, deps({ rel: false }));
  assert.strictEqual(v.allow, false);
});

test('patient reads own record; denied on another patient', async () => {
  const own = await decide({ action: 'emr.read', user: { role: 'patient', userId: 'u' }, ctx: { patientId: P } }, deps({ owns: true }));
  const other = await decide({ action: 'emr.read', user: { role: 'patient', userId: 'u' }, ctx: { patientId: P } }, deps({ owns: false }));
  assert.strictEqual(own.allow, true);
  assert.strictEqual(other.allow, false);
});

test('nurse scoped to assigned ward for vitals (AC-03)', async () => {
  const inWard = await decide({ action: 'vitals.write', user: { role: 'nurse', staffId: 's' }, ctx: { patientId: P } }, deps({ ward: true }));
  const outWard = await decide({ action: 'vitals.write', user: { role: 'nurse', staffId: 's' }, ctx: { patientId: P } }, deps({ ward: false }));
  assert.strictEqual(inWard.allow, true);
  assert.strictEqual(outWard.allow, false);
});

test('pharmacist may read prescriptions but never consultation notes', async () => {
  const rx = await decide({ action: 'rx.read', user: { role: 'pharmacist' }, ctx: { patientId: P } }, deps());
  const emr = await decide({ action: 'emr.read', user: { role: 'pharmacist' }, ctx: { patientId: P } }, deps());
  assert.strictEqual(rx.allow, true);
  assert.strictEqual(emr.allow, false);
});

test('family grantee: allowed only with valid covering grant (FR-FAM-01/02)', async () => {
  const withGrant = await decide({ action: 'invoice.read', user: { role: 'family', userId: 'u' }, ctx: { patientId: P, scopeNeeded: 'billing' } }, deps({ grant: true }));
  const noGrant = await decide({ action: 'invoice.read', user: { role: 'family', userId: 'u' }, ctx: { patientId: P, scopeNeeded: 'billing' } }, deps({ grant: false }));
  assert.strictEqual(withGrant.allow, true);
  assert.strictEqual(noGrant.allow, false);
});

test('only management may void invoices (FR-BIL-07)', async () => {
  const mgmt = await decide({ action: 'invoice.void', user: { role: 'management' }, ctx: {} }, deps());
  const clerk = await decide({ action: 'invoice.void', user: { role: 'billing_clerk' }, ctx: {} }, deps());
  assert.strictEqual(mgmt.allow, true);
  assert.strictEqual(clerk.allow, false);
});

test('unauthenticated requests are denied on protected actions', async () => {
  const v = await decide({ action: 'slot.list', user: null, ctx: {} }, deps());
  assert.strictEqual(v.allow, false);
});
