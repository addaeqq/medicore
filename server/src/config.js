require('dotenv').config();
module.exports = {
  databaseUrl: process.env.DATABASE_URL,
  sessionSecret: process.env.SESSION_SECRET || 'dev-only-secret',
  port: Number(process.env.PORT || 4000),
  corsOrigin: process.env.CORS_ORIGIN || 'http://localhost:3000',
  isProd: process.env.NODE_ENV === 'production',
  // FR-AUTH-08: default staff inactivity timeout (ms)
  sessionTtlMs: 30 * 60 * 1000,
  // FR-AUTH-06: lockout policy
  lockout: { maxAttempts: 5, windowMs: 15 * 60 * 1000 },
};
