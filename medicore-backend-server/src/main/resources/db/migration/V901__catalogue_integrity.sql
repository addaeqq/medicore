-- Formulary identity (FR-PHM-04).
--
-- drugs carried no uniqueness guard: with the pharmacist's "add a drug" form there is now a
-- runtime path that can create a second row for a drug that already exists. A near-duplicate
-- is not cosmetic here — stock splits across two drug_ids, FEFO allocates from whichever row
-- the prescriber happened to pick, and postDispenseCharges bills that row's unit_price.
--
-- Identity is the presentation, compared case- and whitespace-insensitively so that
-- 'Paracetamol'/'paracetamol ' cannot both exist. A distinct brand remains a distinct entry:
-- a hospital may legitimately stock two brands of the same molecule at different prices, and
-- coalesce keeps the unbranded generic in the comparison rather than letting NULLs slip past
-- the index (Postgres treats NULLs as distinct).
--
-- Numbered above the demo seed (V900) deliberately: the seed is already applied on deployed
-- databases, so a lower version would be an out-of-order migration and Flyway is not
-- configured with outOfOrder.
CREATE UNIQUE INDEX uq_drugs_identity ON drugs (
  lower(btrim(generic_name)),
  lower(btrim(strength)),
  lower(btrim(form)),
  lower(btrim(coalesce(brand_name, '')))
);

-- lab_tests.name and departments.name are already UNIQUE, but case-sensitively: 'Widal Test'
-- and 'widal test' would both be accepted. Now that admin.catalogues has endpoints behind it,
-- that is a reachable way to create a confusing near-duplicate, so the constraint matches the
-- comparison CatalogueService makes before inserting.
CREATE UNIQUE INDEX uq_lab_tests_name ON lab_tests (lower(btrim(name)));
CREATE UNIQUE INDEX uq_departments_name ON departments (lower(btrim(name)));
