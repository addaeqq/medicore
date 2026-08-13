/**
 * ER Domain 1 — Identity, Scheduling, Appointments & Access (Design Doc Fig. 3)
 * Requirement anchors: FR-AUTH-02/03, FR-PAT-01/02, FR-APT-01..04, FR-FAM-01, FR-EMR-06.
 */
const ROLES = ['management','doctor','nurse','pharmacist','lab_tech','receptionist','billing_clerk','patient','family','sys_admin'];

exports.up = async (knex) => {
  await knex.raw('CREATE EXTENSION IF NOT EXISTS "pgcrypto"'); // gen_random_uuid()

  await knex.schema.createTable('users', (t) => {
    t.uuid('user_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.string('email').notNullable().unique();
    t.string('password_hash').notNullable(); // bcrypt — never plaintext (FR-AUTH-02)
    t.enu('role', ROLES, { useNative: true, enumName: 'user_role' }).notNullable();
    t.boolean('is_active').notNullable().defaultTo(true);
    t.integer('failed_logins').notNullable().defaultTo(0);      // FR-AUTH-06
    t.timestamp('locked_until', { useTz: true });               // FR-AUTH-06
    t.timestamps(true, true);
  });

  await knex.schema.createTable('departments', (t) => {
    t.uuid('department_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.string('name').notNullable().unique();
    t.enu('dept_type', ['clinical','diagnostic','support'], { useNative: true, enumName: 'dept_type' }).notNullable();
    t.decimal('consult_fee', 10, 2).notNullable().defaultTo(0);
  });

  await knex.schema.createTable('staff', (t) => {
    t.uuid('staff_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('user_id').notNullable().unique().references('users.user_id');
    t.uuid('department_id').references('departments.department_id');
    t.string('staff_type').notNullable(); // doctor|nurse|pharmacist|lab_tech|receptionist|billing_clerk|management|sys_admin
    t.string('full_name').notNullable();
    t.uuid('assigned_ward_id'); // FK added in facility migration (nurse ward scoping, AC-03)
  });

  await knex.schema.createTable('patients', (t) => {
    t.uuid('patient_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('user_id').unique().references('users.user_id'); // nullable: walk-ins without portal account (FR-PAT-02)
    t.string('mrn').notNullable().unique();
    t.string('full_name').notNullable();
    t.date('dob').notNullable();
    t.enu('sex', ['female','male','other'], { useNative: true, enumName: 'sex_type' }).notNullable();
    t.string('phone');
    t.string('address');
    t.string('next_of_kin');            // FR-PAT-05
    t.timestamps(true, true);
  });

  await knex.schema.createTable('schedules', (t) => {
    t.uuid('schedule_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('doctor_id').notNullable().references('staff.staff_id');
    t.specificType('weekday', 'smallint').notNullable(); // 0=Sun..6=Sat
    t.time('start_time').notNullable();
    t.time('end_time').notNullable();
    t.specificType('slot_minutes', 'smallint').notNullable().defaultTo(20);
    t.string('room');
    t.check('?? > ??', ['end_time', 'start_time']);
    t.check('?? BETWEEN 0 AND 6', ['weekday']);
    t.check('?? BETWEEN 5 AND 120', ['slot_minutes']);
  });

  await knex.schema.createTable('slots', (t) => {
    t.uuid('slot_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('schedule_id').notNullable().references('schedules.schedule_id').onDelete('CASCADE');
    t.uuid('doctor_id').notNullable().references('staff.staff_id');
    t.timestamp('starts_at', { useTz: true }).notNullable();
    t.timestamp('ends_at', { useTz: true }).notNullable();
    t.enu('status', ['available','booked','closed'], { useNative: true, enumName: 'slot_status' }).notNullable().defaultTo('available');
    t.unique(['doctor_id', 'starts_at']); // FR-APT-02: no overlapping generation for same doctor
    t.index(['doctor_id', 'status', 'starts_at']);
  });

  await knex.schema.createTable('appointments', (t) => {
    t.uuid('appointment_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    // DD-04 / FR-APT-04: THE double-booking guard — one appointment row per slot, enforced by the DB.
    t.uuid('slot_id').notNullable().unique().references('slots.slot_id');
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.uuid('department_id').notNullable().references('departments.department_id');
    t.enu('status', ['booked','checked_in','in_consultation','completed','cancelled','no_show'],
      { useNative: true, enumName: 'appointment_status' }).notNullable().defaultTo('booked');
    t.timestamp('booked_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    t.index(['patient_id', 'status']);
  });

  await knex.schema.createTable('queue_entries', (t) => {
    t.uuid('queue_entry_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('appointment_id').notNullable().unique().references('appointments.appointment_id');
    t.timestamp('checked_in_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    // DD-06: ordering by (priority, checked_in_at) — no integer positions to renumber.
    t.specificType('priority', 'smallint').notNullable().defaultTo(100);
    t.enu('status', ['waiting','in_consultation','done'], { useNative: true, enumName: 'queue_status' }).notNullable().defaultTo('waiting');
  });

  await knex.schema.createTable('access_grants', (t) => {
    t.uuid('grant_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.uuid('grantee_user_id').notNullable().references('users.user_id');
    t.specificType('scope', 'text[]').notNullable(); // subset of {admission_status,billing,appointments} (FR-FAM-01)
    t.timestamp('expires_at', { useTz: true }).notNullable();
    t.timestamp('revoked_at', { useTz: true });
    t.boolean('is_guardian').notNullable().defaultTo(false); // FR-FAM-03
    t.timestamps(true, true);
  });

  await knex.schema.createTable('audit_log', (t) => {
    t.bigIncrements('audit_id').primary();
    t.uuid('user_id').references('users.user_id');
    t.uuid('patient_id').references('patients.patient_id');
    t.string('action').notNullable();       // e.g. emr.read, appointment.book, auth.login_failed
    t.string('entity_ref');                  // table:uuid provenance
    t.jsonb('meta');
    t.timestamp('occurred_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    t.index(['patient_id', 'occurred_at']);
    t.index(['user_id', 'occurred_at']);
  });
};

exports.down = async (knex) => {
  for (const tbl of ['audit_log','access_grants','queue_entries','appointments','slots','schedules','patients','staff','departments','users'])
    await knex.schema.dropTableIfExists(tbl);
  for (const en of ['user_role','dept_type','sex_type','slot_status','appointment_status','queue_status'])
    await knex.raw(`DROP TYPE IF EXISTS ${en}`);
};
