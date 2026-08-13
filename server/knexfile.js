require('dotenv').config();
module.exports = {
  client: 'pg',
  connection: process.env.DATABASE_URL,
  migrations: { directory: './migrations' },
  pool: { min: 1, max: 10 },
};
