-- ER Domain 3 — Billing, Payments & Facility (Design Fig. 5)
CREATE TABLE invoices (
  invoice_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id  uuid NOT NULL REFERENCES patients(patient_id),
  visit_ref   varchar(80),
  status      text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','issued','partially_paid','paid','void')), -- FR-BIL-05
  void_reason varchar(255),                                   -- FR-BIL-07
  issued_at   timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_inv_patient ON invoices (patient_id, status);

-- DD-05: append-only line items; totals computed, never stored mutable.
CREATE TABLE invoice_items (
  item_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id  uuid NOT NULL REFERENCES invoices(invoice_id),
  source_type text NOT NULL CHECK (source_type IN ('consultation','pharmacy','laboratory','bed_day','other')), -- FR-BIL-02
  source_id   uuid,
  description varchar(255) NOT NULL,
  amount      numeric(10,2) NOT NULL CHECK (amount >= 0),
  posted_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE payments (
  payment_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id  uuid NOT NULL REFERENCES invoices(invoice_id),
  method      text NOT NULL CHECK (method IN ('itc','cash','pos')),   -- ITC Payments (SRS v1.2, DD-07)
  amount      numeric(10,2) NOT NULL CHECK (amount > 0),
  gateway_ref varchar(120) UNIQUE,                            -- server-side verification key (NFR-SEC-06)
  status      text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','paid','failed')),
  paid_at     timestamptz
);

CREATE TABLE wards (
  ward_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name         varchar(120) NOT NULL UNIQUE,
  daily_tariff numeric(10,2) NOT NULL DEFAULT 0               -- FR-FAC-05
);

CREATE TABLE rooms (
  room_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ward_id uuid NOT NULL REFERENCES wards(ward_id),
  room_no varchar(20) NOT NULL,
  UNIQUE (ward_id, room_no)
);

CREATE TABLE beds (
  bed_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id uuid NOT NULL REFERENCES rooms(room_id),
  label   varchar(20) NOT NULL,
  status  text NOT NULL DEFAULT 'available' CHECK (status IN ('available','reserved','occupied','cleaning','maintenance')), -- FR-FAC-01
  UNIQUE (room_id, label)
);

CREATE TABLE admissions (
  admission_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id       uuid NOT NULL REFERENCES patients(patient_id),
  admitting_doctor uuid NOT NULL REFERENCES staff(staff_id),
  bed_id           uuid NOT NULL REFERENCES beds(bed_id),
  invoice_id       uuid REFERENCES invoices(invoice_id),
  admitted_at      timestamptz NOT NULL DEFAULT now(),
  discharged_at    timestamptz,
  status           text NOT NULL DEFAULT 'active' CHECK (status IN ('active','discharged'))
);
-- FR-FAC-03: at most one ACTIVE admission per bed (Design §5.1).
CREATE UNIQUE INDEX one_active_admission_per_bed ON admissions (bed_id) WHERE status = 'active';

CREATE TABLE bed_assignments (
  assignment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  admission_id  uuid NOT NULL REFERENCES admissions(admission_id),
  bed_id        uuid NOT NULL REFERENCES beds(bed_id),
  from_ts       timestamptz NOT NULL DEFAULT now(),
  to_ts         timestamptz                                    -- null = current bed (FR-FAC-04)
);

ALTER TABLE staff ADD CONSTRAINT fk_staff_ward FOREIGN KEY (assigned_ward_id) REFERENCES wards(ward_id);
ALTER TABLE consultations ADD CONSTRAINT fk_cons_admission FOREIGN KEY (admission_id) REFERENCES admissions(admission_id);
