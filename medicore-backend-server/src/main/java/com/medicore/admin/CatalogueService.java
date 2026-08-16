package com.medicore.admin;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reference catalogues behind the admin.catalogues action. The permission existed in the
 * SRS §4 matrix from the start with nothing implementing it, so laboratory tests and
 * departments could only be changed by editing the database — the pharmacy formulary was
 * the exception, since FR-PHM-04 gave drugs their own endpoint.
 *
 * Both tables carry a UNIQUE name; the duplicate check here exists for the message, the
 * constraint remains the invariant.
 */
@Service
public class CatalogueService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CatalogueService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc; this.audit = audit;
    }

    @Transactional
    public UUID createLabTest(String name, String specimen, BigDecimal price,
                              Short tatHours, UUID actorUserId) {
        requireFree("lab_tests", "name", name, "A laboratory test with that name already exists");
        UUID id = jdbc.queryForObject("""
            INSERT INTO lab_tests (name, specimen, price, tat_hours)
            VALUES (btrim(?), btrim(?), ?, ?)
            RETURNING lab_test_id
            """, UUID.class, name, specimen, price, tatHours);
        audit.log(actorUserId, null, "admin.catalogues", "lab_tests:" + id,
            "{\"name\":\"" + json(name) + "\"}");
        return id;
    }

    @Transactional
    public UUID createDepartment(String name, String deptType, BigDecimal consultFee, UUID actorUserId) {
        requireFree("departments", "name", name, "A department with that name already exists");
        UUID id = jdbc.queryForObject("""
            INSERT INTO departments (name, dept_type, consult_fee)
            VALUES (btrim(?), ?, ?)
            RETURNING department_id
            """, UUID.class, name, deptType, consultFee);
        audit.log(actorUserId, null, "admin.catalogues", "departments:" + id,
            "{\"name\":\"" + json(name) + "\",\"consultFee\":" + consultFee + "}");
        return id;
    }

    /** The table name is never user-supplied — only the two literals above reach this. */
    private void requireFree(String table, String column, String value, String message) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE lower(btrim(" + column + ")) = lower(btrim(?))",
            Long.class, value);
        if (n != null && n > 0) throw new ApiException(409, message);
    }

    private static String json(String s) {
        return s.replace("\\", "").replace("\"", "'");
    }
}
