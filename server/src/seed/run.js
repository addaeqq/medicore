/** Demo seed: departments, one user per role, a doctor schedule, drugs, tests, a ward. Synthetic data only (NFR-PRV-03). */
const bcrypt = require('bcryptjs');
const db = require('../db');

async function main() {
  const hash = await bcrypt.hash('Password123!', 12);
  const mkUser = async (email, role) => {
    const [u] = await db('users').insert({ email, password_hash: hash, role })
      .onConflict('email').merge().returning('*');
    return u;
  };

  const [gm] = await db('departments').insert({ name: 'General Medicine', dept_type: 'clinical', consult_fee: 80 })
    .onConflict('name').merge().returning('*');
  await db('departments').insert([
    { name: 'Pediatrics', dept_type: 'clinical', consult_fee: 70 },
    { name: 'Laboratory', dept_type: 'diagnostic', consult_fee: 0 },
    { name: 'Pharmacy', dept_type: 'support', consult_fee: 0 },
  ]).onConflict('name').ignore();

  const mkStaff = async (email, role, staff_type, full_name, department_id) => {
    const u = await mkUser(email, role);
    const existing = await db('staff').where({ user_id: u.user_id }).first();
    if (existing) return existing;
    const [s] = await db('staff').insert({ user_id: u.user_id, staff_type, full_name, department_id }).returning('*');
    return s;
  };

  const doctor = await mkStaff('doctor@medicore.test', 'doctor', 'doctor', 'Dr. Abena Mensah', gm.department_id);
  await mkStaff('admin@medicore.test', 'sys_admin', 'sys_admin', 'System Administrator', null);
  await mkStaff('reception@medicore.test', 'receptionist', 'receptionist', 'Front Desk', gm.department_id);
  await mkStaff('pharmacist@medicore.test', 'pharmacist', 'pharmacist', 'Pharm. Kojo Asante', null);
  await mkStaff('billing@medicore.test', 'billing_clerk', 'billing_clerk', 'Cashier One', null);
  await mkStaff('management@medicore.test', 'management', 'management', 'Hospital Manager', null);

  const pu = await mkUser('patient@medicore.test', 'patient');
  const patient = await db('patients').where({ user_id: pu.user_id }).first()
    || (await db('patients').insert({
      user_id: pu.user_id, mrn: 'MRN-DEMO01', full_name: 'Kwame Owusu',
      dob: '1990-05-14', sex: 'male', phone: '+233200000000',
    }).returning('*'))[0];

  const { createScheduleWithSlots } = require('../services/scheduling');
  const hasSchedule = await db('schedules').where({ doctor_id: doctor.staff_id }).first();
  if (!hasSchedule) {
    for (const weekday of [1, 3, 5]) {
      await createScheduleWithSlots({
        doctorId: doctor.staff_id, weekday, startTime: '09:00', endTime: '12:00', slotMinutes: 20, room: 'C1',
      });
    }
  }

  await db('drugs').insert([
    { generic_name: 'Amoxicillin', form: 'capsule', strength: '500mg', unit_price: 1.5, reorder_level: 100 },
    { generic_name: 'Paracetamol', form: 'tablet', strength: '500mg', unit_price: 0.3, reorder_level: 200 },
  ]).onConflict().ignore();
  await db('lab_tests').insert([
    { name: 'Full Blood Count', specimen: 'blood', price: 45, tat_hours: 4 },
    { name: 'Malaria RDT', specimen: 'blood', price: 20, tat_hours: 1 },
  ]).onConflict('name').ignore();

  const [ward] = await db('wards').insert({ name: 'Female Medical Ward', daily_tariff: 120 })
    .onConflict('name').merge().returning('*');
  const [room] = await db('rooms').insert({ ward_id: ward.ward_id, room_no: 'FM-1' })
    .onConflict(['ward_id', 'room_no']).merge().returning('*');
  await db('beds').insert([
    { room_id: room.room_id, label: 'B1' }, { room_id: room.room_id, label: 'B2' },
  ]).onConflict(['room_id', 'label']).ignore();

  console.log('Seed complete. Demo logins (password: Password123!):');
  console.log('  admin@medicore.test / doctor@medicore.test / reception@medicore.test');
  console.log('  pharmacist@medicore.test / billing@medicore.test / management@medicore.test / patient@medicore.test');
  console.log(`  Demo patient: ${patient.mrn}`);
  await db.destroy();
}
main().catch((e) => { console.error(e); process.exit(1); });
