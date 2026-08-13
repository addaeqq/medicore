/** Admin schedule management (FR-APT-01/02). */
const router = require('express').Router();
const { z } = require('zod');
const { authorize } = require('../policy/engine');
const { createScheduleWithSlots } = require('../services/scheduling');
const { HttpError } = require('../middleware/errors');

const schema = z.object({
  doctorId: z.string().uuid(),
  weekday: z.number().int().min(0).max(6),
  startTime: z.string().regex(/^\d{2}:\d{2}$/),
  endTime: z.string().regex(/^\d{2}:\d{2}$/),
  slotMinutes: z.number().int().min(5).max(120).default(20),
  room: z.string().optional(),
});

router.post('/', authorize('schedule.manage'), async (req, res, next) => {
  try {
    const body = schema.parse(req.body);
    const result = await createScheduleWithSlots(body);
    res.status(201).json(result);
  } catch (e) {
    if (e.name === 'ZodError') return next(new HttpError(422, 'Validation failed', e.issues));
    next(e);
  }
});

module.exports = router;
