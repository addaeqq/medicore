/**
 * ER Domain 3 — Billing, Payments & Facility (Design Doc Fig. 5)
 * Requirement anchors: FR-BIL-01..05, FR-FAC-01..05.
 */
exports.up = async (knex) => {
  await knex.schema.createTable('invoices', (t) => {
    t.uuid('invoice_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.string('visit_ref');
    t.enu('status', ['draft','issued','partially_paid','paid','void'],
      { useNative: true, enumName: 'invoice_status' }).notNullable().defaultTo('draft'); // FR-BIL-05
    t.string('void_reason'); // FR-BIL-07: mandatory when voided
    t.timestamp('issued_at', { useTz: true });
    t.timestamps(true, true);
    t.index(['patient_id', 'status']);
  });

  // DD-05: append-only line items; totals are computed, never stored mutable.
  await knex.schema.createTable('invoice_items', (t) => {
    t.uuid('item_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('invoice_id').notNullable().references('invoices.invoice_id');
    t.enu('source_type', ['consultation','pharmacy','laboratory','bed_day','other'],
      { useNative: true, enumName: 'charge_source' }).notNullable(); // FR-BIL-02 provenance
    t.uuid('source_id');
    t.string('description').notNullable();
    t.decimal('amount', 10, 2).notNullable();
    t.timestamp('posted_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    t.check('?? >= 0', ['amount']);
  });

  await knex.schema.createTable('payments', (t) => {
    t.uuid('payment_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('invoice_id').notNullable().references('invoices.invoice_id');
    t.enu('method', ['paystack','cash','pos'], { useNative: true, enumName: 'payment_method' }).notNullable();
    t.decimal('amount', 10, 2).notNullable();
    t.string('paystack_ref').unique(); // server-side verification key (NFR-SEC-06)
    t.enu('status', ['pending','paid','failed'], { useNative: true, enumName: 'payment_status' }).notNullable().defaultTo('pending');
    t.timestamp('paid_at', { useTz: true });
    t.check('?? > 0', ['amount']);
  });

  await knex.schema.createTable('wards', (t) => {
    t.uuid('ward_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.string('name').notNullable().unique();
    t.decimal('daily_tariff', 10, 2).notNullable().defaultTo(0); // FR-FAC-05
  });

  await knex.schema.createTable('rooms', (t) => {
    t.uuid('room_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('ward_id').notNullable().references('wards.ward_id');
    t.string('room_no').notNullable();
    t.unique(['ward_id', 'room_no']);
  });

  await knex.schema.createTable('beds', (t) => {
    t.uuid('bed_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('room_id').notNullable().references('rooms.room_id');
    t.string('label').notNullable();
    t.enu('status', ['available','reserved','occupied','cleaning','maintenance'],
      { useNative: true, enumName: 'bed_status' }).notNullable().defaultTo('available'); // FR-FAC-01
    t.unique(['room_id', 'label']);
  });

  await knex.schema.createTable('admissions', (t) => {
    t.uuid('admission_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.uuid('admitting_doctor').notNullable().references('staff.staff_id');
    t.uuid('bed_id').notNullable().references('beds.bed_id');
    t.uuid('invoice_id').references('invoices.invoice_id');
    t.timestamp('admitted_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    t.timestamp('discharged_at', { useTz: true });
    t.enu('status', ['active','discharged'], { useNative: true, enumName: 'admission_status' }).notNullable().defaultTo('active');
  });
  // FR-FAC-03: at most one ACTIVE admission per bed — partial unique index (Design Doc §5.1).
  await knex.raw(`CREATE UNIQUE INDEX one_active_admission_per_bed ON admissions (bed_id) WHERE status = 'active'`);

  await knex.schema.createTable('bed_assignments', (t) => {
    t.uuid('assignment_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('admission_id').notNullable().references('admissions.admission_id');
    t.uuid('bed_id').notNullable().references('beds.bed_id');
    t.timestamp('from_ts', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    t.timestamp('to_ts', { useTz: true }); // null = current bed (FR-FAC-04 transfer history)
  });

  // Deferred FKs from earlier domains
  await knex.schema.alterTable('staff', (t) => {
    t.foreign('assigned_ward_id').references('wards.ward_id');
  });
  await knex.schema.alterTable('consultations', (t) => {
    t.foreign('admission_id').references('admissions.admission_id');
  });
};

exports.down = async (knex) => {
  await knex.schema.alterTable('consultations', (t) => t.dropForeign('admission_id'));
  await knex.schema.alterTable('staff', (t) => t.dropForeign('assigned_ward_id'));
  for (const tbl of ['bed_assignments','admissions','beds','rooms','wards','payments','invoice_items','invoices'])
    await knex.schema.dropTableIfExists(tbl);
  for (const en of ['invoice_status','charge_source','payment_method','payment_status','bed_status','admission_status'])
    await knex.raw(`DROP TYPE IF EXISTS ${en}`);
};
