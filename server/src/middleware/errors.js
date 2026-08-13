class HttpError extends Error {
  constructor(status, message, details) { super(message); this.status = status; this.details = details; }
}
const notFound = (req, res) => res.status(404).json({ error: 'Not found' });
// Central error handler: no stack traces leak to clients (NFR-SEC-02).
const errorHandler = (err, req, res, _next) => {
  const status = err.status || 500;
  if (status >= 500) console.error(err);
  res.status(status).json({ error: err.message || 'Internal error', details: err.details });
};
module.exports = { HttpError, notFound, errorHandler };
