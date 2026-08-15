-- Database-level integrity that carries requirements (Design §5.1).
-- FR-EMR-03: consultations immutable once signed; corrections via addendums only.
CREATE OR REPLACE FUNCTION reject_signed_consultation_update() RETURNS trigger AS $$
BEGIN
  IF OLD.signed_at IS NOT NULL THEN
    RAISE EXCEPTION 'Signed consultations are immutable (FR-EMR-03); add an addendum instead';
  END IF;
  RETURN NEW;
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_consultation_immutable
BEFORE UPDATE OR DELETE ON consultations
FOR EACH ROW EXECUTE FUNCTION reject_signed_consultation_update();

-- NFR-SEC-04: audit_log is append-only.
CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_log is append-only (NFR-SEC-04)';
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_append_only
BEFORE UPDATE OR DELETE ON audit_log
FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
