/** Auth routes: FR-AUTH-01/02/06/07-lite, FR-PAT-01. */
const router = require('express').Router();
const bcrypt = require('bcryptjs');
const { z } = require('zod');
const db = require('../db');
const config = require('../config');
const { HttpError } = require('../middleware/errors');
const { audit } = require('../services/audit');

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  fullName: z.string().min(2),
  dob: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  sex: z.enum(['female', 'male', 'other']),
  phone: z.string().optional(),
  address: z.string().optional(),
});

// FR-PAT-01: patient self-registration (public per matrix)
router.post('/register', async (req, res, next) => {
  try {
    const body = registerSchema.parse(req.body);
    const password_hash = await bcrypt.hash(body.password, 12); // FR-AUTH-02
    const result = await db.transaction(async (trx) => {
      const [user] = await trx('users')
        .insert({ email: body.email.toLowerCase(), password_hash, role: 'patient' })
        .returning(['user_id', 'email', 'role']);
      const mrn = 'MRN-' + Date.now().toString(36).toUpperCase();
      const [patient] = await trx('patients').insert({
        user_id: user.user_id, mrn, full_name: body.fullName,
        dob: body.dob, sex: body.sex, phone: body.phone, address: body.address,
      }).returning(['patient_id', 'mrn', 'full_name']);
      return { user, patient };
    });
    await audit({ userId: result.user.user_id, patientId: result.patient.patient_id, action: 'patient.register_self' });
    res.status(201).json(result);
  } catch (e) {
    if (e.code === '23505') return next(new HttpError(409, 'Email already registered'));
    if (e.name === 'ZodError') return next(new HttpError(422, 'Validation failed', e.issues));
    next(e);
  }
});

// FR-AUTH-01/06: login with lockout
router.post('/login', async (req, res, next) => {
  try {
    const { email, password } = z.object({ email: z.string().email(), password: z.string() }).parse(req.body);
    const user = await db('users').where({ email: email.toLowerCase() }).first();
    const fail = async () => {
      if (user) {
        const attempts = user.failed_logins + 1;
        const patch = { failed_logins: attempts };
        if (attempts >= config.lockout.maxAttempts)
          patch.locked_until = new Date(Date.now() + config.lockout.windowMs); // FR-AUTH-06
        await db('users').where({ user_id: user.user_id }).update(patch);
        await audit({ userId: user.user_id, action: 'auth.login_failed', meta: { attempts } });
      }
      throw new HttpError(401, 'Invalid email or password');
    };
    if (!user || !user.is_active) return await fail();
    if (user.locked_until && new Date(user.locked_until) > new Date())
      throw new HttpError(423, 'Account temporarily locked. Try again later.');
    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) return await fail();

    await db('users').where({ user_id: user.user_id }).update({ failed_logins: 0, locked_until: null });
    const staff = await db('staff').where({ user_id: user.user_id }).first();
    const patient = await db('patients').where({ user_id: user.user_id }).first();
    req.session.user = {
      userId: user.user_id, role: user.role,
      staffId: staff?.staff_id || null, patientId: patient?.patient_id || null,
    };
    await audit({ userId: user.user_id, action: 'auth.login' });
    res.json({ user: req.session.user });
  } catch (e) {
    if (e.name === 'ZodError') return next(new HttpError(422, 'Validation failed', e.issues));
    next(e);
  }
});

router.post('/logout', (req, res) => req.session.destroy(() => res.json({ ok: true })));
router.get('/me', (req, res) => res.json({ user: req.session?.user || null }));

module.exports = router;
