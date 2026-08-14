package com.medicore;

import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.clinical.ConsultationService;
import com.medicore.domain.*;
import com.medicore.pharmacy.PharmacyService;
import com.medicore.repo.Repositories.*;
import com.medicore.scheduling.SchedulingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 integration tests (run: MEDICORE_IT=true ./gradlew test).
 * Covers sign-and-lock (FR-EMR-03/04), FEFO dispensing order (FR-PHM-02),
 * and the concurrent-dispense stock guard (FR-PHM-05).
 */
@SpringBootTest(properties = "medicore.seed=false")
@EnabledIfEnvironmentVariable(named = "MEDICORE_IT", matches = "true")
class ClinicalPharmacyIT {

    @Autowired ConsultationService clinical;
    @Autowired PharmacyService pharmacy;
    @Autowired SchedulingService scheduling;
    @Autowired UserRepository users;
    @Autowired StaffRepository staff;
    @Autowired PatientRepository patients;
    @Autowired DepartmentRepository departments;
    @Autowired ConsultationRepository consultations;
    @Autowired PrescriptionItemRepository rxItems;
    @Autowired JdbcTemplate jdbc;

    private record Actors(SessionUser doctor, Staff doctorStaff, Patient patient, Consultation consult) {}

    private Actors freshSignedConsultation() {
        Department dept = departments.findByName("M2Dept").orElseGet(() ->
            departments.save(new Department("M2Dept", "clinical", BigDecimal.TEN)));
        UserAccount du = users.save(new UserAccount("m2-doc-" + UUID.randomUUID() + "@t.test", "x", "doctor"));
        Staff doc = staff.save(new Staff(du.getUserId(), dept.getDepartmentId(), "doctor", "M2 Doc"));
        UserAccount pu = users.save(new UserAccount("m2-pat-" + UUID.randomUUID() + "@t.test", "x", "patient"));
        Patient pat = patients.save(new Patient(pu.getUserId(), "MRN-M2-" + UUID.randomUUID(), "M2 Patient",
            LocalDate.of(1985, 3, 3), "female", null, null));
        SessionUser docSession = new SessionUser(du.getUserId(), "doctor", doc.getStaffId(), null);

        // consultation created directly (appointment flow covered by BookingRaceIT)
        Consultation c = consultations.save(new Consultation(null, doc.getStaffId(), pat.getPatientId()));
        clinical.updateNotes(c.getConsultationId(), docSession, "cough", "clear chest", "URTI");
        clinical.sign(c.getConsultationId(), docSession);
        return new Actors(docSession, doc, pat, consultations.findById(c.getConsultationId()).orElseThrow());
    }

    @Test
    void signedConsultationLocksAndAddendumIsTheOnlyPath() {
        Actors a = freshSignedConsultation();
        // service-level lock
        var ex = assertThrows(ApiException.class, () ->
            clinical.updateNotes(a.consult().getConsultationId(), a.doctor(), null, null, "changed"));
        assertEquals(422, ex.status());
        // addendum path works, and only for the author
        assertNotNull(clinical.addAddendum(a.consult().getConsultationId(), a.doctor(), "BP re-checked: normal"));
        SessionUser other = new SessionUser(UUID.randomUUID(), "doctor", UUID.randomUUID(), null);
        assertEquals(403, assertThrows(ApiException.class, () ->
            clinical.addAddendum(a.consult().getConsultationId(), other, "x")).status());
    }

