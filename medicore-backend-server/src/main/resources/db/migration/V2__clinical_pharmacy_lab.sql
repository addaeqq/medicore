-- ER Domain 2 — Clinical (EMR), Pharmacy & Laboratory (Design Fig. 4)
CREATE TABLE consultations (
  consultation_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id  uuid REFERENCES appointments(appointment_id),
  admission_id    uuid,                                        -- FK added in V3
  doctor_id       uuid NOT NULL REFERENCES staff(staff_id),
  patient_id      uuid NOT NULL REFERENCES patients(patient_id),
  complaint       text,
  findings        text,
  diagnosis       text,
  signed_at       timestamptz,                                 -- immutable once set (FR-EMR-03, V4 trigger)
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_cons_patient ON consultations (patient_id);

CREATE TABLE addendums (
  addendum_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  consultation_id uuid NOT NULL REFERENCES consultations(consultation_id),
  author_id       uuid NOT NULL REFERENCES staff(staff_id),
  body            text NOT NULL,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE vitals (
  vitals_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id  uuid NOT NULL REFERENCES patients(patient_id),
  recorded_by uuid NOT NULL REFERENCES staff(staff_id),
  bp_sys smallint, bp_dia smallint, temp_c numeric(4,1),
  pulse smallint, spo2 smallint, weight_kg numeric(5,2),
  recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_vitals_patient ON vitals (patient_id, recorded_at);

CREATE TABLE allergies (
  allergy_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(patient_id),
  substance  varchar(120) NOT NULL,
  severity   text NOT NULL CHECK (severity IN ('mild','moderate','severe')),
  UNIQUE (patient_id, substance)
);

CREATE TABLE drugs (
  drug_id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  generic_name  varchar(160) NOT NULL,
  brand_name    varchar(160),
  form          varchar(40) NOT NULL,
  strength      varchar(40) NOT NULL,
  unit_price    numeric(10,2) NOT NULL,
  reorder_level int NOT NULL DEFAULT 10,                      -- FR-PHM-06
  is_controlled boolean NOT NULL DEFAULT false                -- FR-PHM-08 (Phase 2)
);

CREATE TABLE stock_batches (
  batch_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  drug_id     uuid NOT NULL REFERENCES drugs(drug_id),
  batch_no    varchar(60) NOT NULL,
  expiry_date date NOT NULL,
  qty_on_hand int NOT NULL CHECK (qty_on_hand >= 0),          -- FR-PHM-05: never negative
  unit_cost   numeric(10,2)
);
CREATE INDEX idx_batch_fefo ON stock_batches (drug_id, expiry_date); -- FEFO scan (FR-PHM-02)

CREATE TABLE prescriptions (
  prescription_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  consultation_id uuid NOT NULL REFERENCES consultations(consultation_id),
  doctor_id       uuid NOT NULL REFERENCES staff(staff_id),
  patient_id      uuid NOT NULL REFERENCES patients(patient_id),
  status          text NOT NULL DEFAULT 'open' CHECK (status IN ('open','partially_dispensed','dispensed','cancelled')),
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE prescription_items (
  rx_item_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  prescription_id uuid NOT NULL REFERENCES prescriptions(prescription_id) ON DELETE CASCADE,
  drug_id         uuid NOT NULL REFERENCES drugs(drug_id),
  dose            varchar(60) NOT NULL,
  frequency       varchar(60) NOT NULL,
  duration_days   smallint,
  quantity        int NOT NULL CHECK (quantity > 0)
);

CREATE TABLE dispenses (
  dispense_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  rx_item_id   uuid NOT NULL REFERENCES prescription_items(rx_item_id),
  batch_id     uuid NOT NULL REFERENCES stock_batches(batch_id),
  qty          int NOT NULL CHECK (qty > 0),
  dispensed_by uuid NOT NULL REFERENCES staff(staff_id),
  dispensed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lab_tests (
  lab_test_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name        varchar(160) NOT NULL UNIQUE,
  specimen    varchar(60) NOT NULL,
  price       numeric(10,2) NOT NULL,
  tat_hours   smallint
);

CREATE TABLE lab_orders (
  lab_order_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  consultation_id uuid NOT NULL REFERENCES consultations(consultation_id),
  patient_id      uuid NOT NULL REFERENCES patients(patient_id),
  ordered_by      uuid NOT NULL REFERENCES staff(staff_id),
  status          text NOT NULL DEFAULT 'ordered' CHECK (status IN ('ordered','sample_collected','in_progress','result_entered','released')), -- FR-LAB-04
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE lab_order_items (
  order_item_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  lab_order_id  uuid NOT NULL REFERENCES lab_orders(lab_order_id) ON DELETE CASCADE,
  lab_test_id   uuid NOT NULL REFERENCES lab_tests(lab_test_id),
  result_value  varchar(255),
  ref_range     varchar(120),
  entered_by    uuid REFERENCES staff(staff_id),
  released_at   timestamptz                                    -- patient visibility gate (FR-LAB-05, AC-04)
);
