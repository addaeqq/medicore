/**
 * Database-level integrity that carries requirements (Design Doc §5.1):
 *  - FR-EMR-03: consultations are immutable once signed; corrections via addendums only.
 *  - NFR-SEC-04: audit_log is append-only — UPDATE/DELETE rejected beneath the application.
 */
exports.up = async (knex) => {
  await knex.raw(`
    CREATE OR REPLACE FUNCTION reject_signed_consultation_update() RETURNS trigger AS $$
    BEGIN
      IF OLD.signed_at IS NOT NULL THEN
        RAISE EXCEPTION 'Signed consultations are immutable (FR-EMR-03); add an addendum instead'
          USING ERRCODE = 'raise_exception';
      END IF;
      RETURN NEW;
    END; $$ LANGUAGE plpgsql;
  `);
  await knex.raw(`
    CREATE TRIGGER trg_consultation_immutable
    BEFORE UPDATE OR DELETE ON consultations
    FOR EACH ROW EXECUTE FUNCTION reject_signed_consultation_update();
  `);

  await knex.raw(`
    CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
    BEGIN
      RAISE EXCEPTION 'audit_log is append-only (NFR-SEC-04)';
    END; $$ LANGUAGE plpgsql;
  `);
  await knex.raw(`
    CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
  `);
};

exports.down = async (knex) => {
  await knex.raw('DROP TRIGGER IF EXISTS trg_audit_append_only ON audit_log');
  await knex.raw('DROP FUNCTION IF EXISTS reject_audit_mutation');
  await knex.raw('DROP TRIGGER IF EXISTS trg_consultation_immutable ON consultations');
  await knex.raw('DROP FUNCTION IF EXISTS reject_signed_consultation_update');
};
