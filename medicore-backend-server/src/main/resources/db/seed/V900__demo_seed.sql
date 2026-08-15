-- =====================================================================
-- V900: DEMO SEED — a working day at MediCore Teaching Hospital, Accra.
--
-- Synthetic data only (NFR-PRV-03). Every person, MRN, phone number and
-- clinical note below is invented; none of it describes a real patient.
--
-- NOT part of the schema baseline. It only runs when Flyway is pointed at
-- this folder:   FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
-- Never enable on an installation holding real data.
--
-- All demo accounts share the password: Password123!
-- (bcrypt cost 12; Spring Security's BCrypt accepts the $2b$ prefix)
--
-- Everything is dated RELATIVE to the deployment date, so the demo always
-- shows a live clinic: visits behind CURRENT_DATE, a queue this morning,
-- and bookable slots ahead. Each visit gets an explicit slot at a known
-- time, so the appointment list, the EMR, the pharmacy worklist and the
-- invoices all tell the same story about the same visit.
--
-- ID prefixes (hex, so they are valid UUIDs):
--   d1 departments  e1 wards      e2 rooms      b1 beds
--   f1 users        f2 staff      aa patients   c0 access grants
--   5c schedules    5a slots      a1 appointments  91 queue entries
--   c1 consultations  ad addendums  71 vitals
--   d2 drugs        ba batches    a2 prescriptions  b2 rx items  df dispenses
--   11 lab tests    10 lab orders 12 lab order items
--   ac admissions   be bed assignments
--   1e invoices     1f invoice items  9a payments  80 outbox
-- =====================================================================

-- =====================================================================
-- 1. DEPARTMENTS  (consult_fee drives the consultation charge, FR-BIL-02)
-- =====================================================================
INSERT INTO departments (department_id, name, dept_type, consult_fee) VALUES
  ('d1000000-0000-4000-8000-000000000001', 'General Medicine',         'clinical',    80),
  ('d1000000-0000-4000-8000-000000000002', 'Pediatrics',               'clinical',    70),
  ('d1000000-0000-4000-8000-000000000003', 'Obstetrics & Gynaecology', 'clinical',   120),
  ('d1000000-0000-4000-8000-000000000004', 'General Surgery',          'clinical',   150),
  ('d1000000-0000-4000-8000-000000000005', 'Accident & Emergency',     'clinical',    60),
  ('d1000000-0000-4000-8000-000000000006', 'Orthopaedics',             'clinical',   130),
  ('d1000000-0000-4000-8000-000000000007', 'Laboratory',               'diagnostic',   0),
  ('d1000000-0000-4000-8000-000000000008', 'Radiology',                'diagnostic',   0),
  ('d1000000-0000-4000-8000-000000000009', 'Pharmacy',                 'support',      0),
  ('d1000000-0000-4000-8000-00000000000a', 'Administration',           'support',      0)
ON CONFLICT (name) DO NOTHING;

-- =====================================================================
-- 2. FACILITY — wards, rooms, beds (FR-FAC-01)
-- =====================================================================
INSERT INTO wards (ward_id, name, daily_tariff) VALUES
  ('e1000000-0000-4000-8000-000000000001', 'Female Medical Ward', 120),
  ('e1000000-0000-4000-8000-000000000002', 'Male Medical Ward',   120),
  ('e1000000-0000-4000-8000-000000000003', 'Paediatric Ward',     100),
  ('e1000000-0000-4000-8000-000000000004', 'Maternity Ward',      150),
  ('e1000000-0000-4000-8000-000000000005', 'Surgical Ward',       180),
  ('e1000000-0000-4000-8000-000000000006', 'Intensive Care Unit', 450)
ON CONFLICT (name) DO NOTHING;

INSERT INTO rooms (room_id, ward_id, room_no) VALUES
  ('e2000000-0000-4000-8000-000000000001', 'e1000000-0000-4000-8000-000000000001', 'FM-1'),
  ('e2000000-0000-4000-8000-000000000002', 'e1000000-0000-4000-8000-000000000001', 'FM-2'),
  ('e2000000-0000-4000-8000-000000000003', 'e1000000-0000-4000-8000-000000000002', 'MM-1'),
  ('e2000000-0000-4000-8000-000000000004', 'e1000000-0000-4000-8000-000000000002', 'MM-2'),
  ('e2000000-0000-4000-8000-000000000005', 'e1000000-0000-4000-8000-000000000003', 'PD-1'),
  ('e2000000-0000-4000-8000-000000000006', 'e1000000-0000-4000-8000-000000000004', 'MT-1'),
  ('e2000000-0000-4000-8000-000000000007', 'e1000000-0000-4000-8000-000000000005', 'SG-1'),
  ('e2000000-0000-4000-8000-000000000008', 'e1000000-0000-4000-8000-000000000006', 'ICU-1')
ON CONFLICT (ward_id, room_no) DO NOTHING;

-- Four beds in each general room, two in the ICU.
INSERT INTO beds (bed_id, room_id, label, status)
SELECT ('b1000000-0000-4000-8000-0000000000' ||
        lpad((row_number() OVER (ORDER BY r.room_no, v.label))::text, 2, '0'))::uuid,
       r.room_id, v.label, 'available'
FROM rooms r
CROSS JOIN (VALUES ('A'), ('B'), ('C'), ('D')) AS v(label)
WHERE r.room_no <> 'ICU-1'
ON CONFLICT (room_id, label) DO NOTHING;

INSERT INTO beds (bed_id, room_id, label, status)
SELECT ('b1000000-0000-4000-8000-0000000001' ||
        lpad((row_number() OVER (ORDER BY v.label))::text, 2, '0'))::uuid,
       r.room_id, v.label, 'available'
FROM rooms r CROSS JOIN (VALUES ('A'), ('B')) AS v(label)
WHERE r.room_no = 'ICU-1'
ON CONFLICT (room_id, label) DO NOTHING;

-- =====================================================================
-- 3. USER ACCOUNTS
-- The seven addresses printed on the login page keep their well-known
-- names (doctor@, reception@, …) so existing documentation stays true;
-- everyone else has a realistic work address.
-- =====================================================================
INSERT INTO users (user_id, email, password_hash, role) VALUES
  -- clinicians
  ('f1000000-0000-4000-8000-000000000001', 'doctor@medicore.test',     '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('f1000000-0000-4000-8000-000000000002', 'k.boateng@medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('f1000000-0000-4000-8000-000000000003', 'e.sarpong@medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('f1000000-0000-4000-8000-000000000004', 'a.owusu@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('f1000000-0000-4000-8000-000000000005', 'a.frimpong@medicore.test', '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('f1000000-0000-4000-8000-000000000006', 'y.antwi@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('f1000000-0000-4000-8000-000000000007', 'n.quaye@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  ('f1000000-0000-4000-8000-000000000008', 's.agbeko@medicore.test',   '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'doctor'),
  -- nursing
  ('f1000000-0000-4000-8000-000000000011', 'c.adjei@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'nurse'),
  ('f1000000-0000-4000-8000-000000000012', 'g.amponsah@medicore.test', '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'nurse'),
  ('f1000000-0000-4000-8000-000000000013', 'a.nyarko@medicore.test',   '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'nurse'),
  ('f1000000-0000-4000-8000-000000000014', 'v.asante@medicore.test',   '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'nurse'),
  ('f1000000-0000-4000-8000-000000000015', 'i.mohammed@medicore.test', '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'nurse'),
  ('f1000000-0000-4000-8000-000000000016', 'm.tetteh@medicore.test',   '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'nurse'),
  -- pharmacy, laboratory
  ('f1000000-0000-4000-8000-000000000021', 'pharmacist@medicore.test', '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'pharmacist'),
  ('f1000000-0000-4000-8000-000000000022', 'n.lamptey@medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'pharmacist'),
  ('f1000000-0000-4000-8000-000000000023', 'd.ofori@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'lab_tech'),
  ('f1000000-0000-4000-8000-000000000024', 'h.boakye@medicore.test',   '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'lab_tech'),
  -- front desk, cash office, management, IT
  ('f1000000-0000-4000-8000-000000000031', 'reception@medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'receptionist'),
  ('f1000000-0000-4000-8000-000000000032', 'e.kotey@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'receptionist'),
  ('f1000000-0000-4000-8000-000000000033', 'r.ansah@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'receptionist'),
  ('f1000000-0000-4000-8000-000000000041', 'billing@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'billing_clerk'),
  ('f1000000-0000-4000-8000-000000000042', 's.nkrumah@medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'billing_clerk'),
  ('f1000000-0000-4000-8000-000000000051', 'management@medicore.test', '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'management'),
  ('f1000000-0000-4000-8000-000000000052', 'k.boadu@medicore.test',    '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'management'),
  ('f1000000-0000-4000-8000-000000000061', 'admin@medicore.test',      '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'sys_admin'),
  -- patient portal accounts
  ('f1000000-0000-4000-8000-000000000071', 'patient@medicore.test',            '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'patient'),
  ('f1000000-0000-4000-8000-000000000072', 'a.boakye@patients.medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'patient'),
  ('f1000000-0000-4000-8000-000000000073', 'k.mensah@patients.medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'patient'),
  ('f1000000-0000-4000-8000-000000000074', 'e.baidoo@patients.medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'patient'),
  ('f1000000-0000-4000-8000-000000000075', 'c.mensah@patients.medicore.test',  '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'patient'),
  -- family member: sees only what a grant allows (FR-FAM-01)
  ('f1000000-0000-4000-8000-000000000081', 'y.owusu@patients.medicore.test',   '$2b$12$qr8jw0M8FZxznMYKGDQR/ejMJoIb1lNR4Myj6pzE7ZDXB5OlFs.NW', 'family')
ON CONFLICT (email) DO NOTHING;

-- =====================================================================
-- 4. STAFF ROSTER
-- Nurses carry assigned_ward_id: that is what the WARD policy scope
-- resolves against (AC-03).
-- =====================================================================
INSERT INTO staff (staff_id, user_id, department_id, staff_type, full_name, assigned_ward_id) VALUES
  ('f2000000-0000-4000-8000-000000000001', 'f1000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'doctor', 'Dr. Abena Mensah',     NULL),
  ('f2000000-0000-4000-8000-000000000002', 'f1000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'doctor', 'Dr. Kwabena Boateng',  NULL),
  ('f2000000-0000-4000-8000-000000000003', 'f1000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000002', 'doctor', 'Dr. Efua Sarpong',     NULL),
  ('f2000000-0000-4000-8000-000000000004', 'f1000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000002', 'doctor', 'Dr. Ama Serwaa Owusu', NULL),
  ('f2000000-0000-4000-8000-000000000005', 'f1000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000003', 'doctor', 'Dr. Akosua Frimpong',  NULL),
  ('f2000000-0000-4000-8000-000000000006', 'f1000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000004', 'doctor', 'Dr. Yaw Antwi',        NULL),
  ('f2000000-0000-4000-8000-000000000007', 'f1000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000005', 'doctor', 'Dr. Nii Armah Quaye',  NULL),
  ('f2000000-0000-4000-8000-000000000008', 'f1000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000006', 'doctor', 'Dr. Selorm Agbeko',    NULL),
  ('f2000000-0000-4000-8000-000000000011', 'f1000000-0000-4000-8000-000000000011', 'd1000000-0000-4000-8000-000000000001', 'nurse', 'Sister Comfort Adjei',   'e1000000-0000-4000-8000-000000000001'),
  ('f2000000-0000-4000-8000-000000000012', 'f1000000-0000-4000-8000-000000000012', 'd1000000-0000-4000-8000-000000000001', 'nurse', 'Nurse Grace Amponsah',   'e1000000-0000-4000-8000-000000000002'),
  ('f2000000-0000-4000-8000-000000000013', 'f1000000-0000-4000-8000-000000000013', 'd1000000-0000-4000-8000-000000000002', 'nurse', 'Nurse Adjoa Nyarko',     'e1000000-0000-4000-8000-000000000003'),
  ('f2000000-0000-4000-8000-000000000014', 'f1000000-0000-4000-8000-000000000014', 'd1000000-0000-4000-8000-000000000003', 'nurse', 'Midwife Vida Asante',    'e1000000-0000-4000-8000-000000000004'),
  ('f2000000-0000-4000-8000-000000000015', 'f1000000-0000-4000-8000-000000000015', 'd1000000-0000-4000-8000-000000000004', 'nurse', 'Nurse Ibrahim Mohammed', 'e1000000-0000-4000-8000-000000000005'),
  ('f2000000-0000-4000-8000-000000000016', 'f1000000-0000-4000-8000-000000000016', 'd1000000-0000-4000-8000-000000000005', 'nurse', 'Nurse Michael Tetteh',   'e1000000-0000-4000-8000-000000000006'),
  ('f2000000-0000-4000-8000-000000000021', 'f1000000-0000-4000-8000-000000000021', 'd1000000-0000-4000-8000-000000000009', 'pharmacist', 'Pharm. Kojo Asante',         NULL),
  ('f2000000-0000-4000-8000-000000000022', 'f1000000-0000-4000-8000-000000000022', 'd1000000-0000-4000-8000-000000000009', 'pharmacist', 'Pharm. Naa Adjeley Lamptey', NULL),
  ('f2000000-0000-4000-8000-000000000023', 'f1000000-0000-4000-8000-000000000023', 'd1000000-0000-4000-8000-000000000007', 'lab_tech',   'Mr. Daniel Ofori',           NULL),
  ('f2000000-0000-4000-8000-000000000024', 'f1000000-0000-4000-8000-000000000024', 'd1000000-0000-4000-8000-000000000007', 'lab_tech',   'Ms. Hannah Boakye',          NULL),
  ('f2000000-0000-4000-8000-000000000031', 'f1000000-0000-4000-8000-000000000031', 'd1000000-0000-4000-8000-000000000001', 'receptionist',  'Mercy Dartey',     NULL),
  ('f2000000-0000-4000-8000-000000000032', 'f1000000-0000-4000-8000-000000000032', 'd1000000-0000-4000-8000-000000000002', 'receptionist',  'Emmanuel Kotey',   NULL),
  ('f2000000-0000-4000-8000-000000000033', 'f1000000-0000-4000-8000-000000000033', 'd1000000-0000-4000-8000-000000000005', 'receptionist',  'Rita Ansah',       NULL),
  ('f2000000-0000-4000-8000-000000000041', 'f1000000-0000-4000-8000-000000000041', 'd1000000-0000-4000-8000-00000000000a', 'billing_clerk', 'Gifty Owusu-Ansah', NULL),
  ('f2000000-0000-4000-8000-000000000042', 'f1000000-0000-4000-8000-000000000042', 'd1000000-0000-4000-8000-00000000000a', 'billing_clerk', 'Samuel Nkrumah',   NULL),
  ('f2000000-0000-4000-8000-000000000051', 'f1000000-0000-4000-8000-000000000051', 'd1000000-0000-4000-8000-00000000000a', 'management',    'Bright Agyeman',   NULL),
  ('f2000000-0000-4000-8000-000000000052', 'f1000000-0000-4000-8000-000000000052', 'd1000000-0000-4000-8000-00000000000a', 'management',    'Dr. Kwesi Boadu',  NULL),
  ('f2000000-0000-4000-8000-000000000061', 'f1000000-0000-4000-8000-000000000061', 'd1000000-0000-4000-8000-00000000000a', 'sys_admin',     'Nana Kwaku Antwi', NULL)
ON CONFLICT (user_id) DO NOTHING;

-- =====================================================================
-- 5. PATIENT REGISTER
-- MRNs follow the hospital's YEAR-SEQUENCE convention. Patients with a
-- user_id can sign in to the portal; the rest are front-desk
-- registrations (FR-PAT-02 walk-ins).
-- =====================================================================
INSERT INTO patients (patient_id, user_id, mrn, full_name, dob, sex, phone, address, next_of_kin) VALUES
  ('aa000000-0000-4000-8000-000000000001', 'f1000000-0000-4000-8000-000000000071', 'MRN-2021-0043', 'Kwame Owusu',     DATE '1990-05-14', 'male',   '+233 24 411 8820', 'H/No. 12 Ring Road East, Osu, Accra',   'Yaa Owusu (wife) · +233 24 411 8821'),
  ('aa000000-0000-4000-8000-000000000002', 'f1000000-0000-4000-8000-000000000072', 'MRN-2021-0187', 'Akua Boakye',     DATE '1985-11-02', 'female', '+233 20 776 3391', 'Flat 4, Dansoman High Street, Accra',   'Kofi Boakye (brother) · +233 20 776 3390'),
  ('aa000000-0000-4000-8000-000000000003', NULL,                                   'MRN-2022-0512', 'Yaw Darko',       DATE '1978-03-27', 'male',   '+233 27 330 1145', 'Plot 8, Adenta Housing Down, Accra',    'Afia Darko (wife) · +233 27 330 1146'),
  ('aa000000-0000-4000-8000-000000000004', NULL,                                   'MRN-2022-0904', 'Adwoa Asantewaa', DATE '1996-07-19', 'female', '+233 55 208 7734', 'Room 3, Madina Estates, Accra',         'Yaw Asantewaa (father) · +233 55 208 7730'),
  ('aa000000-0000-4000-8000-000000000005', 'f1000000-0000-4000-8000-000000000073', 'MRN-2019-0071', 'Kofi Mensah',     DATE '1962-01-08', 'male',   '+233 24 900 4412', '15 Labone Crescent, Accra',             'Ama Mensah (daughter) · +233 24 900 4410'),
  ('aa000000-0000-4000-8000-000000000006', NULL,                                   'MRN-2024-1180', 'Abena Nyarko',    DATE '2019-04-22', 'female', '+233 26 551 2280', 'Community 7, Tema',                     'Esther Nyarko (mother) · +233 26 551 2281'),
  ('aa000000-0000-4000-8000-000000000007', NULL,                                   'MRN-2024-1206', 'Kwabena Amoah',   DATE '2016-09-30', 'male',   '+233 24 118 9903', 'Ashaiman Lebanon Zone, Tema',           'Mary Amoah (mother) · +233 24 118 9900'),
  ('aa000000-0000-4000-8000-000000000008', 'f1000000-0000-4000-8000-000000000074', 'MRN-2025-0233', 'Esi Baidoo',      DATE '1993-12-11', 'female', '+233 50 447 6612', '22 Spintex Road, Accra',                'Nana Baidoo (husband) · +233 50 447 6613'),
  ('aa000000-0000-4000-8000-000000000009', NULL,                                   'MRN-2023-0318', 'Nana Ama Serwaa', DATE '1988-06-05', 'female', '+233 24 662 0091', 'East Legon Hills, Accra',               'Kojo Serwaa (husband) · +233 24 662 0092'),
  ('aa000000-0000-4000-8000-00000000000a', NULL,                                   'MRN-2020-0655', 'Ibrahim Yakubu',  DATE '1971-02-17', 'male',   '+233 54 300 8817', 'Nima Junction, Accra',                  'Fatima Yakubu (wife) · +233 54 300 8818'),
  ('aa000000-0000-4000-8000-00000000000b', NULL,                                   'MRN-2018-0092', 'Comfort Ankrah',  DATE '1955-08-23', 'female', '+233 24 208 5510', 'Teshie Nungua Estates, Accra',          'Daniel Ankrah (son) · +233 24 208 5511'),
  ('aa000000-0000-4000-8000-00000000000c', NULL,                                   'MRN-2025-0741', 'Emmanuel Tetteh', DATE '2001-10-04', 'male',   '+233 59 771 2204', 'Legon Hall Annex, University of Ghana', 'Grace Tetteh (mother) · +233 59 771 2200'),
  ('aa000000-0000-4000-8000-00000000000d', NULL,                                   'MRN-2025-0802', 'Mavis Agyeman',   DATE '1999-05-30', 'female', '+233 26 990 3345', 'Achimota Mile 7, Accra',                'Kwadwo Agyeman (brother) · +233 26 990 3346'),
  ('aa000000-0000-4000-8000-00000000000e', NULL,                                   'MRN-2023-0447', 'Solomon Adjei',   DATE '1983-09-12', 'male',   '+233 20 334 7781', 'Sakumono Estates, Tema',                'Vida Adjei (wife) · +233 20 334 7782'),
  ('aa000000-0000-4000-8000-00000000000f', NULL,                                   'MRN-2022-0231', 'Grace Appiah',    DATE '1974-04-03', 'female', '+233 24 776 1102', 'Kaneshie First Light, Accra',           'Isaac Appiah (husband) · +233 24 776 1103'),
  ('aa000000-0000-4000-8000-000000000010', NULL,                                   'MRN-2021-0388', 'Daniel Ofosu',    DATE '1968-12-25', 'male',   '+233 27 881 4420', 'Weija Old Barrier, Accra',              'Rebecca Ofosu (wife) · +233 27 881 4421'),
  ('aa000000-0000-4000-8000-000000000011', NULL,                                   'MRN-2024-1355', 'Patience Amoako', DATE '2010-02-14', 'female', '+233 24 559 6612', 'Haatso Ecomog, Accra',                  'Felicia Amoako (mother) · +233 24 559 6613'),
  ('aa000000-0000-4000-8000-000000000012', NULL,                                   'MRN-2026-0021', 'Musah Alhassan',  DATE '1990-11-19', 'male',   '+233 55 442 0087', 'Abeka Lapaz, Accra',                    'Zeinab Alhassan (sister) · +233 55 442 0088'),
  ('aa000000-0000-4000-8000-000000000013', 'f1000000-0000-4000-8000-000000000075', 'MRN-2026-0034', 'Cynthia Mensah',  DATE '2003-03-08', 'female', '+233 24 003 9912', 'Adabraka, Accra',                       'Kofi Mensah (father) · +233 24 900 4412'),
  ('aa000000-0000-4000-8000-000000000014', NULL,                                   'MRN-2019-0410', 'Joseph Boateng',  DATE '1959-07-01', 'male',   '+233 20 118 3345', 'Tesano, Accra',                         'Akosua Boateng (wife) · +233 20 118 3346')
ON CONFLICT (mrn) DO NOTHING;

-- FR-FAM-01: Kwame's wife may see appointments, admission status and bills
-- — never the clinical record.
INSERT INTO access_grants (grant_id, patient_id, grantee_user_id, scope, expires_at, is_guardian)
VALUES ('c0000000-0000-4000-8000-000000000001',
        'aa000000-0000-4000-8000-000000000001',
        'f1000000-0000-4000-8000-000000000081',
        ARRAY['appointments','admission_status','billing'],
        now() + interval '60 days', false)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 6. CLINIC TIMETABLE (FR-APT-01)
-- Regular weekly clinics, plus an afternoon duty clinic on whichever
-- weekday the system is deployed, so there are always same-day slots.
-- weekday: 0 = Sunday … 6 = Saturday.
-- =====================================================================
INSERT INTO schedules (schedule_id, doctor_id, weekday, start_time, end_time, slot_minutes, room) VALUES
  -- Dr. Abena Mensah — General Medicine OPD
  ('5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', 1, TIME '09:00', TIME '12:00', 20, 'Consulting Room 1'),
  ('5c000000-0000-4000-8000-000000000002', 'f2000000-0000-4000-8000-000000000001', 3, TIME '09:00', TIME '12:00', 20, 'Consulting Room 1'),
  ('5c000000-0000-4000-8000-000000000003', 'f2000000-0000-4000-8000-000000000001', 5, TIME '09:00', TIME '12:00', 20, 'Consulting Room 1'),
  -- Dr. Kwabena Boateng — General Medicine
  ('5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002', 2, TIME '08:30', TIME '12:30', 20, 'Consulting Room 2'),
  ('5c000000-0000-4000-8000-000000000005', 'f2000000-0000-4000-8000-000000000002', 4, TIME '08:30', TIME '12:30', 20, 'Consulting Room 2'),
  -- Dr. Efua Sarpong — Paediatric clinic
  ('5c000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000003', 1, TIME '08:00', TIME '11:00', 15, 'Paediatric OPD A'),
  ('5c000000-0000-4000-8000-000000000007', 'f2000000-0000-4000-8000-000000000003', 4, TIME '08:00', TIME '11:00', 15, 'Paediatric OPD A'),
  -- Dr. Ama Serwaa Owusu — Paediatrics
  ('5c000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000004', 2, TIME '09:00', TIME '12:00', 15, 'Paediatric OPD B'),
  ('5c000000-0000-4000-8000-000000000009', 'f2000000-0000-4000-8000-000000000004', 5, TIME '09:00', TIME '12:00', 15, 'Paediatric OPD B'),
  -- Dr. Akosua Frimpong — antenatal / gynaecology
  ('5c000000-0000-4000-8000-00000000000a', 'f2000000-0000-4000-8000-000000000005', 2, TIME '08:00', TIME '12:00', 20, 'Antenatal Clinic'),
  ('5c000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000005', 4, TIME '08:00', TIME '12:00', 20, 'Antenatal Clinic'),
  -- Dr. Yaw Antwi — surgical outpatient
  ('5c000000-0000-4000-8000-00000000000c', 'f2000000-0000-4000-8000-000000000006', 3, TIME '10:00', TIME '13:00', 30, 'Surgical OPD'),
  ('5c000000-0000-4000-8000-00000000000d', 'f2000000-0000-4000-8000-000000000006', 6, TIME '09:00', TIME '12:00', 30, 'Surgical OPD'),
  -- Dr. Nii Armah Quaye — the A&E review clinic runs every day
  ('5c000000-0000-4000-8000-00000000000e', 'f2000000-0000-4000-8000-000000000007', 0, TIME '09:00', TIME '12:00', 15, 'Emergency Bay 2'),
  ('5c000000-0000-4000-8000-00000000000f', 'f2000000-0000-4000-8000-000000000007', 1, TIME '09:00', TIME '12:00', 15, 'Emergency Bay 2'),
  ('5c000000-0000-4000-8000-000000000010', 'f2000000-0000-4000-8000-000000000007', 2, TIME '09:00', TIME '12:00', 15, 'Emergency Bay 2'),
  ('5c000000-0000-4000-8000-000000000011', 'f2000000-0000-4000-8000-000000000007', 3, TIME '09:00', TIME '12:00', 15, 'Emergency Bay 2'),
  ('5c000000-0000-4000-8000-000000000012', 'f2000000-0000-4000-8000-000000000007', 4, TIME '09:00', TIME '12:00', 15, 'Emergency Bay 2'),
  ('5c000000-0000-4000-8000-000000000013', 'f2000000-0000-4000-8000-000000000007', 5, TIME '09:00', TIME '12:00', 15, 'Emergency Bay 2'),
  ('5c000000-0000-4000-8000-000000000014', 'f2000000-0000-4000-8000-000000000007', 6, TIME '09:00', TIME '12:00', 15, 'Emergency Bay 2'),
  -- Dr. Selorm Agbeko — orthopaedic clinic
  ('5c000000-0000-4000-8000-000000000015', 'f2000000-0000-4000-8000-000000000008', 3, TIME '08:30', TIME '11:30', 20, 'Orthopaedic Clinic'),
  ('5c000000-0000-4000-8000-000000000016', 'f2000000-0000-4000-8000-000000000008', 5, TIME '08:30', TIME '11:30', 20, 'Orthopaedic Clinic')
ON CONFLICT DO NOTHING;

-- Afternoon duty clinics on today's weekday: guarantees same-day slots
-- whichever day of the week the demo is deployed.
INSERT INTO schedules (schedule_id, doctor_id, weekday, start_time, end_time, slot_minutes, room)
SELECT v.schedule_id::uuid, v.doctor_id::uuid, EXTRACT(DOW FROM CURRENT_DATE)::smallint,
       v.start_time::time, v.end_time::time, v.slot_minutes::smallint, v.room
FROM (VALUES
  ('5c000000-0000-4000-8000-000000000021', 'f2000000-0000-4000-8000-000000000001', '14:00', '17:00', 20, 'Consulting Room 1'),
  ('5c000000-0000-4000-8000-000000000022', 'f2000000-0000-4000-8000-000000000003', '14:00', '17:00', 15, 'Paediatric OPD A'),
  ('5c000000-0000-4000-8000-000000000023', 'f2000000-0000-4000-8000-000000000007', '14:00', '18:00', 15, 'Emergency Bay 2'),
  ('5c000000-0000-4000-8000-000000000024', 'f2000000-0000-4000-8000-000000000005', '14:00', '17:00', 20, 'Antenatal Clinic')
) AS v(schedule_id, doctor_id, start_time, end_time, slot_minutes, room)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 7. VISIT SLOTS
-- The exact slots the seeded appointments sit in. Inserted BEFORE the
-- recurring generation below so their ids are fixed and every dated
-- record (consultation, prescription, invoice) can line up with them.
-- days_ago < 0 means a future clinic.
-- =====================================================================
INSERT INTO slots (slot_id, schedule_id, doctor_id, starts_at, ends_at, status)
SELECT v.slot_id::uuid, v.schedule_id::uuid, v.doctor_id::uuid,
       (CURRENT_DATE - v.days_ago::int + v.at::time)::timestamptz,
       (CURRENT_DATE - v.days_ago::int + v.at::time)::timestamptz + (v.mins || ' minutes')::interval,
       'available'
FROM (VALUES
  -- ---- completed visits (the clinical history) ----------------------
  ('5a000000-0000-4000-8000-000000000001', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001',  6, '09:00', 20),
  ('5a000000-0000-4000-8000-000000000002', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', 13, '09:20', 20),
  ('5a000000-0000-4000-8000-000000000003', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', 20, '09:40', 20),
  ('5a000000-0000-4000-8000-000000000004', '5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002',  5, '08:30', 20),
  ('5a000000-0000-4000-8000-000000000005', '5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002', 12, '08:50', 20),
  ('5a000000-0000-4000-8000-000000000006', '5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002', 19, '09:10', 20),
  ('5a000000-0000-4000-8000-000000000007', '5c000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000003',  7, '08:00', 15),
  ('5a000000-0000-4000-8000-000000000008', '5c000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000003', 14, '08:15', 15),
  ('5a000000-0000-4000-8000-000000000009', '5c000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000004',  9, '09:00', 15),
  ('5a000000-0000-4000-8000-00000000000a', '5c000000-0000-4000-8000-00000000000a', 'f2000000-0000-4000-8000-000000000005', 10, '08:00', 20),
  ('5a000000-0000-4000-8000-00000000000b', '5c000000-0000-4000-8000-00000000000c', 'f2000000-0000-4000-8000-000000000006',  3, '10:00', 30),
  ('5a000000-0000-4000-8000-00000000000c', '5c000000-0000-4000-8000-00000000000f', 'f2000000-0000-4000-8000-000000000007', 16, '09:00', 15),
  ('5a000000-0000-4000-8000-00000000000d', '5c000000-0000-4000-8000-000000000015', 'f2000000-0000-4000-8000-000000000008', 11, '08:30', 20),
  ('5a000000-0000-4000-8000-00000000000e', '5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002', 17, '09:30', 20),
  ('5a000000-0000-4000-8000-00000000000f', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', 22, '10:00', 20),
  -- ---- seen earlier today (their prescriptions are on the counter) ---
  ('5a000000-0000-4000-8000-000000000011', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001',  0, '08:20', 20),
  ('5a000000-0000-4000-8000-000000000012', '5c000000-0000-4000-8000-00000000000f', 'f2000000-0000-4000-8000-000000000007',  0, '08:30', 15),
  -- ---- in the queue right now ---------------------------------------
  ('5a000000-0000-4000-8000-000000000021', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001',  0, '09:00', 20),
  ('5a000000-0000-4000-8000-000000000022', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001',  0, '09:20', 20),
  ('5a000000-0000-4000-8000-000000000023', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001',  0, '09:40', 20),
  ('5a000000-0000-4000-8000-000000000024', '5c000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000003',  0, '08:00', 15),
  ('5a000000-0000-4000-8000-000000000025', '5c000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000003',  0, '08:15', 15),
  ('5a000000-0000-4000-8000-000000000026', '5c000000-0000-4000-8000-00000000000f', 'f2000000-0000-4000-8000-000000000007',  0, '09:00', 15),
  ('5a000000-0000-4000-8000-000000000027', '5c000000-0000-4000-8000-00000000000f', 'f2000000-0000-4000-8000-000000000007',  0, '09:15', 15),
  ('5a000000-0000-4000-8000-000000000028', '5c000000-0000-4000-8000-00000000000a', 'f2000000-0000-4000-8000-000000000005',  0, '08:00', 20),
  -- ---- booked, still to come ----------------------------------------
  ('5a000000-0000-4000-8000-000000000031', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', -3, '09:00', 20),
  ('5a000000-0000-4000-8000-000000000032', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', -3, '09:20', 20),
  ('5a000000-0000-4000-8000-000000000033', '5c000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', -5, '09:00', 20),
  ('5a000000-0000-4000-8000-000000000034', '5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002', -4, '08:30', 20),
  ('5a000000-0000-4000-8000-000000000035', '5c000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000003', -6, '08:00', 15),
  ('5a000000-0000-4000-8000-000000000036', '5c000000-0000-4000-8000-00000000000a', 'f2000000-0000-4000-8000-000000000005', -2, '08:00', 20),
  ('5a000000-0000-4000-8000-000000000037', '5c000000-0000-4000-8000-00000000000c', 'f2000000-0000-4000-8000-000000000006', -7, '10:00', 30),
  ('5a000000-0000-4000-8000-000000000038', '5c000000-0000-4000-8000-000000000015', 'f2000000-0000-4000-8000-000000000008', -9, '08:30', 20),
  ('5a000000-0000-4000-8000-000000000039', '5c000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000004', -4, '09:00', 15),
  ('5a000000-0000-4000-8000-00000000003a', '5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002', -11, '08:50', 20),
  -- ---- cancellations and no-shows -----------------------------------
  ('5a000000-0000-4000-8000-000000000041', '5c000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002',  9, '09:50', 20),
  ('5a000000-0000-4000-8000-000000000042', '5c000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000004', 15, '09:15', 15),
  ('5a000000-0000-4000-8000-000000000043', '5c000000-0000-4000-8000-000000000015', 'f2000000-0000-4000-8000-000000000008', 18, '08:50', 20),
  ('5a000000-0000-4000-8000-000000000044', '5c000000-0000-4000-8000-00000000000a', 'f2000000-0000-4000-8000-000000000005', 21, '08:20', 20)
) AS v(slot_id, schedule_id, doctor_id, days_ago, at, mins)
ON CONFLICT (doctor_id, starts_at) DO NOTHING;

-- =====================================================================
-- 8. RECURRING SLOT MATERIALISATION
-- Four weeks back and four weeks forward. Mirrors SlotGenerator's
-- arithmetic; UNIQUE (doctor_id, starts_at) makes it re-runnable and
-- leaves the fixed visit slots above untouched.
-- =====================================================================
INSERT INTO slots (schedule_id, doctor_id, starts_at, ends_at, status)
SELECT sch.schedule_id,
       sch.doctor_id,
       (d.day + sch.start_time)::timestamptz + (n.n * make_interval(mins => sch.slot_minutes)),
       (d.day + sch.start_time)::timestamptz + ((n.n + 1) * make_interval(mins => sch.slot_minutes)),
       'available'
FROM schedules sch
CROSS JOIN LATERAL generate_series(CURRENT_DATE - 28, CURRENT_DATE + 27, INTERVAL '1 day') AS d(day)
CROSS JOIN LATERAL generate_series(0,
       (EXTRACT(EPOCH FROM (sch.end_time - sch.start_time)) / 60 / sch.slot_minutes)::int - 1) AS n(n)
WHERE EXTRACT(DOW FROM d.day) = sch.weekday
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 9. APPOINTMENTS (FR-APT-03..08)
-- =====================================================================
INSERT INTO appointments (appointment_id, slot_id, patient_id, department_id, status, booked_at) VALUES
  -- completed visits
  ('a1000000-0000-4000-8000-000000000001', '5a000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 11 + TIME '10:12')::timestamptz),
  ('a1000000-0000-4000-8000-000000000002', '5a000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 20 + TIME '14:31')::timestamptz),
  ('a1000000-0000-4000-8000-000000000003', '5a000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-00000000000b', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 27 + TIME '09:05')::timestamptz),
  ('a1000000-0000-4000-8000-000000000004', '5a000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 9  + TIME '11:47')::timestamptz),
  ('a1000000-0000-4000-8000-000000000005', '5a000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-00000000000a', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 18 + TIME '08:15')::timestamptz),
  ('a1000000-0000-4000-8000-000000000006', '5a000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-00000000000f', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 24 + TIME '16:02')::timestamptz),
  ('a1000000-0000-4000-8000-000000000007', '5a000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000002', 'completed', (CURRENT_DATE - 8  + TIME '19:40')::timestamptz),
  ('a1000000-0000-4000-8000-000000000008', '5a000000-0000-4000-8000-000000000008', 'aa000000-0000-4000-8000-000000000011', 'd1000000-0000-4000-8000-000000000002', 'completed', (CURRENT_DATE - 21 + TIME '10:22')::timestamptz),
  ('a1000000-0000-4000-8000-000000000009', '5a000000-0000-4000-8000-000000000009', 'aa000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000002', 'completed', (CURRENT_DATE - 15 + TIME '13:18')::timestamptz),
  ('a1000000-0000-4000-8000-00000000000a', '5a000000-0000-4000-8000-00000000000a', 'aa000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000003', 'completed', (CURRENT_DATE - 17 + TIME '09:33')::timestamptz),
  ('a1000000-0000-4000-8000-00000000000b', '5a000000-0000-4000-8000-00000000000b', 'aa000000-0000-4000-8000-00000000000e', 'd1000000-0000-4000-8000-000000000004', 'completed', (CURRENT_DATE - 3  + TIME '08:05')::timestamptz),
  ('a1000000-0000-4000-8000-00000000000c', '5a000000-0000-4000-8000-00000000000c', 'aa000000-0000-4000-8000-000000000012', 'd1000000-0000-4000-8000-000000000005', 'completed', (CURRENT_DATE - 16 + TIME '08:40')::timestamptz),
  ('a1000000-0000-4000-8000-00000000000d', '5a000000-0000-4000-8000-00000000000d', 'aa000000-0000-4000-8000-000000000014', 'd1000000-0000-4000-8000-000000000006', 'completed', (CURRENT_DATE - 14 + TIME '15:55')::timestamptz),
  ('a1000000-0000-4000-8000-00000000000e', '5a000000-0000-4000-8000-00000000000e', 'aa000000-0000-4000-8000-00000000000c', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 23 + TIME '12:10')::timestamptz),
  ('a1000000-0000-4000-8000-00000000000f', '5a000000-0000-4000-8000-00000000000f', 'aa000000-0000-4000-8000-00000000000d', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 26 + TIME '17:24')::timestamptz),
  -- seen earlier today
  ('a1000000-0000-4000-8000-000000000011', '5a000000-0000-4000-8000-000000000011', 'aa000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'completed', (CURRENT_DATE - 4  + TIME '10:03')::timestamptz),
  ('a1000000-0000-4000-8000-000000000012', '5a000000-0000-4000-8000-000000000012', 'aa000000-0000-4000-8000-00000000000c', 'd1000000-0000-4000-8000-000000000005', 'completed', (CURRENT_DATE - 2  + TIME '18:47')::timestamptz),
  -- in the queue now
  ('a1000000-0000-4000-8000-000000000021', '5a000000-0000-4000-8000-000000000021', 'aa000000-0000-4000-8000-000000000002', 'd1000000-0000-4000-8000-000000000001', 'checked_in',      (CURRENT_DATE - 5 + TIME '09:12')::timestamptz),
  ('a1000000-0000-4000-8000-000000000022', '5a000000-0000-4000-8000-000000000022', 'aa000000-0000-4000-8000-00000000000a', 'd1000000-0000-4000-8000-000000000001', 'checked_in',      (CURRENT_DATE - 6 + TIME '11:38')::timestamptz),
  ('a1000000-0000-4000-8000-000000000023', '5a000000-0000-4000-8000-000000000023', 'aa000000-0000-4000-8000-000000000010', 'd1000000-0000-4000-8000-000000000001', 'checked_in',      (CURRENT_DATE - 4 + TIME '14:55')::timestamptz),
  ('a1000000-0000-4000-8000-000000000024', '5a000000-0000-4000-8000-000000000024', 'aa000000-0000-4000-8000-000000000007', 'd1000000-0000-4000-8000-000000000002', 'checked_in',      (CURRENT_DATE - 7 + TIME '08:41')::timestamptz),
  ('a1000000-0000-4000-8000-000000000025', '5a000000-0000-4000-8000-000000000025', 'aa000000-0000-4000-8000-000000000011', 'd1000000-0000-4000-8000-000000000002', 'checked_in',      (CURRENT_DATE - 3 + TIME '16:19')::timestamptz),
  ('a1000000-0000-4000-8000-000000000026', '5a000000-0000-4000-8000-000000000026', 'aa000000-0000-4000-8000-000000000012', 'd1000000-0000-4000-8000-000000000005', 'in_consultation', (CURRENT_DATE - 9 + TIME '10:27')::timestamptz),
  ('a1000000-0000-4000-8000-000000000027', '5a000000-0000-4000-8000-000000000027', 'aa000000-0000-4000-8000-00000000000d', 'd1000000-0000-4000-8000-000000000005', 'checked_in',      (CURRENT_DATE - 2 + TIME '13:04')::timestamptz),
  ('a1000000-0000-4000-8000-000000000028', '5a000000-0000-4000-8000-000000000028', 'aa000000-0000-4000-8000-000000000009', 'd1000000-0000-4000-8000-000000000003', 'checked_in',      (CURRENT_DATE - 8 + TIME '09:50')::timestamptz),
  -- booked, still to come
  ('a1000000-0000-4000-8000-000000000031', '5a000000-0000-4000-8000-000000000031', 'aa000000-0000-4000-8000-000000000001', 'd1000000-0000-4000-8000-000000000001', 'booked', now() - interval '2 days'),
  ('a1000000-0000-4000-8000-000000000032', '5a000000-0000-4000-8000-000000000032', 'aa000000-0000-4000-8000-000000000005', 'd1000000-0000-4000-8000-000000000001', 'booked', now() - interval '2 days'),
  ('a1000000-0000-4000-8000-000000000033', '5a000000-0000-4000-8000-000000000033', 'aa000000-0000-4000-8000-00000000000b', 'd1000000-0000-4000-8000-000000000001', 'booked', now() - interval '4 days'),
  ('a1000000-0000-4000-8000-000000000034', '5a000000-0000-4000-8000-000000000034', 'aa000000-0000-4000-8000-000000000003', 'd1000000-0000-4000-8000-000000000001', 'booked', now() - interval '5 days'),
  ('a1000000-0000-4000-8000-000000000035', '5a000000-0000-4000-8000-000000000035', 'aa000000-0000-4000-8000-000000000006', 'd1000000-0000-4000-8000-000000000002', 'booked', now() - interval '1 day'),
  ('a1000000-0000-4000-8000-000000000036', '5a000000-0000-4000-8000-000000000036', 'aa000000-0000-4000-8000-000000000008', 'd1000000-0000-4000-8000-000000000003', 'booked', now() - interval '8 days'),
  ('a1000000-0000-4000-8000-000000000037', '5a000000-0000-4000-8000-000000000037', 'aa000000-0000-4000-8000-00000000000e', 'd1000000-0000-4000-8000-000000000004', 'booked', now() - interval '2 days'),
  ('a1000000-0000-4000-8000-000000000038', '5a000000-0000-4000-8000-000000000038', 'aa000000-0000-4000-8000-000000000014', 'd1000000-0000-4000-8000-000000000006', 'booked', now() - interval '6 days'),
  ('a1000000-0000-4000-8000-000000000039', '5a000000-0000-4000-8000-000000000039', 'aa000000-0000-4000-8000-000000000013', 'd1000000-0000-4000-8000-000000000002', 'booked', now() - interval '3 days'),
  ('a1000000-0000-4000-8000-00000000003a', '5a000000-0000-4000-8000-00000000003a', 'aa000000-0000-4000-8000-00000000000f', 'd1000000-0000-4000-8000-000000000001', 'booked', now() - interval '1 day'),
  -- cancellations and no-shows
  ('a1000000-0000-4000-8000-000000000041', '5a000000-0000-4000-8000-000000000041', 'aa000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000001', 'cancelled', (CURRENT_DATE - 14 + TIME '09:15')::timestamptz),
  ('a1000000-0000-4000-8000-000000000042', '5a000000-0000-4000-8000-000000000042', 'aa000000-0000-4000-8000-000000000013', 'd1000000-0000-4000-8000-000000000002', 'cancelled', (CURRENT_DATE - 19 + TIME '11:02')::timestamptz),
  ('a1000000-0000-4000-8000-000000000043', '5a000000-0000-4000-8000-000000000043', 'aa000000-0000-4000-8000-000000000010', 'd1000000-0000-4000-8000-000000000006', 'no_show',   (CURRENT_DATE - 25 + TIME '15:41')::timestamptz),
  ('a1000000-0000-4000-8000-000000000044', '5a000000-0000-4000-8000-000000000044', 'aa000000-0000-4000-8000-000000000004', 'd1000000-0000-4000-8000-000000000003', 'no_show',   (CURRENT_DATE - 28 + TIME '08:58')::timestamptz)
ON CONFLICT DO NOTHING;

-- A live appointment holds its slot; a cancellation releases it (FR-APT-05).
UPDATE slots SET status = 'booked'
WHERE slot_id IN (SELECT slot_id FROM appointments WHERE status <> 'cancelled');

-- =====================================================================
-- 10. THIS MORNING'S QUEUE (FR-APT-07/08; DD-06 orders by priority, then arrival)
-- =====================================================================
INSERT INTO queue_entries (queue_entry_id, appointment_id, checked_in_at, priority, status) VALUES
  ('91000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000021', now() - interval '52 minutes', 100, 'waiting'),
  ('91000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000022', now() - interval '41 minutes', 100, 'waiting'),
  ('91000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000023', now() - interval '18 minutes', 100, 'waiting'),
  ('91000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000024', now() - interval '46 minutes', 100, 'waiting'),
  ('91000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000025', now() - interval '27 minutes', 100, 'waiting'),
  -- A&E: this one was triaged ahead of the queue (lower number = seen sooner)
  ('91000000-0000-4000-8000-000000000006', 'a1000000-0000-4000-8000-000000000026', now() - interval '35 minutes',  10, 'in_consultation'),
  ('91000000-0000-4000-8000-000000000007', 'a1000000-0000-4000-8000-000000000027', now() - interval '12 minutes', 100, 'waiting'),
  ('91000000-0000-4000-8000-000000000008', 'a1000000-0000-4000-8000-000000000028', now() - interval '31 minutes', 100, 'waiting'),
  -- already seen this morning, marked done
  ('91000000-0000-4000-8000-000000000009', 'a1000000-0000-4000-8000-000000000011', now() - interval '3 hours',    100, 'done'),
  ('91000000-0000-4000-8000-00000000000a', 'a1000000-0000-4000-8000-000000000012', now() - interval '4 hours',     50, 'done')
ON CONFLICT (appointment_id) DO NOTHING;

-- =====================================================================
-- 11. ADMISSIONS (FR-FAC-02..05)
-- Inserted before the ward consultations that reference them. The partial
-- unique index permits only one active admission per bed (FR-FAC-03).
-- =====================================================================
INSERT INTO admissions (admission_id, patient_id, admitting_doctor, bed_id, admitted_at, discharged_at, status)
SELECT v.admission_id::uuid, v.patient_id::uuid, v.doctor_id::uuid, b.bed_id,
       (CURRENT_DATE - v.days_ago::int + v.at::time)::timestamptz,
       CASE WHEN v.out_days IS NULL THEN NULL
            ELSE (CURRENT_DATE - v.out_days::int + TIME '11:00')::timestamptz END,
       CASE WHEN v.out_days IS NULL THEN 'active' ELSE 'discharged' END
FROM (VALUES
  -- post-appendicectomy, Surgical Ward
  ('ac000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-00000000000e', 'f2000000-0000-4000-8000-000000000006', 'SG-1', 'A', 3, '12:30', NULL),
  -- decompensated hypertension, Female Medical Ward
  ('ac000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000001', 'FM-1', 'A', 4, '11:00', NULL),
  -- antenatal admission for observation, Maternity
  ('ac000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000005', 'MT-1', 'B', 1, '09:30', NULL),
  -- child rehydrated and sent home
  ('ac000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000003', 'PD-1', 'A', 7, '10:00', 5)
) AS v(admission_id, patient_id, doctor_id, room_no, bed_label, days_ago, at, out_days)
JOIN rooms r ON r.room_no = v.room_no
JOIN beds  b ON b.room_id = r.room_id AND b.label = v.bed_label
ON CONFLICT DO NOTHING;

INSERT INTO bed_assignments (assignment_id, admission_id, bed_id, from_ts, to_ts)
SELECT ('be000000-0000-4000-8000-0000000000' ||
        lpad((row_number() OVER (ORDER BY a.admitted_at))::text, 2, '0'))::uuid,
       a.admission_id, a.bed_id, a.admitted_at, a.discharged_at
FROM admissions a
ON CONFLICT DO NOTHING;

-- Bed board: occupied where an admission is live, cleaning after the
-- paediatric discharge, one ICU bed out of service (FR-FAC-01).
UPDATE beds SET status = 'occupied'
WHERE bed_id IN (SELECT bed_id FROM admissions WHERE status = 'active');

UPDATE beds SET status = 'cleaning'
WHERE bed_id IN (SELECT bed_id FROM admissions WHERE status = 'discharged')
  AND status = 'available';

UPDATE beds SET status = 'maintenance'
WHERE bed_id = (SELECT b.bed_id FROM beds b JOIN rooms r ON r.room_id = b.room_id
                WHERE r.room_no = 'ICU-1' AND b.label = 'B');

-- =====================================================================
-- 12. CONSULTATIONS (FR-EMR-01..03)
-- Dated from the slot each visit sat in, so the appointment list and the
-- record agree. Signed notes are immutable — the V4 trigger blocks any
-- later UPDATE or DELETE.
-- =====================================================================
INSERT INTO consultations (consultation_id, appointment_id, admission_id, doctor_id, patient_id, complaint, findings, diagnosis, signed_at, created_at) VALUES
  ('c1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', NULL, 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001',
   'Fever, headache and general body pains for three days. Took paracetamol at home with little relief.',
   'Temp 38.6°C, pulse 96/min, BP 118/76. Mild pallor, no jaundice. Chest clear, abdomen soft and non-tender.',
   'Uncomplicated Plasmodium falciparum malaria (RDT positive).',
   (CURRENT_DATE - 6 + TIME '09:00')::timestamptz + interval '35 minutes', (CURRENT_DATE - 6 + TIME '09:00')::timestamptz),

  ('c1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000002', NULL, 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000005',
   'Routine review of hypertension and type 2 diabetes. Reports occasional dizziness on standing.',
   'BP 158/96 seated, repeat 152/92. Fasting blood sugar 8.4 mmol/L. Weight 84 kg. No focal neurological deficit; peripheral pulses intact.',
   'Hypertension, poorly controlled. Type 2 diabetes mellitus on oral agents.',
   (CURRENT_DATE - 13 + TIME '09:20')::timestamptz + interval '30 minutes', (CURRENT_DATE - 13 + TIME '09:20')::timestamptz),

  ('c1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000003', NULL, 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-00000000000b',
   'Progressive pain in both knees, worse on climbing stairs, for about eight months.',
   'Crepitus over both knees with reduced flexion on the right. No effusion, no warmth. BMI 31.',
   'Bilateral osteoarthritis of the knees.',
   (CURRENT_DATE - 20 + TIME '09:40')::timestamptz + interval '25 minutes', (CURRENT_DATE - 20 + TIME '09:40')::timestamptz),

  ('c1000000-0000-4000-8000-000000000004', 'a1000000-0000-4000-8000-000000000004', NULL, 'f2000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000003',
   'Cough productive of yellow sputum for five days, with chest tightness. Non-smoker.',
   'Temp 37.8°C, RR 20/min, SpO2 97% on air. Coarse crepitations at both bases, no wheeze.',
   'Acute bronchitis.',
   (CURRENT_DATE - 5 + TIME '08:30')::timestamptz + interval '25 minutes', (CURRENT_DATE - 5 + TIME '08:30')::timestamptz),

  ('c1000000-0000-4000-8000-000000000005', 'a1000000-0000-4000-8000-000000000005', NULL, 'f2000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-00000000000a',
   'Burning epigastric pain, worse at night and when hungry, for three weeks.',
   'Epigastric tenderness on deep palpation. No guarding, no rebound. No melaena reported.',
   'Peptic ulcer disease — for eradication therapy if H. pylori confirmed.',
   (CURRENT_DATE - 12 + TIME '08:50')::timestamptz + interval '30 minutes', (CURRENT_DATE - 12 + TIME '08:50')::timestamptz),

  ('c1000000-0000-4000-8000-000000000006', 'a1000000-0000-4000-8000-000000000006', NULL, 'f2000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-00000000000f',
   'Burning on passing urine with increased frequency for four days.',
   'Suprapubic tenderness, no loin tenderness, afebrile. Urine dipstick: leucocytes ++, nitrites positive.',
   'Uncomplicated lower urinary tract infection.',
   (CURRENT_DATE - 19 + TIME '09:10')::timestamptz + interval '20 minutes', (CURRENT_DATE - 19 + TIME '09:10')::timestamptz),

  ('c1000000-0000-4000-8000-000000000007', 'a1000000-0000-4000-8000-000000000007', NULL, 'f2000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000006',
   'Mother reports loose stools six times and two episodes of vomiting since yesterday.',
   'Alert but irritable. Mildly dehydrated: skin turgor slightly reduced, mucous membranes moist. Weight 12.4 kg, temp 37.9°C.',
   'Acute gastroenteritis with mild dehydration — admitted for rehydration.',
   (CURRENT_DATE - 7 + TIME '08:00')::timestamptz + interval '20 minutes', (CURRENT_DATE - 7 + TIME '08:00')::timestamptz),

  ('c1000000-0000-4000-8000-000000000008', 'a1000000-0000-4000-8000-000000000008', NULL, 'f2000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000011',
   'Night cough and wheeze, three episodes in the last month; worse after running.',
   'Bilateral expiratory wheeze, no crepitations. SpO2 98%. Good response to salbutamol in clinic.',
   'Mild persistent asthma — commence inhaled reliever, review in four weeks.',
   (CURRENT_DATE - 14 + TIME '08:15')::timestamptz + interval '25 minutes', (CURRENT_DATE - 14 + TIME '08:15')::timestamptz),

  ('c1000000-0000-4000-8000-000000000009', 'a1000000-0000-4000-8000-000000000009', NULL, 'f2000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000007',
   'Sore throat and difficulty swallowing for three days; poor appetite.',
   'Tonsils enlarged and inflamed with exudate. Tender cervical lymph nodes. Temp 38.2°C.',
   'Acute bacterial tonsillitis.',
   (CURRENT_DATE - 9 + TIME '09:00')::timestamptz + interval '20 minutes', (CURRENT_DATE - 9 + TIME '09:00')::timestamptz),

  ('c1000000-0000-4000-8000-00000000000a', 'a1000000-0000-4000-8000-00000000000a', NULL, 'f2000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-000000000008',
   'Routine antenatal review at 28 weeks. No bleeding, good fetal movements.',
   'Fundal height 28 cm, longitudinal lie, cephalic presentation. FHR 142/min. BP 118/74. Urine: no protein.',
   'Normal singleton pregnancy at 28 weeks gestation.',
   (CURRENT_DATE - 10 + TIME '08:00')::timestamptz + interval '25 minutes', (CURRENT_DATE - 10 + TIME '08:00')::timestamptz),

  ('c1000000-0000-4000-8000-00000000000b', 'a1000000-0000-4000-8000-00000000000b', NULL, 'f2000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-00000000000e',
   'Right lower abdominal pain for 14 hours, started around the umbilicus. Vomited twice.',
   'Temp 37.9°C. Tenderness and rebound at McBurney''s point. Rovsing''s sign positive. WBC 14.2.',
   'Acute appendicitis — admitted for appendicectomy.',
   (CURRENT_DATE - 3 + TIME '10:00')::timestamptz + interval '45 minutes', (CURRENT_DATE - 3 + TIME '10:00')::timestamptz),

  ('c1000000-0000-4000-8000-00000000000c', 'a1000000-0000-4000-8000-00000000000c', NULL, 'f2000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000012',
   'Laceration to the left forearm from a workshop accident about an hour ago.',
   '6 cm clean laceration on the left volar forearm. No tendon or neurovascular involvement; distal pulses intact.',
   'Laceration of left forearm — cleaned, sutured under local anaesthetic, tetanus prophylaxis given.',
   (CURRENT_DATE - 16 + TIME '09:00')::timestamptz + interval '55 minutes', (CURRENT_DATE - 16 + TIME '09:00')::timestamptz),

  ('c1000000-0000-4000-8000-00000000000d', 'a1000000-0000-4000-8000-00000000000d', NULL, 'f2000000-0000-4000-8000-000000000008', 'aa000000-0000-4000-8000-000000000014',
   'Sudden severe pain and swelling of the right great toe overnight.',
   'First metatarsophalangeal joint hot, swollen and exquisitely tender. Serum urate 520 µmol/L.',
   'Acute gout — first metatarsophalangeal joint.',
   (CURRENT_DATE - 11 + TIME '08:30')::timestamptz + interval '20 minutes', (CURRENT_DATE - 11 + TIME '08:30')::timestamptz),

  ('c1000000-0000-4000-8000-00000000000e', 'a1000000-0000-4000-8000-00000000000e', NULL, 'f2000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-00000000000c',
   'Sore throat and fever for two days ahead of end-of-semester examinations.',
   'Pharynx injected, no exudate. Temp 37.6°C. Chest clear.',
   'Acute viral pharyngitis — symptomatic treatment, no antibiotic indicated.',
   (CURRENT_DATE - 17 + TIME '09:30')::timestamptz + interval '15 minutes', (CURRENT_DATE - 17 + TIME '09:30')::timestamptz),

  ('c1000000-0000-4000-8000-00000000000f', 'a1000000-0000-4000-8000-00000000000f', NULL, 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-00000000000d',
   'Itchy raised rash on both forearms since changing washing soap last week.',
   'Erythematous papular rash over both forearms, sparing the palms. No mucosal involvement.',
   'Allergic contact dermatitis.',
   (CURRENT_DATE - 22 + TIME '10:00')::timestamptz + interval '15 minutes', (CURRENT_DATE - 22 + TIME '10:00')::timestamptz),

  -- ---- seen earlier this morning; their prescriptions are on the counter
  ('c1000000-0000-4000-8000-000000000011', 'a1000000-0000-4000-8000-000000000011', NULL, 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000004',
   'Fever and sore throat for two days, with painful swallowing.',
   'Temp 38.1°C. Tonsils inflamed with follicular exudate. Tender anterior cervical nodes. Chest clear.',
   'Acute bacterial tonsillitis.',
   (CURRENT_DATE + TIME '08:20')::timestamptz + interval '30 minutes', (CURRENT_DATE + TIME '08:20')::timestamptz),

  ('c1000000-0000-4000-8000-000000000012', 'a1000000-0000-4000-8000-000000000012', NULL, 'f2000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-00000000000c',
   'Twisted the right ankle playing football this morning; unable to bear weight fully.',
   'Swelling over the lateral malleolus with tenderness. No bony tenderness at the posterior malleolar edge; Ottawa rules negative. Neurovascularly intact.',
   'Grade I lateral ligament sprain of the right ankle — no radiograph indicated.',
   (CURRENT_DATE + TIME '08:30')::timestamptz + interval '25 minutes', (CURRENT_DATE + TIME '08:30')::timestamptz),

  -- ---- ward round entry against a live admission (no outpatient slot)
  ('c1000000-0000-4000-8000-000000000016', NULL, 'ac000000-0000-4000-8000-000000000002', 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-00000000000b',
   'Admitted from the outpatient clinic with breathlessness on exertion and ankle swelling.',
   'BP 186/104, pulse 92 irregular. Bibasal crepitations, pitting oedema to mid-shin. JVP raised 4 cm.',
   'Hypertensive heart failure — admitted for diuresis and blood pressure control.',
   (CURRENT_DATE - 4 + TIME '11:15')::timestamptz + interval '40 minutes', (CURRENT_DATE - 4 + TIME '11:15')::timestamptz),

  -- ---- open right now: the A&E patient currently with the doctor
  ('c1000000-0000-4000-8000-000000000021', 'a1000000-0000-4000-8000-000000000026', NULL, 'f2000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000012',
   'Attends for wound review and removal of sutures from the forearm laceration.',
   NULL, NULL, NULL, now() - interval '20 minutes')
ON CONFLICT DO NOTHING;

-- FR-EMR-04: corrections after signing are append-only addendums.
INSERT INTO addendums (addendum_id, consultation_id, author_id, body, created_at) VALUES
  ('ad000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001',
   'Malaria RDT confirmed positive after this note was signed. Patient counselled to complete the full three-day course of artemether-lumefantrine and to return if fever persists beyond 48 hours.',
   (CURRENT_DATE - 6 + TIME '11:10')::timestamptz),
  ('ad000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000002', 'f2000000-0000-4000-8000-000000000001',
   'HbA1c returned at 8.9%. Metformin increased to 1 g twice daily and dietitian review requested. Patient informed by telephone.',
   (CURRENT_DATE - 12 + TIME '14:05')::timestamptz),
  ('ad000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000006',
   'Appendicectomy performed the same evening. Findings: acutely inflamed, non-perforated appendix. Recovery uneventful; for discharge review on the next ward round.',
   (CURRENT_DATE - 2 + TIME '08:20')::timestamptz),
  ('ad000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-000000000005', 'f2000000-0000-4000-8000-000000000002',
   'H. pylori stool antigen positive. Triple therapy commenced; urea breath test to be repeated in six weeks.',
   (CURRENT_DATE - 8 + TIME '10:40')::timestamptz),
  ('ad000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-000000000016', 'f2000000-0000-4000-8000-000000000001',
   'Ward round day 3: BP now 142/88, oedema resolving, weight down 2.1 kg. Continue current diuretic dose and plan discharge review tomorrow.',
   (CURRENT_DATE - 1 + TIME '09:15')::timestamptz)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 13. NURSING OBSERVATIONS AND ALLERGIES (FR-EMR-05, FR-PHM-07)
-- =====================================================================
INSERT INTO vitals (vitals_id, patient_id, recorded_by, bp_sys, bp_dia, temp_c, pulse, spo2, weight_kg, recorded_at) VALUES
  ('71000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000011', 118, 76, 38.6,  96, 98, 72.50, (CURRENT_DATE - 6  + TIME '08:50')::timestamptz),
  ('71000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000005', 'f2000000-0000-4000-8000-000000000011', 158, 96, 36.8,  82, 97, 84.00, (CURRENT_DATE - 13 + TIME '09:10')::timestamptz),
  ('71000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000011', 186,104, 36.7,  92, 96, 78.20, (CURRENT_DATE - 4  + TIME '11:05')::timestamptz),
  ('71000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000011', 158, 94, 36.6,  88, 97, 77.10, (CURRENT_DATE - 2  + TIME '06:30')::timestamptz),
  ('71000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000011', 142, 88, 36.5,  80, 98, 76.10, now() - interval '5 hours'),
  ('71000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-000000000006', 'f2000000-0000-4000-8000-000000000013',  95, 60, 37.9, 118, 99, 12.40, (CURRENT_DATE - 7  + TIME '07:50')::timestamptz),
  ('71000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000014', 118, 74, 36.6,  84, 99, 68.90, (CURRENT_DATE - 10 + TIME '07:50')::timestamptz),
  ('71000000-0000-4000-8000-000000000008', 'aa000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000014', 116, 72, 36.5,  88, 99, 69.60, (CURRENT_DATE - 1  + TIME '09:35')::timestamptz),
  ('71000000-0000-4000-8000-000000000009', 'aa000000-0000-4000-8000-00000000000e', 'f2000000-0000-4000-8000-000000000015', 126, 78, 37.9, 104, 98, 81.00, (CURRENT_DATE - 3  + TIME '12:35')::timestamptz),
  ('71000000-0000-4000-8000-00000000000a', 'aa000000-0000-4000-8000-00000000000e', 'f2000000-0000-4000-8000-000000000015', 122, 76, 36.9,  88, 99, 80.60, now() - interval '8 hours'),
  ('71000000-0000-4000-8000-00000000000b', 'aa000000-0000-4000-8000-000000000011', 'f2000000-0000-4000-8000-000000000013', 104, 66, 36.8,  92, 98, 34.70, (CURRENT_DATE - 14 + TIME '08:05')::timestamptz),
  ('71000000-0000-4000-8000-00000000000c', 'aa000000-0000-4000-8000-000000000002', 'f2000000-0000-4000-8000-000000000011', 128, 82, 37.1,  76, 99, 63.40, now() - interval '50 minutes'),
  ('71000000-0000-4000-8000-00000000000d', 'aa000000-0000-4000-8000-000000000010', 'f2000000-0000-4000-8000-000000000012', 148, 90, 36.9,  84, 97, 88.30, now() - interval '16 minutes')
ON CONFLICT DO NOTHING;

INSERT INTO allergies (patient_id, substance, severity) VALUES
  ('aa000000-0000-4000-8000-000000000001', 'Penicillin',        'severe'),
  ('aa000000-0000-4000-8000-000000000005', 'Sulphonamides',     'moderate'),
  ('aa000000-0000-4000-8000-00000000000b', 'Ibuprofen',         'moderate'),
  ('aa000000-0000-4000-8000-000000000008', 'Peanuts',           'moderate'),
  ('aa000000-0000-4000-8000-00000000000f', 'Aspirin',           'mild'),
  ('aa000000-0000-4000-8000-000000000011', 'House dust mite',   'mild'),
  ('aa000000-0000-4000-8000-00000000000e', 'Iodine (contrast)', 'severe'),
  ('aa000000-0000-4000-8000-000000000004', 'Codeine',           'mild')
ON CONFLICT (patient_id, substance) DO NOTHING;

-- =====================================================================
-- 14. PHARMACY — formulary and stock (FR-PHM-01..06)
-- Two or three batches per line with different expiry dates so FEFO has
-- something to choose between; a few lines sit below their reorder level
-- so the low-stock warning shows on the stock table.
-- =====================================================================
INSERT INTO drugs (drug_id, generic_name, brand_name, form, strength, unit_price, reorder_level, is_controlled) VALUES
  ('d2000000-0000-4000-8000-000000000001', 'Paracetamol',             'Panadol',    'tablet',    '500mg',     0.30, 200, false),
  ('d2000000-0000-4000-8000-000000000002', 'Amoxicillin',             'Amoxil',     'capsule',   '500mg',     1.50, 100, false),
  ('d2000000-0000-4000-8000-000000000003', 'Artemether/Lumefantrine', 'Coartem',    'tablet',    '20/120mg',  3.20,  60, false),
  ('d2000000-0000-4000-8000-000000000004', 'Metformin',               'Glucophage', 'tablet',    '500mg',     0.80, 120, false),
  ('d2000000-0000-4000-8000-000000000005', 'Amlodipine',              'Norvasc',    'tablet',    '5mg',       0.90, 120, false),
  ('d2000000-0000-4000-8000-000000000006', 'Lisinopril',              'Zestril',    'tablet',    '10mg',      1.10, 100, false),
  ('d2000000-0000-4000-8000-000000000007', 'Hydrochlorothiazide',     NULL,         'tablet',    '25mg',      0.55, 100, false),
  ('d2000000-0000-4000-8000-000000000008', 'Ibuprofen',               'Brufen',     'tablet',    '400mg',     0.45, 150, false),
  ('d2000000-0000-4000-8000-000000000009', 'Diclofenac',              'Voltaren',   'tablet',    '50mg',      0.60, 100, false),
  ('d2000000-0000-4000-8000-00000000000a', 'Ciprofloxacin',           'Ciprotab',   'tablet',    '500mg',     2.10,  80, false),
  ('d2000000-0000-4000-8000-00000000000b', 'Metronidazole',           'Flagyl',     'tablet',    '400mg',     0.70, 100, false),
  ('d2000000-0000-4000-8000-00000000000c', 'Azithromycin',            'Zithromax',  'tablet',    '500mg',     4.50,  50, false),
  ('d2000000-0000-4000-8000-00000000000d', 'Omeprazole',              'Losec',      'capsule',   '20mg',      1.20,  80, false),
  ('d2000000-0000-4000-8000-00000000000e', 'Cetirizine',              'Zyrtec',     'tablet',    '10mg',      0.50, 100, false),
  ('d2000000-0000-4000-8000-00000000000f', 'Salbutamol',              'Ventolin',   'inhaler',   '100mcg',   38.00,  15, false),
  ('d2000000-0000-4000-8000-000000000010', 'Oral Rehydration Salts',  NULL,         'sachet',    '20.5g',     2.50, 100, false),
  ('d2000000-0000-4000-8000-000000000011', 'Ferrous Sulphate',        NULL,         'tablet',    '200mg',     0.35, 200, false),
  ('d2000000-0000-4000-8000-000000000012', 'Folic Acid',              NULL,         'tablet',    '5mg',       0.25, 200, false),
  ('d2000000-0000-4000-8000-000000000013', 'Insulin (Soluble)',       'Actrapid',   'vial',      '100IU/ml', 65.00,  10, false),
  ('d2000000-0000-4000-8000-000000000014', 'Allopurinol',             NULL,         'tablet',    '100mg',     0.75,  60, false),
  ('d2000000-0000-4000-8000-000000000015', 'Hydrocortisone',          NULL,         'cream',     '1%',       14.00,  25, false),
  ('d2000000-0000-4000-8000-000000000016', 'Morphine Sulphate',       NULL,         'injection', '10mg/ml',  12.00,  20, true),
  ('d2000000-0000-4000-8000-000000000017', 'Diazepam',                'Valium',     'tablet',    '5mg',       0.90,  40, true)
ON CONFLICT DO NOTHING;

-- qty_on_hand is the CURRENT figure: the dispensing recorded below has
-- already been taken off these numbers.
INSERT INTO stock_batches (batch_id, drug_id, batch_no, expiry_date, qty_on_hand, unit_cost) VALUES
  ('ba000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', 'PCM-2408-A', CURRENT_DATE + 45,  261, 0.10),
  ('ba000000-0000-4000-8000-000000000002', 'd2000000-0000-4000-8000-000000000001', 'PCM-2511-B', CURRENT_DATE + 400, 800, 0.11),
  ('ba000000-0000-4000-8000-000000000003', 'd2000000-0000-4000-8000-000000000002', 'AMX-2409-A', CURRENT_DATE + 120, 268, 0.90),
  ('ba000000-0000-4000-8000-000000000004', 'd2000000-0000-4000-8000-000000000002', 'AMX-2602-B', CURRENT_DATE + 360, 500, 0.85),
  ('ba000000-0000-4000-8000-000000000005', 'd2000000-0000-4000-8000-000000000003', 'ACT-2410-A', CURRENT_DATE + 30,   76, 2.10),
  ('ba000000-0000-4000-8000-000000000006', 'd2000000-0000-4000-8000-000000000003', 'ACT-2606-B', CURRENT_DATE + 480, 240, 2.05),
  ('ba000000-0000-4000-8000-000000000007', 'd2000000-0000-4000-8000-000000000004', 'MET-2501-A', CURRENT_DATE + 200, 340, 0.40),
  ('ba000000-0000-4000-8000-000000000008', 'd2000000-0000-4000-8000-000000000004', 'MET-2609-B', CURRENT_DATE + 540, 600, 0.38),
  ('ba000000-0000-4000-8000-000000000009', 'd2000000-0000-4000-8000-000000000005', 'AML-2503-A', CURRENT_DATE + 150, 270, 0.45),
  ('ba000000-0000-4000-8000-00000000000a', 'd2000000-0000-4000-8000-000000000005', 'AML-2607-B', CURRENT_DATE + 500, 400, 0.44),
  ('ba000000-0000-4000-8000-00000000000b', 'd2000000-0000-4000-8000-000000000006', 'LIS-2505-A', CURRENT_DATE + 260, 270, 0.55),
  ('ba000000-0000-4000-8000-00000000000c', 'd2000000-0000-4000-8000-000000000007', 'HCT-2504-A', CURRENT_DATE + 240, 260, 0.20),
  ('ba000000-0000-4000-8000-00000000000d', 'd2000000-0000-4000-8000-000000000008', 'IBU-2412-A', CURRENT_DATE + 90,  180, 0.20),
  ('ba000000-0000-4000-8000-00000000000e', 'd2000000-0000-4000-8000-000000000008', 'IBU-2606-B', CURRENT_DATE + 460, 400, 0.19),
  ('ba000000-0000-4000-8000-00000000000f', 'd2000000-0000-4000-8000-000000000009', 'DIC-2502-A', CURRENT_DATE + 170, 214, 0.28),
  ('ba000000-0000-4000-8000-000000000010', 'd2000000-0000-4000-8000-00000000000a', 'CIP-2506-A', CURRENT_DATE + 280, 146, 1.10),
  ('ba000000-0000-4000-8000-000000000011', 'd2000000-0000-4000-8000-00000000000b', 'MTZ-2411-A', CURRENT_DATE + 60,  190, 0.30),
  ('ba000000-0000-4000-8000-000000000012', 'd2000000-0000-4000-8000-00000000000b', 'MTZ-2605-B', CURRENT_DATE + 430, 300, 0.29),
  -- below the reorder level of 50
  ('ba000000-0000-4000-8000-000000000013', 'd2000000-0000-4000-8000-00000000000c', 'AZI-2503-A', CURRENT_DATE + 110,  32, 2.40),
  ('ba000000-0000-4000-8000-000000000014', 'd2000000-0000-4000-8000-00000000000d', 'OME-2507-A', CURRENT_DATE + 300, 246, 0.60),
  ('ba000000-0000-4000-8000-000000000015', 'd2000000-0000-4000-8000-00000000000e', 'CET-2508-A', CURRENT_DATE + 320, 280, 0.22),
  -- inhalers below the reorder level of 15
  ('ba000000-0000-4000-8000-000000000016', 'd2000000-0000-4000-8000-00000000000f', 'SAL-2509-A', CURRENT_DATE + 220,   9, 22.00),
  ('ba000000-0000-4000-8000-000000000017', 'd2000000-0000-4000-8000-000000000010', 'ORS-2510-A', CURRENT_DATE + 350, 186, 1.20),
  ('ba000000-0000-4000-8000-000000000018', 'd2000000-0000-4000-8000-000000000011', 'FES-2512-A', CURRENT_DATE + 380, 420, 0.15),
  ('ba000000-0000-4000-8000-000000000019', 'd2000000-0000-4000-8000-000000000012', 'FOL-2512-B', CURRENT_DATE + 380, 500, 0.10),
  -- cold chain, below the reorder level of 10
  ('ba000000-0000-4000-8000-00000000001a', 'd2000000-0000-4000-8000-000000000013', 'INS-2601-A', CURRENT_DATE + 75,    6, 42.00),
  ('ba000000-0000-4000-8000-00000000001b', 'd2000000-0000-4000-8000-000000000014', 'ALL-2604-A', CURRENT_DATE + 400, 152, 0.40),
  ('ba000000-0000-4000-8000-00000000001c', 'd2000000-0000-4000-8000-000000000015', 'HYD-2603-A', CURRENT_DATE + 290,  39, 8.00),
  -- controlled drugs (FR-PHM-08)
  ('ba000000-0000-4000-8000-00000000001d', 'd2000000-0000-4000-8000-000000000016', 'MOR-2602-A', CURRENT_DATE + 330,  48, 7.00),
  ('ba000000-0000-4000-8000-00000000001e', 'd2000000-0000-4000-8000-000000000017', 'DZP-2605-A', CURRENT_DATE + 410,  90, 0.45)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 15. PRESCRIPTIONS
-- Fully dispensed, part-dispensed and untouched, so the pharmacy worklist
-- ('open' and 'partially_dispensed') has real work waiting on it.
-- =====================================================================
INSERT INTO prescriptions (prescription_id, consultation_id, doctor_id, patient_id, status, created_at) VALUES
  ('a2000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'dispensed',           (CURRENT_DATE - 6  + TIME '09:45')::timestamptz),
  ('a2000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000002', 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000005', 'dispensed',           (CURRENT_DATE - 13 + TIME '09:55')::timestamptz),
  ('a2000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000004', 'f2000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000003', 'dispensed',           (CURRENT_DATE - 5  + TIME '08:58')::timestamptz),
  ('a2000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-000000000007', 'f2000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000006', 'dispensed',           (CURRENT_DATE - 7  + TIME '08:25')::timestamptz),
  ('a2000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-00000000000e', 'partially_dispensed', (CURRENT_DATE - 3  + TIME '10:50')::timestamptz),
  ('a2000000-0000-4000-8000-000000000006', 'c1000000-0000-4000-8000-000000000011', 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000004', 'open',                (CURRENT_DATE      + TIME '08:52')::timestamptz),
  ('a2000000-0000-4000-8000-000000000007', 'c1000000-0000-4000-8000-000000000012', 'f2000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-00000000000c', 'open',                (CURRENT_DATE      + TIME '08:58')::timestamptz),
  ('a2000000-0000-4000-8000-000000000008', 'c1000000-0000-4000-8000-000000000008', 'f2000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000011', 'dispensed',           (CURRENT_DATE - 14 + TIME '08:42')::timestamptz),
  ('a2000000-0000-4000-8000-000000000009', 'c1000000-0000-4000-8000-00000000000a', 'f2000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-000000000008', 'dispensed',           (CURRENT_DATE - 10 + TIME '08:28')::timestamptz),
  ('a2000000-0000-4000-8000-00000000000a', 'c1000000-0000-4000-8000-00000000000d', 'f2000000-0000-4000-8000-000000000008', 'aa000000-0000-4000-8000-000000000014', 'open',                (CURRENT_DATE - 11 + TIME '08:52')::timestamptz),
  ('a2000000-0000-4000-8000-00000000000b', 'c1000000-0000-4000-8000-000000000016', 'f2000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-00000000000b', 'partially_dispensed', (CURRENT_DATE - 4  + TIME '11:58')::timestamptz)
ON CONFLICT DO NOTHING;

INSERT INTO prescription_items (rx_item_id, prescription_id, drug_id, dose, frequency, duration_days, quantity) VALUES
  -- Kwame Owusu — malaria (penicillin allergy on file, so no amoxicillin)
  ('b2000000-0000-4000-8000-000000000001', 'a2000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000003', '4 tablets', 'twice daily',       3, 24),
  ('b2000000-0000-4000-8000-000000000002', 'a2000000-0000-4000-8000-000000000001', 'd2000000-0000-4000-8000-000000000001', '1g',        'three times daily', 5, 15),
  -- Kofi Mensah — hypertension and diabetes
  ('b2000000-0000-4000-8000-000000000003', 'a2000000-0000-4000-8000-000000000002', 'd2000000-0000-4000-8000-000000000004', '1g',        'twice daily',      30, 60),
  ('b2000000-0000-4000-8000-000000000004', 'a2000000-0000-4000-8000-000000000002', 'd2000000-0000-4000-8000-000000000005', '5mg',       'once daily',       30, 30),
  ('b2000000-0000-4000-8000-000000000005', 'a2000000-0000-4000-8000-000000000002', 'd2000000-0000-4000-8000-000000000006', '10mg',      'once daily',       30, 30),
  -- Yaw Darko — bronchitis
  ('b2000000-0000-4000-8000-000000000006', 'a2000000-0000-4000-8000-000000000003', 'd2000000-0000-4000-8000-000000000002', '500mg',     'three times daily', 7, 21),
  ('b2000000-0000-4000-8000-000000000007', 'a2000000-0000-4000-8000-000000000003', 'd2000000-0000-4000-8000-000000000001', '1g',        'three times daily', 5, 15),
  -- Abena Nyarko (child) — gastroenteritis
  ('b2000000-0000-4000-8000-000000000008', 'a2000000-0000-4000-8000-000000000004', 'd2000000-0000-4000-8000-000000000010', '1 sachet',  'after each loose stool', 3, 10),
  ('b2000000-0000-4000-8000-000000000009', 'a2000000-0000-4000-8000-000000000004', 'd2000000-0000-4000-8000-000000000001', '250mg',     'three times daily', 3,  9),
  -- Solomon Adjei — post-appendicectomy; the ward still owes the diclofenac
  ('b2000000-0000-4000-8000-00000000000a', 'a2000000-0000-4000-8000-000000000005', 'd2000000-0000-4000-8000-00000000000b', '400mg',     'three times daily', 5, 15),
  ('b2000000-0000-4000-8000-00000000000b', 'a2000000-0000-4000-8000-000000000005', 'd2000000-0000-4000-8000-000000000009', '50mg',      'twice daily',       5, 10),
  -- Adwoa Asantewaa — tonsillitis, written this morning
  ('b2000000-0000-4000-8000-00000000000c', 'a2000000-0000-4000-8000-000000000006', 'd2000000-0000-4000-8000-000000000002', '500mg',     'three times daily', 7, 21),
  ('b2000000-0000-4000-8000-00000000000d', 'a2000000-0000-4000-8000-000000000006', 'd2000000-0000-4000-8000-000000000001', '1g',        'three times daily', 5, 15),
  -- Emmanuel Tetteh — ankle sprain, written this morning
  ('b2000000-0000-4000-8000-00000000000e', 'a2000000-0000-4000-8000-000000000007', 'd2000000-0000-4000-8000-000000000009', '50mg',      'twice daily',       5, 10),
  ('b2000000-0000-4000-8000-00000000000f', 'a2000000-0000-4000-8000-000000000007', 'd2000000-0000-4000-8000-000000000001', '1g',        'three times daily', 4, 12),
  -- Patience Amoako — asthma
  ('b2000000-0000-4000-8000-000000000010', 'a2000000-0000-4000-8000-000000000008', 'd2000000-0000-4000-8000-00000000000f', '2 puffs',   'as required',      30,  1),
  -- Esi Baidoo — routine antenatal supplements
  ('b2000000-0000-4000-8000-000000000011', 'a2000000-0000-4000-8000-000000000009', 'd2000000-0000-4000-8000-000000000011', '200mg',     'once daily',       30, 30),
  ('b2000000-0000-4000-8000-000000000012', 'a2000000-0000-4000-8000-000000000009', 'd2000000-0000-4000-8000-000000000012', '5mg',       'once daily',       30, 30),
  -- Joseph Boateng — gout, never collected
  ('b2000000-0000-4000-8000-000000000013', 'a2000000-0000-4000-8000-00000000000a', 'd2000000-0000-4000-8000-000000000009', '50mg',      'twice daily',       5, 10),
  ('b2000000-0000-4000-8000-000000000014', 'a2000000-0000-4000-8000-00000000000a', 'd2000000-0000-4000-8000-000000000014', '100mg',     'once daily',       30, 30),
  -- Comfort Ankrah — ward prescription, diuretic still to come up
  ('b2000000-0000-4000-8000-000000000015', 'a2000000-0000-4000-8000-00000000000b', 'd2000000-0000-4000-8000-000000000006', '10mg',      'once daily',       30, 30),
  ('b2000000-0000-4000-8000-000000000016', 'a2000000-0000-4000-8000-00000000000b', 'd2000000-0000-4000-8000-000000000007', '25mg',      'once daily',       30, 30)
ON CONFLICT DO NOTHING;

-- Dispensing history. Batches were drawn earliest-expiry-first (FR-PHM-02),
-- which is why the *-A batches above carry the reduced quantities.
INSERT INTO dispenses (dispense_id, rx_item_id, batch_id, qty, dispensed_by, dispensed_at) VALUES
  ('df000000-0000-4000-8000-000000000001', 'b2000000-0000-4000-8000-000000000001', 'ba000000-0000-4000-8000-000000000005', 24, 'f2000000-0000-4000-8000-000000000021', (CURRENT_DATE - 6  + TIME '10:05')::timestamptz),
  ('df000000-0000-4000-8000-000000000002', 'b2000000-0000-4000-8000-000000000002', 'ba000000-0000-4000-8000-000000000001', 15, 'f2000000-0000-4000-8000-000000000021', (CURRENT_DATE - 6  + TIME '10:05')::timestamptz),
  ('df000000-0000-4000-8000-000000000003', 'b2000000-0000-4000-8000-000000000003', 'ba000000-0000-4000-8000-000000000007', 60, 'f2000000-0000-4000-8000-000000000021', (CURRENT_DATE - 13 + TIME '10:20')::timestamptz),
  ('df000000-0000-4000-8000-000000000004', 'b2000000-0000-4000-8000-000000000004', 'ba000000-0000-4000-8000-000000000009', 30, 'f2000000-0000-4000-8000-000000000021', (CURRENT_DATE - 13 + TIME '10:20')::timestamptz),
  ('df000000-0000-4000-8000-000000000005', 'b2000000-0000-4000-8000-000000000005', 'ba000000-0000-4000-8000-00000000000b', 30, 'f2000000-0000-4000-8000-000000000021', (CURRENT_DATE - 13 + TIME '10:20')::timestamptz),
  ('df000000-0000-4000-8000-000000000006', 'b2000000-0000-4000-8000-000000000006', 'ba000000-0000-4000-8000-000000000003', 21, 'f2000000-0000-4000-8000-000000000022', (CURRENT_DATE - 5  + TIME '09:20')::timestamptz),
  ('df000000-0000-4000-8000-000000000007', 'b2000000-0000-4000-8000-000000000007', 'ba000000-0000-4000-8000-000000000001', 15, 'f2000000-0000-4000-8000-000000000022', (CURRENT_DATE - 5  + TIME '09:20')::timestamptz),
  ('df000000-0000-4000-8000-000000000008', 'b2000000-0000-4000-8000-000000000008', 'ba000000-0000-4000-8000-000000000017', 10, 'f2000000-0000-4000-8000-000000000022', (CURRENT_DATE - 7  + TIME '08:45')::timestamptz),
  ('df000000-0000-4000-8000-000000000009', 'b2000000-0000-4000-8000-000000000009', 'ba000000-0000-4000-8000-000000000001',  9, 'f2000000-0000-4000-8000-000000000022', (CURRENT_DATE - 7  + TIME '08:45')::timestamptz),
  ('df000000-0000-4000-8000-00000000000a', 'b2000000-0000-4000-8000-00000000000a', 'ba000000-0000-4000-8000-000000000011', 15, 'f2000000-0000-4000-8000-000000000021', (CURRENT_DATE - 3  + TIME '13:10')::timestamptz),
  ('df000000-0000-4000-8000-00000000000b', 'b2000000-0000-4000-8000-000000000010', 'ba000000-0000-4000-8000-000000000016',  1, 'f2000000-0000-4000-8000-000000000022', (CURRENT_DATE - 14 + TIME '09:05')::timestamptz),
  ('df000000-0000-4000-8000-00000000000c', 'b2000000-0000-4000-8000-000000000011', 'ba000000-0000-4000-8000-000000000018', 30, 'f2000000-0000-4000-8000-000000000022', (CURRENT_DATE - 10 + TIME '08:55')::timestamptz),
  ('df000000-0000-4000-8000-00000000000d', 'b2000000-0000-4000-8000-000000000012', 'ba000000-0000-4000-8000-000000000019', 30, 'f2000000-0000-4000-8000-000000000022', (CURRENT_DATE - 10 + TIME '08:55')::timestamptz),
  ('df000000-0000-4000-8000-00000000000e', 'b2000000-0000-4000-8000-000000000015', 'ba000000-0000-4000-8000-00000000000b', 30, 'f2000000-0000-4000-8000-000000000021', (CURRENT_DATE - 4  + TIME '12:30')::timestamptz)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 16. LABORATORY (FR-LAB-01..05)
-- Results become visible to the patient only once released (AC-04).
-- =====================================================================
INSERT INTO lab_tests (lab_test_id, name, specimen, price, tat_hours) VALUES
  ('11000000-0000-4000-8000-000000000001', 'Full Blood Count',                 'blood', 45,  4),
  ('11000000-0000-4000-8000-000000000002', 'Malaria RDT',                      'blood', 20,  1),
  ('11000000-0000-4000-8000-000000000003', 'Blood Film for Malaria Parasites', 'blood', 30,  3),
  ('11000000-0000-4000-8000-000000000004', 'Fasting Blood Sugar',              'blood', 25,  2),
  ('11000000-0000-4000-8000-000000000005', 'HbA1c',                            'blood', 90, 24),
  ('11000000-0000-4000-8000-000000000006', 'Urinalysis',                       'urine', 30,  2),
  ('11000000-0000-4000-8000-000000000007', 'Liver Function Test',              'blood',120, 24),
  ('11000000-0000-4000-8000-000000000008', 'Renal Function Test',              'blood',110, 24),
  ('11000000-0000-4000-8000-000000000009', 'Lipid Profile',                    'blood',130, 24),
  ('11000000-0000-4000-8000-00000000000a', 'Widal Test',                       'blood', 40,  6),
  ('11000000-0000-4000-8000-00000000000b', 'HIV Screening',                    'blood', 35,  2),
  ('11000000-0000-4000-8000-00000000000c', 'Hepatitis B Surface Antigen',      'blood', 45,  4),
  ('11000000-0000-4000-8000-00000000000d', 'Blood Grouping & Rhesus',          'blood', 30,  2),
  ('11000000-0000-4000-8000-00000000000e', 'Stool Routine Examination',        'stool', 35,  4),
  ('11000000-0000-4000-8000-00000000000f', 'Serum Uric Acid',                  'blood', 55,  6)
ON CONFLICT (name) DO NOTHING;

INSERT INTO lab_orders (lab_order_id, consultation_id, patient_id, ordered_by, status, created_at) VALUES
  ('10000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'f2000000-0000-4000-8000-000000000001', 'released',         (CURRENT_DATE - 6  + TIME '09:20')::timestamptz),
  ('10000000-0000-4000-8000-000000000002', 'c1000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000005', 'f2000000-0000-4000-8000-000000000001', 'released',         (CURRENT_DATE - 13 + TIME '09:40')::timestamptz),
  ('10000000-0000-4000-8000-000000000003', 'c1000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-00000000000f', 'f2000000-0000-4000-8000-000000000002', 'released',         (CURRENT_DATE - 19 + TIME '09:25')::timestamptz),
  ('10000000-0000-4000-8000-000000000004', 'c1000000-0000-4000-8000-00000000000b', 'aa000000-0000-4000-8000-00000000000e', 'f2000000-0000-4000-8000-000000000006', 'result_entered',   (CURRENT_DATE - 3  + TIME '10:30')::timestamptz),
  ('10000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-00000000000d', 'aa000000-0000-4000-8000-000000000014', 'f2000000-0000-4000-8000-000000000008', 'in_progress',      (CURRENT_DATE - 11 + TIME '08:45')::timestamptz),
  ('10000000-0000-4000-8000-000000000006', 'c1000000-0000-4000-8000-000000000016', 'aa000000-0000-4000-8000-00000000000b', 'f2000000-0000-4000-8000-000000000001', 'sample_collected', (CURRENT_DATE - 1  + TIME '07:20')::timestamptz),
  ('10000000-0000-4000-8000-000000000007', 'c1000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-00000000000a', 'f2000000-0000-4000-8000-000000000002', 'ordered',          (CURRENT_DATE - 12 + TIME '09:15')::timestamptz)
ON CONFLICT DO NOTHING;

INSERT INTO lab_order_items (order_item_id, lab_order_id, lab_test_id, result_value, ref_range, entered_by, released_at) VALUES
  ('12000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000002', 'Positive (P. falciparum)',              'Negative',                 'f2000000-0000-4000-8000-000000000023', (CURRENT_DATE - 6  + TIME '10:50')::timestamptz),
  ('12000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000001', 'Hb 11.8 g/dL; WBC 6.4; Platelets 168',  'Hb 13.0-17.0 g/dL',        'f2000000-0000-4000-8000-000000000023', (CURRENT_DATE - 6  + TIME '13:20')::timestamptz),
  ('12000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000005', '8.9 %',                                 '< 7.0 % (target)',         'f2000000-0000-4000-8000-000000000024', (CURRENT_DATE - 12 + TIME '11:40')::timestamptz),
  ('12000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000008', 'Creatinine 96 µmol/L; Urea 5.1 mmol/L', 'Creatinine 62-106 µmol/L', 'f2000000-0000-4000-8000-000000000024', (CURRENT_DATE - 12 + TIME '11:40')::timestamptz),
  ('12000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000006', 'Leucocytes ++; Nitrites positive',      'Negative',                 'f2000000-0000-4000-8000-000000000023', (CURRENT_DATE - 19 + TIME '11:30')::timestamptz),
  -- entered but deliberately NOT released: the patient must not see it yet (AC-04)
  ('12000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000004', '11000000-0000-4000-8000-000000000001', 'Hb 13.1 g/dL; WBC 14.2; Platelets 240', 'WBC 4.0-11.0 x10^9/L',     'f2000000-0000-4000-8000-000000000023', NULL),
  ('12000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000005', '11000000-0000-4000-8000-00000000000f', NULL,                                    '200-430 µmol/L',           NULL, NULL),
  ('12000000-0000-4000-8000-000000000008', '10000000-0000-4000-8000-000000000006', '11000000-0000-4000-8000-000000000008', NULL,                                    'Creatinine 62-106 µmol/L', NULL, NULL),
  ('12000000-0000-4000-8000-000000000009', '10000000-0000-4000-8000-000000000006', '11000000-0000-4000-8000-000000000001', NULL,                                    'Hb 11.0-14.7 g/dL',        NULL, NULL),
  ('12000000-0000-4000-8000-00000000000a', '10000000-0000-4000-8000-000000000007', '11000000-0000-4000-8000-00000000000e', NULL,                                    'No ova or parasites seen', NULL, NULL)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 17. BILLING (FR-BIL-01..07, DD-05)
-- visit_ref follows the hospital's OPD / IPD / ANC / A&E numbering.
-- Line items are append-only and totals are always computed from them,
-- never stored.
-- =====================================================================
INSERT INTO invoices (invoice_id, patient_id, visit_ref, status, void_reason, issued_at, created_at) VALUES
  ('1e000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'OPD-2026-04417', 'paid',           NULL, (CURRENT_DATE - 6  + TIME '10:15')::timestamptz, (CURRENT_DATE - 6  + TIME '10:15')::timestamptz),
  ('1e000000-0000-4000-8000-000000000002', 'aa000000-0000-4000-8000-000000000005', 'OPD-2026-04310', 'partially_paid', NULL, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz),
  ('1e000000-0000-4000-8000-000000000003', 'aa000000-0000-4000-8000-000000000003', 'OPD-2026-04455', 'paid',           NULL, (CURRENT_DATE - 5  + TIME '09:30')::timestamptz, (CURRENT_DATE - 5  + TIME '09:30')::timestamptz),
  ('1e000000-0000-4000-8000-000000000004', 'aa000000-0000-4000-8000-000000000006', 'PED-2026-01188', 'paid',           NULL, (CURRENT_DATE - 7  + TIME '09:10')::timestamptz, (CURRENT_DATE - 7  + TIME '09:10')::timestamptz),
  ('1e000000-0000-4000-8000-000000000005', 'aa000000-0000-4000-8000-00000000000e', 'IPD-2026-00218', 'issued',         NULL, (CURRENT_DATE - 3  + TIME '11:30')::timestamptz, (CURRENT_DATE - 3  + TIME '11:30')::timestamptz),
  ('1e000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-00000000000b', 'IPD-2026-00214', 'issued',         NULL, (CURRENT_DATE - 4  + TIME '12:15')::timestamptz, (CURRENT_DATE - 4  + TIME '12:15')::timestamptz),
  ('1e000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000008', 'ANC-2026-00981', 'paid',           NULL, (CURRENT_DATE - 10 + TIME '09:05')::timestamptz, (CURRENT_DATE - 10 + TIME '09:05')::timestamptz),
  ('1e000000-0000-4000-8000-000000000008', 'aa000000-0000-4000-8000-000000000004', 'OPD-2026-04502', 'draft',          NULL, NULL,                                            (CURRENT_DATE      + TIME '09:05')::timestamptz),
  ('1e000000-0000-4000-8000-000000000009', 'aa000000-0000-4000-8000-000000000012', 'A&E-2026-01677', 'paid',           NULL, (CURRENT_DATE - 16 + TIME '10:20')::timestamptz, (CURRENT_DATE - 16 + TIME '10:20')::timestamptz),
  ('1e000000-0000-4000-8000-00000000000a', 'aa000000-0000-4000-8000-000000000014', 'OPD-2026-04266', 'issued',         NULL, (CURRENT_DATE - 11 + TIME '09:15')::timestamptz, (CURRENT_DATE - 11 + TIME '09:15')::timestamptz),
  -- FR-BIL-07: voiding needs a reason and is recorded, never deleted.
  ('1e000000-0000-4000-8000-00000000000b', 'aa000000-0000-4000-8000-000000000001', 'OPD-2026-04418', 'void',
   'Raised in error — duplicate of OPD-2026-04417 for the same visit.', (CURRENT_DATE - 6 + TIME '10:20')::timestamptz, (CURRENT_DATE - 6 + TIME '10:20')::timestamptz),
  -- Kwame's outstanding bill: this is the one the patient portal can pay online.
  ('1e000000-0000-4000-8000-00000000000c', 'aa000000-0000-4000-8000-000000000001', 'OPD-2026-04531', 'issued',         NULL, (CURRENT_DATE - 2  + TIME '10:00')::timestamptz, (CURRENT_DATE - 2  + TIME '10:00')::timestamptz),
  ('1e000000-0000-4000-8000-00000000000d', 'aa000000-0000-4000-8000-00000000000f', 'OPD-2026-04102', 'paid',           NULL, (CURRENT_DATE - 19 + TIME '09:45')::timestamptz, (CURRENT_DATE - 19 + TIME '09:45')::timestamptz),
  ('1e000000-0000-4000-8000-00000000000e', 'aa000000-0000-4000-8000-00000000000a', 'OPD-2026-04188', 'paid',           NULL, (CURRENT_DATE - 12 + TIME '09:35')::timestamptz, (CURRENT_DATE - 12 + TIME '09:35')::timestamptz),
  ('1e000000-0000-4000-8000-00000000000f', 'aa000000-0000-4000-8000-000000000011', 'PED-2026-01204', 'paid',           NULL, (CURRENT_DATE - 14 + TIME '09:10')::timestamptz, (CURRENT_DATE - 14 + TIME '09:10')::timestamptz),
  ('1e000000-0000-4000-8000-000000000010', 'aa000000-0000-4000-8000-000000000007', 'PED-2026-01196', 'paid',           NULL, (CURRENT_DATE - 9  + TIME '09:30')::timestamptz, (CURRENT_DATE - 9  + TIME '09:30')::timestamptz),
  ('1e000000-0000-4000-8000-000000000011', 'aa000000-0000-4000-8000-00000000000c', 'OPD-2026-04231', 'paid',           NULL, (CURRENT_DATE - 17 + TIME '09:55')::timestamptz, (CURRENT_DATE - 17 + TIME '09:55')::timestamptz),
  ('1e000000-0000-4000-8000-000000000012', 'aa000000-0000-4000-8000-00000000000d', 'OPD-2026-04077', 'paid',           NULL, (CURRENT_DATE - 22 + TIME '10:20')::timestamptz, (CURRENT_DATE - 22 + TIME '10:20')::timestamptz),
  ('1e000000-0000-4000-8000-000000000013', 'aa000000-0000-4000-8000-00000000000c', 'A&E-2026-01702', 'issued',         NULL, (CURRENT_DATE      + TIME '09:10')::timestamptz, (CURRENT_DATE      + TIME '09:10')::timestamptz)
ON CONFLICT DO NOTHING;

INSERT INTO invoice_items (item_id, invoice_id, source_type, source_id, description, amount, posted_at) VALUES
  -- Kwame Owusu — malaria visit
  ('1f000000-0000-4000-8000-000000000001', '1e000000-0000-4000-8000-000000000001', 'consultation', 'c1000000-0000-4000-8000-000000000001', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 6 + TIME '10:15')::timestamptz),
  ('1f000000-0000-4000-8000-000000000002', '1e000000-0000-4000-8000-000000000001', 'pharmacy',     'a2000000-0000-4000-8000-000000000001', 'Artemether/Lumefantrine 20/120mg x24',        76.80, (CURRENT_DATE - 6 + TIME '10:15')::timestamptz),
  ('1f000000-0000-4000-8000-000000000003', '1e000000-0000-4000-8000-000000000001', 'pharmacy',     'a2000000-0000-4000-8000-000000000001', 'Paracetamol 500mg x15',                        4.50, (CURRENT_DATE - 6 + TIME '10:15')::timestamptz),
  ('1f000000-0000-4000-8000-000000000004', '1e000000-0000-4000-8000-000000000001', 'laboratory',   '10000000-0000-4000-8000-000000000001', 'Malaria RDT',                                 20.00, (CURRENT_DATE - 6 + TIME '10:15')::timestamptz),
  ('1f000000-0000-4000-8000-000000000005', '1e000000-0000-4000-8000-000000000001', 'laboratory',   '10000000-0000-4000-8000-000000000001', 'Full Blood Count',                            45.00, (CURRENT_DATE - 6 + TIME '10:15')::timestamptz),
  -- Kofi Mensah — chronic disease review
  ('1f000000-0000-4000-8000-000000000011', '1e000000-0000-4000-8000-000000000002', 'consultation', 'c1000000-0000-4000-8000-000000000002', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000012', '1e000000-0000-4000-8000-000000000002', 'pharmacy',     'a2000000-0000-4000-8000-000000000002', 'Metformin 500mg x60',                         48.00, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000013', '1e000000-0000-4000-8000-000000000002', 'pharmacy',     'a2000000-0000-4000-8000-000000000002', 'Amlodipine 5mg x30',                          27.00, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000014', '1e000000-0000-4000-8000-000000000002', 'pharmacy',     'a2000000-0000-4000-8000-000000000002', 'Lisinopril 10mg x30',                         33.00, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000015', '1e000000-0000-4000-8000-000000000002', 'laboratory',   '10000000-0000-4000-8000-000000000002', 'HbA1c',                                       90.00, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000016', '1e000000-0000-4000-8000-000000000002', 'laboratory',   '10000000-0000-4000-8000-000000000002', 'Renal Function Test',                        110.00, (CURRENT_DATE - 13 + TIME '10:30')::timestamptz),
  -- Yaw Darko — bronchitis
  ('1f000000-0000-4000-8000-000000000021', '1e000000-0000-4000-8000-000000000003', 'consultation', 'c1000000-0000-4000-8000-000000000004', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 5 + TIME '09:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000022', '1e000000-0000-4000-8000-000000000003', 'pharmacy',     'a2000000-0000-4000-8000-000000000003', 'Amoxicillin 500mg x21',                       31.50, (CURRENT_DATE - 5 + TIME '09:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000023', '1e000000-0000-4000-8000-000000000003', 'pharmacy',     'a2000000-0000-4000-8000-000000000003', 'Paracetamol 500mg x15',                        4.50, (CURRENT_DATE - 5 + TIME '09:30')::timestamptz),
  -- Abena Nyarko — paediatric visit
  ('1f000000-0000-4000-8000-000000000031', '1e000000-0000-4000-8000-000000000004', 'consultation', 'c1000000-0000-4000-8000-000000000007', 'Consultation — Pediatrics',                   70.00, (CURRENT_DATE - 7 + TIME '09:10')::timestamptz),
  ('1f000000-0000-4000-8000-000000000032', '1e000000-0000-4000-8000-000000000004', 'pharmacy',     'a2000000-0000-4000-8000-000000000004', 'Oral Rehydration Salts x10',                  25.00, (CURRENT_DATE - 7 + TIME '09:10')::timestamptz),
  ('1f000000-0000-4000-8000-000000000033', '1e000000-0000-4000-8000-000000000004', 'pharmacy',     'a2000000-0000-4000-8000-000000000004', 'Paracetamol 500mg x9',                         2.70, (CURRENT_DATE - 7 + TIME '09:10')::timestamptz),
  -- Solomon Adjei — inpatient, three bed-days so far (FR-FAC-05)
  ('1f000000-0000-4000-8000-000000000041', '1e000000-0000-4000-8000-000000000005', 'consultation', 'c1000000-0000-4000-8000-00000000000b', 'Consultation — General Surgery',             150.00, (CURRENT_DATE - 3 + TIME '11:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000042', '1e000000-0000-4000-8000-000000000005', 'other',        NULL,                                   'Appendicectomy — theatre and surgeon fee',  1850.00, (CURRENT_DATE - 3 + TIME '18:40')::timestamptz),
  ('1f000000-0000-4000-8000-000000000043', '1e000000-0000-4000-8000-000000000005', 'bed_day',      'ac000000-0000-4000-8000-000000000001', 'Surgical Ward — bed day 1',                  180.00, (CURRENT_DATE - 3 + TIME '23:00')::timestamptz),
  ('1f000000-0000-4000-8000-000000000044', '1e000000-0000-4000-8000-000000000005', 'bed_day',      'ac000000-0000-4000-8000-000000000001', 'Surgical Ward — bed day 2',                  180.00, (CURRENT_DATE - 2 + TIME '23:00')::timestamptz),
  ('1f000000-0000-4000-8000-000000000045', '1e000000-0000-4000-8000-000000000005', 'bed_day',      'ac000000-0000-4000-8000-000000000001', 'Surgical Ward — bed day 3',                  180.00, (CURRENT_DATE - 1 + TIME '23:00')::timestamptz),
  ('1f000000-0000-4000-8000-000000000046', '1e000000-0000-4000-8000-000000000005', 'pharmacy',     'a2000000-0000-4000-8000-000000000005', 'Metronidazole 400mg x15',                     10.50, (CURRENT_DATE - 3 + TIME '13:15')::timestamptz),
  ('1f000000-0000-4000-8000-000000000047', '1e000000-0000-4000-8000-000000000005', 'laboratory',   '10000000-0000-4000-8000-000000000004', 'Full Blood Count',                            45.00, (CURRENT_DATE - 3 + TIME '11:30')::timestamptz),
  -- Comfort Ankrah — medical admission
  ('1f000000-0000-4000-8000-000000000051', '1e000000-0000-4000-8000-000000000006', 'consultation', 'c1000000-0000-4000-8000-000000000016', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 4 + TIME '12:15')::timestamptz),
  ('1f000000-0000-4000-8000-000000000052', '1e000000-0000-4000-8000-000000000006', 'bed_day',      'ac000000-0000-4000-8000-000000000002', 'Female Medical Ward — bed day 1',            120.00, (CURRENT_DATE - 4 + TIME '23:00')::timestamptz),
  ('1f000000-0000-4000-8000-000000000053', '1e000000-0000-4000-8000-000000000006', 'bed_day',      'ac000000-0000-4000-8000-000000000002', 'Female Medical Ward — bed day 2',            120.00, (CURRENT_DATE - 3 + TIME '23:00')::timestamptz),
  ('1f000000-0000-4000-8000-000000000054', '1e000000-0000-4000-8000-000000000006', 'bed_day',      'ac000000-0000-4000-8000-000000000002', 'Female Medical Ward — bed day 3',            120.00, (CURRENT_DATE - 2 + TIME '23:00')::timestamptz),
  ('1f000000-0000-4000-8000-000000000055', '1e000000-0000-4000-8000-000000000006', 'bed_day',      'ac000000-0000-4000-8000-000000000002', 'Female Medical Ward — bed day 4',            120.00, (CURRENT_DATE - 1 + TIME '23:00')::timestamptz),
  ('1f000000-0000-4000-8000-000000000056', '1e000000-0000-4000-8000-000000000006', 'pharmacy',     'a2000000-0000-4000-8000-00000000000b', 'Lisinopril 10mg x30',                         33.00, (CURRENT_DATE - 4 + TIME '12:35')::timestamptz),
  -- Esi Baidoo — antenatal
  ('1f000000-0000-4000-8000-000000000061', '1e000000-0000-4000-8000-000000000007', 'consultation', 'c1000000-0000-4000-8000-00000000000a', 'Consultation — Obstetrics & Gynaecology',    120.00, (CURRENT_DATE - 10 + TIME '09:05')::timestamptz),
  ('1f000000-0000-4000-8000-000000000062', '1e000000-0000-4000-8000-000000000007', 'pharmacy',     'a2000000-0000-4000-8000-000000000009', 'Ferrous Sulphate 200mg x30',                  10.50, (CURRENT_DATE - 10 + TIME '09:05')::timestamptz),
  ('1f000000-0000-4000-8000-000000000063', '1e000000-0000-4000-8000-000000000007', 'pharmacy',     'a2000000-0000-4000-8000-000000000009', 'Folic Acid 5mg x30',                           7.50, (CURRENT_DATE - 10 + TIME '09:05')::timestamptz),
  -- Adwoa Asantewaa — this morning's visit, still a draft on the clerk's desk
  ('1f000000-0000-4000-8000-000000000071', '1e000000-0000-4000-8000-000000000008', 'consultation', 'c1000000-0000-4000-8000-000000000011', 'Consultation — General Medicine',              80.00, (CURRENT_DATE + TIME '09:05')::timestamptz),
  -- Musah Alhassan — A&E attendance
  ('1f000000-0000-4000-8000-000000000081', '1e000000-0000-4000-8000-000000000009', 'consultation', 'c1000000-0000-4000-8000-00000000000c', 'Consultation — Accident & Emergency',         60.00, (CURRENT_DATE - 16 + TIME '10:20')::timestamptz),
  ('1f000000-0000-4000-8000-000000000082', '1e000000-0000-4000-8000-000000000009', 'other',        NULL,                                   'Wound toilet and suturing',                  220.00, (CURRENT_DATE - 16 + TIME '10:20')::timestamptz),
  ('1f000000-0000-4000-8000-000000000083', '1e000000-0000-4000-8000-000000000009', 'other',        NULL,                                   'Tetanus toxoid',                              35.00, (CURRENT_DATE - 16 + TIME '10:20')::timestamptz),
  -- Joseph Boateng — gout, awaiting payment
  ('1f000000-0000-4000-8000-000000000091', '1e000000-0000-4000-8000-00000000000a', 'consultation', 'c1000000-0000-4000-8000-00000000000d', 'Consultation — Orthopaedics',                130.00, (CURRENT_DATE - 11 + TIME '09:15')::timestamptz),
  ('1f000000-0000-4000-8000-000000000092', '1e000000-0000-4000-8000-00000000000a', 'laboratory',   '10000000-0000-4000-8000-000000000005', 'Serum Uric Acid',                             55.00, (CURRENT_DATE - 11 + TIME '09:15')::timestamptz),
  -- the voided duplicate keeps its line so the trail stays readable
  ('1f000000-0000-4000-8000-0000000000a1', '1e000000-0000-4000-8000-00000000000b', 'other',        NULL,                                   'Consultation — General Medicine (duplicate)', 80.00, (CURRENT_DATE - 6 + TIME '10:20')::timestamptz),
  -- Kwame Owusu — outstanding review bill (payable from the portal)
  ('1f000000-0000-4000-8000-0000000000b1', '1e000000-0000-4000-8000-00000000000c', 'other',        NULL,                                   'Review consultation — General Medicine',      80.00, (CURRENT_DATE - 2 + TIME '10:00')::timestamptz),
  ('1f000000-0000-4000-8000-0000000000b2', '1e000000-0000-4000-8000-00000000000c', 'laboratory',   NULL,                                   'Full Blood Count (repeat)',                   45.00, (CURRENT_DATE - 2 + TIME '10:00')::timestamptz),
  -- the remaining settled outpatient visits
  ('1f000000-0000-4000-8000-0000000000c1', '1e000000-0000-4000-8000-00000000000d', 'consultation', 'c1000000-0000-4000-8000-000000000006', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 19 + TIME '09:45')::timestamptz),
  ('1f000000-0000-4000-8000-0000000000c2', '1e000000-0000-4000-8000-00000000000d', 'laboratory',   '10000000-0000-4000-8000-000000000003', 'Urinalysis',                                  30.00, (CURRENT_DATE - 19 + TIME '09:45')::timestamptz),
  ('1f000000-0000-4000-8000-0000000000d1', '1e000000-0000-4000-8000-00000000000e', 'consultation', 'c1000000-0000-4000-8000-000000000005', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 12 + TIME '09:35')::timestamptz),
  ('1f000000-0000-4000-8000-0000000000d2', '1e000000-0000-4000-8000-00000000000e', 'laboratory',   '10000000-0000-4000-8000-000000000007', 'Stool Routine Examination',                   35.00, (CURRENT_DATE - 12 + TIME '09:35')::timestamptz),
  ('1f000000-0000-4000-8000-0000000000e1', '1e000000-0000-4000-8000-00000000000f', 'consultation', 'c1000000-0000-4000-8000-000000000008', 'Consultation — Pediatrics',                   70.00, (CURRENT_DATE - 14 + TIME '09:10')::timestamptz),
  ('1f000000-0000-4000-8000-0000000000e2', '1e000000-0000-4000-8000-00000000000f', 'pharmacy',     'a2000000-0000-4000-8000-000000000008', 'Salbutamol inhaler 100mcg x1',                38.00, (CURRENT_DATE - 14 + TIME '09:10')::timestamptz),
  ('1f000000-0000-4000-8000-0000000000f1', '1e000000-0000-4000-8000-000000000010', 'consultation', 'c1000000-0000-4000-8000-000000000009', 'Consultation — Pediatrics',                   70.00, (CURRENT_DATE - 9  + TIME '09:30')::timestamptz),
  ('1f000000-0000-4000-8000-000000000101', '1e000000-0000-4000-8000-000000000011', 'consultation', 'c1000000-0000-4000-8000-00000000000e', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 17 + TIME '09:55')::timestamptz),
  ('1f000000-0000-4000-8000-000000000111', '1e000000-0000-4000-8000-000000000012', 'consultation', 'c1000000-0000-4000-8000-00000000000f', 'Consultation — General Medicine',              80.00, (CURRENT_DATE - 22 + TIME '10:20')::timestamptz),
  ('1f000000-0000-4000-8000-000000000112', '1e000000-0000-4000-8000-000000000012', 'other',        NULL,                                   'Hydrocortisone cream 1%',                     14.00, (CURRENT_DATE - 22 + TIME '10:20')::timestamptz),
  -- Emmanuel Tetteh — this morning's A&E attendance
  ('1f000000-0000-4000-8000-000000000121', '1e000000-0000-4000-8000-000000000013', 'consultation', 'c1000000-0000-4000-8000-000000000012', 'Consultation — Accident & Emergency',         60.00, (CURRENT_DATE + TIME '09:10')::timestamptz),
  ('1f000000-0000-4000-8000-000000000122', '1e000000-0000-4000-8000-000000000013', 'other',        NULL,                                   'Ankle support and strapping',                 45.00, (CURRENT_DATE + TIME '09:10')::timestamptz)
ON CONFLICT DO NOTHING;

-- Payments. ITC rows carry the gateway reference that server-side
-- verification matched on (NFR-SEC-06); cash and POS are cash-office entries.
INSERT INTO payments (payment_id, invoice_id, method, amount, gateway_ref, status, paid_at) VALUES
  ('9a000000-0000-4000-8000-000000000001', '1e000000-0000-4000-8000-000000000001', 'itc',  226.30, 'ITC-TRX-4417-88213', 'paid',    (CURRENT_DATE - 6  + TIME '10:52')::timestamptz),
  ('9a000000-0000-4000-8000-000000000002', '1e000000-0000-4000-8000-000000000002', 'cash', 200.00, NULL,                 'paid',    (CURRENT_DATE - 13 + TIME '11:05')::timestamptz),
  ('9a000000-0000-4000-8000-000000000003', '1e000000-0000-4000-8000-000000000003', 'pos',  116.00, NULL,                 'paid',    (CURRENT_DATE - 5  + TIME '10:02')::timestamptz),
  ('9a000000-0000-4000-8000-000000000004', '1e000000-0000-4000-8000-000000000004', 'itc',   97.70, 'ITC-TRX-1188-45907', 'paid',    (CURRENT_DATE - 7  + TIME '09:41')::timestamptz),
  ('9a000000-0000-4000-8000-000000000005', '1e000000-0000-4000-8000-000000000007', 'itc',  138.00, 'ITC-TRX-0981-33120', 'paid',    (CURRENT_DATE - 10 + TIME '09:38')::timestamptz),
  ('9a000000-0000-4000-8000-000000000006', '1e000000-0000-4000-8000-000000000009', 'cash', 315.00, NULL,                 'paid',    (CURRENT_DATE - 16 + TIME '11:26')::timestamptz),
  ('9a000000-0000-4000-8000-000000000007', '1e000000-0000-4000-8000-00000000000d', 'cash', 110.00, NULL,                 'paid',    (CURRENT_DATE - 19 + TIME '10:15')::timestamptz),
  ('9a000000-0000-4000-8000-000000000008', '1e000000-0000-4000-8000-00000000000e', 'cash', 115.00, NULL,                 'paid',    (CURRENT_DATE - 12 + TIME '10:04')::timestamptz),
  ('9a000000-0000-4000-8000-000000000009', '1e000000-0000-4000-8000-00000000000f', 'itc',  108.00, 'ITC-TRX-1204-77410', 'paid',    (CURRENT_DATE - 14 + TIME '09:47')::timestamptz),
  ('9a000000-0000-4000-8000-00000000000a', '1e000000-0000-4000-8000-000000000010', 'cash',  70.00, NULL,                 'paid',    (CURRENT_DATE - 9  + TIME '10:11')::timestamptz),
  ('9a000000-0000-4000-8000-00000000000b', '1e000000-0000-4000-8000-000000000011', 'pos',   80.00, NULL,                 'paid',    (CURRENT_DATE - 17 + TIME '10:22')::timestamptz),
  ('9a000000-0000-4000-8000-00000000000c', '1e000000-0000-4000-8000-000000000012', 'cash',  94.00, NULL,                 'paid',    (CURRENT_DATE - 22 + TIME '10:49')::timestamptz),
  -- a checkout that was started and never completed: nothing is credited
  ('9a000000-0000-4000-8000-00000000000d', '1e000000-0000-4000-8000-00000000000a', 'itc',  185.00, 'ITC-TRX-4266-51882', 'pending', NULL),
  -- a declined card attempt, kept for the record
  ('9a000000-0000-4000-8000-00000000000e', '1e000000-0000-4000-8000-000000000005', 'itc',  500.00, 'ITC-TRX-0218-90344', 'failed',  NULL)
ON CONFLICT DO NOTHING;

-- Inpatient invoices are linked back to their admission (FR-BIL-04).
UPDATE admissions SET invoice_id = '1e000000-0000-4000-8000-000000000005' WHERE admission_id = 'ac000000-0000-4000-8000-000000000001';
UPDATE admissions SET invoice_id = '1e000000-0000-4000-8000-000000000006' WHERE admission_id = 'ac000000-0000-4000-8000-000000000002';

-- =====================================================================
-- 18. NOTIFICATION OUTBOX (DD-08, FR-APT-06)
-- What the worker has already drained. Rows are 'skipped' when no SMTP is
-- configured — mail never blocks a business flow.
-- =====================================================================
INSERT INTO notification_outbox (notification_id, template, ref_id, recipient, subject, body_text, status, attempts, created_at, sent_at) VALUES
  ('80000000-0000-4000-8000-000000000001', 'booking_confirmation', 'a1000000-0000-4000-8000-000000000031', 'patient@medicore.test',
   'Your MediCore appointment is confirmed',
   'Dear Kwame Owusu, your appointment with Dr. Abena Mensah (General Medicine) is confirmed. Please arrive 15 minutes early with your MediCore card.',
   'sent', 1, now() - interval '2 days', now() - interval '2 days' + interval '20 seconds'),
  ('80000000-0000-4000-8000-000000000002', 'booking_confirmation', 'a1000000-0000-4000-8000-000000000032', 'k.mensah@patients.medicore.test',
   'Your MediCore appointment is confirmed',
   'Dear Kofi Mensah, your review appointment with Dr. Abena Mensah (General Medicine) is confirmed.',
   'sent', 1, now() - interval '2 days', now() - interval '2 days' + interval '18 seconds'),
  ('80000000-0000-4000-8000-000000000003', 'payment_receipt', '9a000000-0000-4000-8000-000000000001', 'patient@medicore.test',
   'Receipt for invoice OPD-2026-04417',
   'We have received GHS 226.30 for invoice OPD-2026-04417. Thank you.',
   'sent', 1, (CURRENT_DATE - 6 + TIME '10:53')::timestamptz, (CURRENT_DATE - 6 + TIME '10:53')::timestamptz + interval '40 seconds'),
  ('80000000-0000-4000-8000-000000000004', 'cancellation', 'a1000000-0000-4000-8000-000000000041', 'reception@medicore.test',
   'Appointment cancelled',
   'The appointment for Adwoa Asantewaa has been cancelled and the slot released back to the clinic.',
   'skipped', 0, (CURRENT_DATE - 14 + TIME '09:20')::timestamptz, NULL),
  ('80000000-0000-4000-8000-000000000005', 'reminder', 'a1000000-0000-4000-8000-000000000036', 'e.baidoo@patients.medicore.test',
   'Reminder: antenatal appointment',
   'Dear Esi Baidoo, this is a reminder of your antenatal appointment with Dr. Akosua Frimpong.',
   'pending', 0, now() - interval '2 hours', NULL)
ON CONFLICT (template, ref_id) DO NOTHING;

-- =====================================================================
-- 19. AUDIT TRAIL (NFR-SEC-04 — append-only; V4 blocks UPDATE and DELETE)
-- A plausible recent history, including a denial: deny-by-default is
-- audited exactly like an allow.
-- =====================================================================
INSERT INTO audit_log (user_id, patient_id, action, entity_ref, meta, occurred_at) VALUES
  ('f1000000-0000-4000-8000-000000000031', NULL,                                   'auth.login',        NULL,                                                 '{"result":"allowed"}',                                     now() - interval '3 hours'),
  ('f1000000-0000-4000-8000-000000000031', 'aa000000-0000-4000-8000-000000000002', 'appointment.book',  'appointments:a1000000-0000-4000-8000-000000000021',  '{"channel":"front_desk"}',                                 (CURRENT_DATE - 5 + TIME '09:12')::timestamptz),
  ('f1000000-0000-4000-8000-000000000031', 'aa000000-0000-4000-8000-000000000002', 'queue.checkin',     'appointments:a1000000-0000-4000-8000-000000000021',  '{"priority":100}',                                         now() - interval '52 minutes'),
  ('f1000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'emr.read',          'patients:aa000000-0000-4000-8000-000000000001',      '{"scope":"RELATIONSHIP"}',                                 (CURRENT_DATE - 6 + TIME '09:02')::timestamptz),
  ('f1000000-0000-4000-8000-000000000001', 'aa000000-0000-4000-8000-000000000001', 'consultation.sign', 'consultations:c1000000-0000-4000-8000-000000000001', '{"locked":true}',                                          (CURRENT_DATE - 6 + TIME '09:35')::timestamptz),
  ('f1000000-0000-4000-8000-000000000021', 'aa000000-0000-4000-8000-000000000001', 'pharmacy.dispense', 'prescriptions:a2000000-0000-4000-8000-000000000001', '{"strategy":"FEFO","batches":["ACT-2410-A","PCM-2408-A"]}', (CURRENT_DATE - 6 + TIME '10:05')::timestamptz),
  ('f1000000-0000-4000-8000-000000000041', 'aa000000-0000-4000-8000-000000000001', 'invoice.issue',     'invoices:1e000000-0000-4000-8000-000000000001',      '{"total":226.30}',                                         (CURRENT_DATE - 6 + TIME '10:15')::timestamptz),
  ('f1000000-0000-4000-8000-000000000071', 'aa000000-0000-4000-8000-000000000001', 'payment.record',    'payments:9a000000-0000-4000-8000-000000000001',      '{"method":"itc","verified":true}',                         (CURRENT_DATE - 6 + TIME '10:52')::timestamptz),
  ('f1000000-0000-4000-8000-000000000051', 'aa000000-0000-4000-8000-000000000001', 'invoice.void',      'invoices:1e000000-0000-4000-8000-00000000000b',      '{"reason":"duplicate"}',                                   (CURRENT_DATE - 6 + TIME '11:30')::timestamptz),
  ('f1000000-0000-4000-8000-000000000072', 'aa000000-0000-4000-8000-000000000005', 'emr.read',          'patients:aa000000-0000-4000-8000-000000000005',      '{"result":"denied","reason":"scope OWN"}',                 now() - interval '2 days'),
  ('f1000000-0000-4000-8000-000000000006', 'aa000000-0000-4000-8000-00000000000e', 'admission.manage',  'admissions:ac000000-0000-4000-8000-000000000001',    '{"action":"admit","ward":"Surgical Ward"}',                (CURRENT_DATE - 3 + TIME '12:30')::timestamptz),
  ('f1000000-0000-4000-8000-000000000023', 'aa000000-0000-4000-8000-000000000001', 'lab.process',       'lab_orders:10000000-0000-4000-8000-000000000001',    '{"status":"released"}',                                    (CURRENT_DATE - 6 + TIME '13:20')::timestamptz),
  ('f1000000-0000-4000-8000-000000000007', 'aa000000-0000-4000-8000-000000000012', 'consultation.open', 'consultations:c1000000-0000-4000-8000-000000000021', '{"source":"queue"}',                                       now() - interval '20 minutes');
