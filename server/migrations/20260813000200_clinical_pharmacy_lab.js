/**
 * ER Domain 2 — Clinical (EMR), Pharmacy & Laboratory (Design Doc Fig. 4)
 * Requirement anchors: FR-EMR-01..05, FR-PHM-01..05, FR-LAB-01..06.
 */
exports.up = async (knex) => {
  await knex.schema.createTable('consultations', (t) => {
    t.uuid('consultation_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('appointment_id').references('appointments.appointment_id');
    t.uuid('admission_id'); // FK added in facility migration
    t.uuid('doctor_id').notNullable().references('staff.staff_id');
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.text('complaint');
    t.text('findings');
    t.text('diagnosis');
    t.timestamp('signed_at', { useTz: true }); // once set, row is immutable (FR-EMR-03, trigger in migration 4)
    t.timestamps(true, true);
    t.index(['patient_id']);
  });

  await knex.schema.createTable('addendums', (t) => {
    t.uuid('addendum_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('consultation_id').notNullable().references('consultations.consultation_id');
    t.uuid('author_id').notNullable().references('staff.staff_id');
    t.text('body').notNullable();
    t.timestamp('created_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
  });

  await knex.schema.createTable('vitals', (t) => {
    t.uuid('vitals_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.uuid('recorded_by').notNullable().references('staff.staff_id');
    t.specificType('bp_sys', 'smallint');
    t.specificType('bp_dia', 'smallint');
    t.decimal('temp_c', 4, 1);
    t.specificType('pulse', 'smallint');
    t.specificType('spo2', 'smallint');
    t.decimal('weight_kg', 5, 2);
    t.timestamp('recorded_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    t.index(['patient_id', 'recorded_at']);
  });

  await knex.schema.createTable('allergies', (t) => {
    t.uuid('allergy_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.string('substance').notNullable();
    t.enu('severity', ['mild','moderate','severe'], { useNative: true, enumName: 'allergy_severity' }).notNullable();
    t.unique(['patient_id', 'substance']);
  });

  await knex.schema.createTable('drugs', (t) => {
    t.uuid('drug_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.string('generic_name').notNullable();
    t.string('brand_name');
    t.string('form').notNullable();      // tablet|syrup|injection|...
    t.string('strength').notNullable();
    t.decimal('unit_price', 10, 2).notNullable();
    t.integer('reorder_level').notNullable().defaultTo(10); // FR-PHM-06
    t.boolean('is_controlled').notNullable().defaultTo(false); // FR-PHM-08 (Phase 2 enforcement)
  });

  await knex.schema.createTable('stock_batches', (t) => {
    t.uuid('batch_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('drug_id').notNullable().references('drugs.drug_id');
    t.string('batch_no').notNullable();
    t.date('expiry_date').notNullable();
    t.integer('qty_on_hand').notNullable();
    t.decimal('unit_cost', 10, 2);
    t.check('?? >= 0', ['qty_on_hand']); // FR-PHM-05: stock can never go negative
    t.index(['drug_id', 'expiry_date']); // FEFO scan order (FR-PHM-02)
  });

  await knex.schema.createTable('prescriptions', (t) => {
    t.uuid('prescription_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('consultation_id').notNullable().references('consultations.consultation_id');
    t.uuid('doctor_id').notNullable().references('staff.staff_id');
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.enu('status', ['open','partially_dispensed','dispensed','cancelled'],
      { useNative: true, enumName: 'rx_status' }).notNullable().defaultTo('open');
    t.timestamps(true, true);
  });

  await knex.schema.createTable('prescription_items', (t) => {
    t.uuid('rx_item_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('prescription_id').notNullable().references('prescriptions.prescription_id').onDelete('CASCADE');
    t.uuid('drug_id').notNullable().references('drugs.drug_id');
    t.string('dose').notNullable();
    t.string('frequency').notNullable();
    t.specificType('duration_days', 'smallint');
    t.integer('quantity').notNullable();
    t.check('?? > 0', ['quantity']);
  });

  await knex.schema.createTable('dispenses', (t) => {
    t.uuid('dispense_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('rx_item_id').notNullable().references('prescription_items.rx_item_id');
    t.uuid('batch_id').notNullable().references('stock_batches.batch_id');
    t.integer('qty').notNullable();
    t.uuid('dispensed_by').notNullable().references('staff.staff_id');
    t.timestamp('dispensed_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    t.check('?? > 0', ['qty']);
  });

  await knex.schema.createTable('lab_tests', (t) => {
    t.uuid('lab_test_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.string('name').notNullable().unique();
    t.string('specimen').notNullable();
    t.decimal('price', 10, 2).notNullable();
    t.specificType('tat_hours', 'smallint');
  });

  await knex.schema.createTable('lab_orders', (t) => {
    t.uuid('lab_order_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('consultation_id').notNullable().references('consultations.consultation_id');
    t.uuid('patient_id').notNullable().references('patients.patient_id');
    t.uuid('ordered_by').notNullable().references('staff.staff_id');
    t.enu('status', ['ordered','sample_collected','in_progress','result_entered','released'],
      { useNative: true, enumName: 'lab_order_status' }).notNullable().defaultTo('ordered'); // FR-LAB-04
    t.timestamps(true, true);
  });

  await knex.schema.createTable('lab_order_items', (t) => {
    t.uuid('order_item_id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    t.uuid('lab_order_id').notNullable().references('lab_orders.lab_order_id').onDelete('CASCADE');
    t.uuid('lab_test_id').notNullable().references('lab_tests.lab_test_id');
    t.string('result_value');
    t.string('ref_range');
    t.uuid('entered_by').references('staff.staff_id');
    t.timestamp('released_at', { useTz: true }); // patient visibility gate (FR-LAB-05, AC-04)
  });
};

exports.down = async (knex) => {
  for (const tbl of ['lab_order_items','lab_orders','lab_tests','dispenses','prescription_items','prescriptions','stock_batches','drugs','allergies','vitals','addendums','consultations'])
    await knex.schema.dropTableIfExists(tbl);
  for (const en of ['allergy_severity','rx_status','lab_order_status'])
    await knex.raw(`DROP TYPE IF EXISTS ${en}`);
};
