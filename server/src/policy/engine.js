/**
 * Policy Engine (DD-03, AC-06): the ONLY authorisation path in the system.
 * decide() is a pure function over the matrix + injected resolvers, so the
 * permission model is unit-testable without HTTP or a database.
 */
const { MATRIX, AUDITED_ACTIONS } = require('./matrix');
const resolvers = require('./relationships');
const { audit } = require('../services/audit');
const { HttpError } = require('../middleware/errors');

/**
 * @param {object} args
 * @param {string} args.action   e.g. 'emr.read'
 * @param {object|null} args.user session user { userId, role, staffId?, patientId? } or null
 * @param {object} args.ctx      resource context, e.g. { patientId, scopeNeeded }
 * @param {object} deps          resolver functions (injectable for tests)
 * @returns {Promise<{allow: boolean, reason: string}>}
 */
async function decide({ action, user, ctx = {} }, deps = resolvers) {
  const rule = MATRIX[action];
  if (!rule) return { allow: false, reason: `no rule for '${action}' (deny-by-default)` }; // NFR-SEC-03
  if (rule.public) return { allow: true, reason: 'public' };
  if (!user) return { allow: false, reason: 'unauthenticated' };

  const scope = rule[user.role];
  if (!scope) return { allow: false, reason: `role '${user.role}' denied for '${action}'` };

  switch (scope) {
    case 'any':
      return { allow: true, reason: 'role:any' };
    case 'own': {
      if (!ctx.patientId) return { allow: false, reason: 'own-scope requires patient context' };
      const owns = await deps.patientOwns(user.userId, ctx.patientId);
      return { allow: owns, reason: owns ? 'own' : 'not your record' };
    }
    case 'relationship': { // FR-AUTH-05
      if (!ctx.patientId) return { allow: false, reason: 'relationship-scope requires patient context' };
      const ok = await deps.doctorHasActiveRelationship(user.staffId, ctx.patientId);
      return { allow: ok, reason: ok ? 'active care relationship' : 'no active care relationship' };
    }
    case 'ward': { // AC-03
      if (!ctx.patientId) return { allow: false, reason: 'ward-scope requires patient context' };
      const ok = await deps.nurseWardMatches(user.staffId, ctx.patientId);
      return { allow: ok, reason: ok ? 'assigned ward' : 'patient not in assigned ward' };
    }
    case 'grant': { // FR-FAM-01/02
      if (!ctx.patientId || !ctx.scopeNeeded) return { allow: false, reason: 'grant-scope requires patient + scope context' };
      const ok = await deps.grantCovers(user.userId, ctx.patientId, ctx.scopeNeeded);
      return { allow: ok, reason: ok ? 'valid grant' : 'no valid grant' };
    }
    default:
      return { allow: false, reason: `unknown scope '${scope}'` };
  }
}

/**
 * Express middleware factory.
 * @param {string} action
 * @param {(req) => object} ctxFrom  extracts resource context from the request
 */
function authorize(action, ctxFrom = () => ({})) {
  return async (req, res, next) => {
    try {
      const user = req.session?.user || null;
      const ctx = ctxFrom(req) || {};
      const verdict = await decide({ action, user, ctx });
      if (AUDITED_ACTIONS.has(action)) { // FR-EMR-06: log allowed AND denied clinical access
        await audit({
          userId: user?.userId, patientId: ctx.patientId, action,
          meta: { allow: verdict.allow, reason: verdict.reason, path: req.path },
        });
      }
      if (!verdict.allow) {
        const status = user ? 403 : 401;
        return next(new HttpError(status, 'Not permitted'));
      }
      next();
    } catch (e) { next(e); }
  };
}

module.exports = { decide, authorize };
