/** Express app assembly — layering per Design Doc §2: routes → auth middleware → policy → services. */
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const session = require('express-session');
const PgSession = require('connect-pg-simple')(session);
const rateLimit = require('express-rate-limit');
const { Pool } = require('pg');
const config = require('./config');
const { notFound, errorHandler } = require('./middleware/errors');

const app = express();
app.set('trust proxy', 1);
app.use(helmet());                                       // NFR-SEC-01 headers
app.use(cors({ origin: config.corsOrigin, credentials: true }));
app.use(express.json({ limit: '256kb' }));

// DD-02: server-side sessions in httpOnly cookies, stored in Postgres.
app.use(session({
  store: new PgSession({
    pool: new Pool({ connectionString: config.databaseUrl }),
    createTableIfMissing: true,
  }),
  secret: config.sessionSecret,
  resave: false,
  saveUninitialized: false,
  rolling: true,                                          // FR-AUTH-08: inactivity-based expiry
  cookie: {
    httpOnly: true,
    secure: config.isProd,
    sameSite: 'lax',
    maxAge: config.sessionTtlMs,
  },
}));

// NFR-SEC-07: rate limiting on auth endpoints.
app.use('/api/auth', rateLimit({ windowMs: 15 * 60 * 1000, limit: 50, standardHeaders: true }));

app.get('/api/health', (req, res) => res.json({ ok: true, service: 'medicore-api' }));
app.use('/api/auth', require('./routes/auth'));
app.use('/api', require('./routes/directory'));
app.use('/api', require('./routes/clinical_compat'));
app.use('/api/schedules', require('./routes/schedules'));
app.use('/api/appointments', require('./routes/appointments'));

app.use(notFound);
app.use(errorHandler);
module.exports = app;