    @Test
    void dispenseFollowsFefoAcrossBatchesAndUpdatesStatus() {
        Actors a = freshSignedConsultation();
        SessionUser pharm = pharmacistSession();

        Drug drug = pharmacy.createDrug("FefoTestDrug-" + UUID.randomUUID(), null, "tablet", "500mg",
            new BigDecimal("1.50"), 5);
        var early = pharmacy.receiveBatch(drug.getDrugId(), "B-EARLY", LocalDate.now().plusDays(30), 6, null);
        var late = pharmacy.receiveBatch(drug.getDrugId(), "B-LATE", LocalDate.now().plusDays(300), 20, null);

        var rx = pharmacy.createPrescription(a.consult().getConsultationId(),
            List.of(new PharmacyService.RxItemRequest(drug.getDrugId(), "500mg", "tds", (short) 5, 10)), a.doctor());
        UUID itemId = rxItems.findByPrescriptionId(rx.getPrescriptionId()).get(0).getRxItemId();

        var result = pharmacy.dispense(rx.getPrescriptionId(),
            List.of(new PharmacyService.DispenseRequest(itemId, 10)), pharm);
        assertEquals("dispensed", result.get("status"));

        Integer earlyLeft = jdbc.queryForObject("SELECT qty_on_hand FROM stock_batches WHERE batch_id = ?",
            Integer.class, early.getBatchId());
        Integer lateLeft = jdbc.queryForObject("SELECT qty_on_hand FROM stock_batches WHERE batch_id = ?",
            Integer.class, late.getBatchId());
        assertEquals(0, earlyLeft, "earliest-expiry batch drained first");
        assertEquals(16, lateLeft, "remainder drawn from later batch");
    }

    @Test
    void concurrentDispensesNeverOversellStock() throws Exception {
        Actors a = freshSignedConsultation();
        SessionUser pharm1 = pharmacistSession();
        SessionUser pharm2 = pharmacistSession();

        Drug drug = pharmacy.createDrug("RaceDrug-" + UUID.randomUUID(), null, "tablet", "250mg",
            BigDecimal.ONE, 5);
        pharmacy.receiveBatch(drug.getDrugId(), "B-RACE", LocalDate.now().plusDays(90), 10, null);

        // two prescriptions of 7 against stock of 10 -> at most one can fully dispense
        var rx1 = pharmacy.createPrescription(a.consult().getConsultationId(),
            List.of(new PharmacyService.RxItemRequest(drug.getDrugId(), "250mg", "bd", (short) 3, 7)), a.doctor());
        var rx2 = pharmacy.createPrescription(a.consult().getConsultationId(),
            List.of(new PharmacyService.RxItemRequest(drug.getDrugId(), "250mg", "bd", (short) 3, 7)), a.doctor());
        UUID item1 = rxItems.findByPrescriptionId(rx1.getPrescriptionId()).get(0).getRxItemId();
        UUID item2 = rxItems.findByPrescriptionId(rx2.getPrescriptionId()).get(0).getRxItemId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Object> f1 = pool.submit(() -> { go.await(); return pharmacy.dispense(rx1.getPrescriptionId(),
            List.of(new PharmacyService.DispenseRequest(item1, 7)), pharm1); });
        Future<Object> f2 = pool.submit(() -> { go.await(); return pharmacy.dispense(rx2.getPrescriptionId(),
            List.of(new PharmacyService.DispenseRequest(item2, 7)), pharm2); });
        go.countDown();

        int wins = 0, conflicts = 0;
        for (Future<Object> f : List.of(f1, f2)) {
            try { f.get(30, TimeUnit.SECONDS); wins++; }
            catch (ExecutionException e) {
                if (e.getCause() instanceof ApiException api && api.status() == 409) conflicts++;
                else fail("Unexpected failure: " + e.getCause());
            }
        }
        pool.shutdown();
        assertEquals(1, wins, "exactly one dispense succeeds");
        assertEquals(1, conflicts, "the other receives 409");

        Integer left = jdbc.queryForObject(
            "SELECT COALESCE(sum(qty_on_hand),0) FROM stock_batches WHERE drug_id = ?",
            Integer.class, drug.getDrugId());
        assertEquals(3, left, "stock decremented exactly once; never negative");
    }

    private SessionUser pharmacistSession() {
        UserAccount u = users.save(new UserAccount("m2-ph-" + UUID.randomUUID() + "@t.test", "x", "pharmacist"));
        Staff s = staff.save(new Staff(u.getUserId(), null, "pharmacist", "M2 Pharm"));
        return new SessionUser(u.getUserId(), "pharmacist", s.getStaffId(), null);
    }
}
