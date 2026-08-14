-- ER Domain 1 — Identity, Scheduling, Appointments & Access (Design Fig. 3)
-- Note: status/role columns use TEXT + CHECK rather than native PG enums for clean
-- JPA @Enumerated(STRING) mapping; the constraint set is identical in effect.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
  user_id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email          varchar(255) NOT NULL UNIQUE,
  password_hash  varchar(100) NOT NULL,                       -- bcrypt only (FR-AUTH-02)
  role           text NOT NULL CHECK (role IN ('management','doctor','nurse','pharmacist','lab_tech','receptionist','billing_clerk','patient','family','sys_admin')),
  is_active      boolean NOT NULL DEFAULT true,
  failed_logins  int NOT NULL DEFAULT 0,                      -- FR-AUTH-06
  locked_until   timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE departments (
  department_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name           varchar(120) NOT NULL UNIQUE,
  dept_type      text NOT NULL CHECK (dept_type IN ('clinical','diagnostic','support')),
  consult_fee    numeric(10,2) NOT NULL DEFAULT 0
);

CREATE TABLE staff (
  staff_id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid NOT NULL UNIQUE REFERENCES users(user_id),
  department_id    uuid REFERENCES departments(department_id),
  staff_type       varchar(30) NOT NULL,
  full_name        varchar(160) NOT NULL,
  assigned_ward_id uuid                                       -- FK added in V3 (nurse ward scoping, AC-03)
);

CREATE TABLE patients (
  patient_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      uuid UNIQUE REFERENCES users(user_id),         -- nullable: walk-ins (FR-PAT-02)
  mrn          varchar(40) NOT NULL UNIQUE,
  full_name    varchar(160) NOT NULL,
  dob          date NOT NULL,
  sex          text NOT NULL CHECK (sex IN ('female','male','other')),
  phone        varchar(40),
  address      varchar(255),
  next_of_kin  varchar(160),                                  -- FR-PAT-05
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE schedules (
  schedule_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id    uuid NOT NULL REFERENCES staff(staff_id),
  weekday      smallint NOT NULL CHECK (weekday BETWEEN 0 AND 6),
  start_time   time NOT NULL,
  end_time     time NOT NULL,
  slot_minutes smallint NOT NULL DEFAULT 20 CHECK (slot_minutes BETWEEN 5 AND 120),
  room         varchar(40),
  CHECK (end_time > start_time)
);

CREATE TABLE slots (
  slot_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  schedule_id uuid NOT NULL REFERENCES schedules(schedule_id) ON DELETE CASCADE,
  doctor_id   uuid NOT NULL REFERENCES staff(staff_id),
  starts_at   timestamptz NOT NULL,
  ends_at     timestamptz NOT NULL,
  status      text NOT NULL DEFAULT 'available' CHECK (status IN ('available','booked','closed')),
  UNIQUE (doctor_id, starts_at)                               -- FR-APT-02: no double generation
);
CREATE INDEX idx_slots_avail ON slots (doctor_id, status, starts_at);

CREATE TABLE appointments (
  appointment_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  -- DD-04 / FR-APT-04: THE double-booking guard — one appointment per slot, enforced by the DB.
  slot_id        uuid NOT NULL UNIQUE REFERENCES slots(slot_id),
  patient_id     uuid NOT NULL REFERENCES patients(patient_id),
  department_id  uuid NOT NULL REFERENCES departments(department_id),
  status         text NOT NULL DEFAULT 'booked' CHECK (status IN ('booked','checked_in','in_consultation','completed','cancelled','no_show')),
  booked_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_appt_patient ON appointments (patient_id, status);

CREATE TABLE queue_entries (
  queue_entry_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id uuid NOT NULL UNIQUE REFERENCES appointments(appointment_id),
  checked_in_at  timestamptz NOT NULL DEFAULT now(),
  priority       smallint NOT NULL DEFAULT 100,               -- DD-06: (priority, checked_in_at) ordering
  status         text NOT NULL DEFAULT 'waiting' CHECK (status IN ('waiting','in_consultation','done'))
);

CREATE TABLE access_grants (
  grant_id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id      uuid NOT NULL REFERENCES patients(patient_id),
  grantee_user_id uuid NOT NULL REFERENCES users(user_id),
  scope           text[] NOT NULL,                            -- {admission_status,billing,appointments} (FR-FAM-01)
  expires_at      timestamptz NOT NULL,
  revoked_at      timestamptz,
  is_guardian     boolean NOT NULL DEFAULT false,             -- FR-FAM-03
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
  audit_id    bigserial PRIMARY KEY,
  user_id     uuid REFERENCES users(user_id),
  patient_id  uuid REFERENCES patients(patient_id),
  action      varchar(80) NOT NULL,
  entity_ref  varchar(120),
  meta        jsonb,
  occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_patient ON audit_log (patient_id, occurred_at);
CREATE INDEX idx_audit_user ON audit_log (user_id, occurred_at);
