-- =====================================================================
-- V900: DEMO SEED (synthetic data only — NFR-PRV-03)
-- Mirrors server/src/seed/run.js so a fresh Java deployment is demo-ready.
--
-- NOT part of the schema baseline. It only runs when Flyway is pointed at
-- this folder:   FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
-- Never enable on an installation holding real data.
--
-- All demo accounts share the password: Password123!
-- (bcrypt cost 12; Spring Security's BCrypt accepts the $2b$ prefix)
-- =====================================================================

-- ---------- departments -------------------------------------------------
INSERT INTO departments (name, dept_type, consult_fee) VALUES
  ('General Medicine', 'clinical',   80),
  ('Pediatrics',       'clinical',   70),
  ('Laboratory',       'diagnostic',  0),
  ('Pharmacy',         'support',     0)
ON CONFLICT (name) DO NOTHING;

-- ---------- users (one per role) ----------------------------------------
INSERT INTO users (email, password_hash, role) VALUES
  ('admin@medicore.test',      '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'sys_admin'),
  ('doctor@medicore.test',     '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('reception@medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'receptionist'),
  ('pharmacist@medicore.test', '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'pharmacist'),
  ('billing@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'billing_clerk'),
  ('management@medicore.test', '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'management'),
  ('patient@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'patient')
ON CONFLICT (email) DO NOTHING;

-- ---------- staff --------------------------------------------------------
INSERT INTO staff (user_id, department_id, staff_type, full_name)
SELECT u.user_id, d.department_id, v.staff_type, v.full_name
FROM (VALUES
  ('doctor@medicore.test',     'doctor',        'Dr. Abena Mensah',     'General Medicine'),
  ('reception@medicore.test',  'receptionist',  'Front Desk',           'General Medicine'),
  ('admin@medicore.test',      'sys_admin',     'System Administrator', NULL),
  ('pharmacist@medicore.test', 'pharmacist',    'Pharm. Kojo Asante',   NULL),
  ('billing@medicore.test',    'billing_clerk', 'Cashier One',          NULL),
  ('management@medicore.test', 'management',    'Hospital Manager',     NULL)
) AS v(email, staff_type, full_name, dept_name)
JOIN users u ON u.email = v.email
LEFT JOIN departments d ON d.name = v.dept_name
ON CONFLICT (user_id) DO NOTHING;

-- ---------- demo patient -------------------------------------------------
INSERT INTO patients (user_id, mrn, full_name, dob, sex, phone)
SELECT u.user_id, 'MRN-DEMO01', 'Kwame Owusu', DATE '1990-05-14', 'male', '+233200000000'
FROM users u WHERE u.email = 'patient@medicore.test'
ON CONFLICT (mrn) DO NOTHING;

-- ---------- weekly clinics: Mon/Wed/Fri 09:00-12:00, 20-minute slots ------
INSERT INTO schedules (doctor_id, weekday, start_time, end_time, slot_minutes, room)
SELECT s.staff_id, w.weekday, TIME '09:00', TIME '12:00', 20, 'C1'
FROM staff s CROSS JOIN (VALUES (1), (3), (5)) AS w(weekday)
WHERE s.full_name = 'Dr. Abena Mensah'
  AND NOT EXISTS (SELECT 1 FROM schedules x WHERE x.doctor_id = s.staff_id AND x.weekday = w.weekday);

-- Rolling slot generation: next 28 days from the deploy date, matching
-- SlotGenerator's arithmetic (n slots where n*slot_minutes fits the window).
INSERT INTO slots (schedule_id, doctor_id, starts_at, ends_at, status)
SELECT sch.schedule_id,
       sch.doctor_id,
       (d.day + sch.start_time)::timestamptz + (n.n * make_interval(mins => sch.slot_minutes)),
       (d.day + sch.start_time)::timestamptz + ((n.n + 1) * make_interval(mins => sch.slot_minutes)),
       'available'
FROM schedules sch
JOIN staff st ON st.staff_id = sch.doctor_id AND st.full_name = 'Dr. Abena Mensah'
CROSS JOIN LATERAL generate_series(CURRENT_DATE, CURRENT_DATE + 27, INTERVAL '1 day') AS d(day)
CROSS JOIN LATERAL generate_series(0,
       (EXTRACT(EPOCH FROM (sch.end_time - sch.start_time)) / 60 / sch.slot_minutes)::int - 1) AS n(n)
WHERE EXTRACT(DOW FROM d.day) = sch.weekday
ON CONFLICT DO NOTHING;                                   -- UNIQUE (doctor_id, starts_at)

-- ---------- pharmacy catalogue + opening stock ---------------------------
INSERT INTO drugs (generic_name, form, strength, unit_price, reorder_level)
SELECT * FROM (VALUES
  ('Amoxicillin', 'capsule', '500mg', 1.50::numeric, 100),
  ('Paracetamol', 'tablet',  '500mg', 0.30::numeric, 200)
) AS v(generic_name, form, strength, unit_price, reorder_level)
WHERE NOT EXISTS (SELECT 1 FROM drugs dd WHERE dd.generic_name = v.generic_name AND dd.strength = v.strength);

-- Two batches per drug with different expiries so FEFO is visible in the demo.
INSERT INTO stock_batches (drug_id, batch_no, expiry_date, qty_on_hand, unit_cost)
SELECT d.drug_id, v.batch_no, CURRENT_DATE + v.expiry_days, v.qty, v.cost
FROM (VALUES
  ('Amoxicillin', 'AMX-EARLY', 120, 300, 0.90::numeric),
  ('Amoxicillin', 'AMX-LATE',  360, 500, 0.85::numeric),
  ('Paracetamol', 'PCM-EARLY',  90, 800, 0.10::numeric),
  ('Paracetamol', 'PCM-LATE',  400, 800, 0.10::numeric)
) AS v(generic_name, batch_no, expiry_days, qty, cost)
JOIN drugs d ON d.generic_name = v.generic_name
WHERE NOT EXISTS (SELECT 1 FROM stock_batches b WHERE b.batch_no = v.batch_no);

-- ---------- lab catalogue + facility (Phase-2 modules, data ready) --------
INSERT INTO lab_tests (name, specimen, price, tat_hours) VALUES
  ('Full Blood Count', 'blood', 45, 4),
  ('Malaria RDT',      'blood', 20, 1)
ON CONFLICT (name) DO NOTHING;

INSERT INTO wards (name, daily_tariff) VALUES ('Female Medical Ward', 120)
ON CONFLICT (name) DO NOTHING;

INSERT INTO rooms (ward_id, room_no)
SELECT w.ward_id, 'FM-1' FROM wards w WHERE w.name = 'Female Medical Ward'
ON CONFLICT (ward_id, room_no) DO NOTHING;

INSERT INTO beds (room_id, label)
SELECT r.room_id, v.label
FROM rooms r JOIN wards w ON w.ward_id = r.ward_id AND w.name = 'Female Medical Ward',
     (VALUES ('B1'), ('B2')) AS v(label)
WHERE r.room_no = 'FM-1'
ON CONFLICT (room_id, label) DO NOTHING;
