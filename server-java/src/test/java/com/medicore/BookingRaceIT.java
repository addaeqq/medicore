package com.medicore;

import com.medicore.common.ApiException;
import com.medicore.domain.*;
import com.medicore.repo.Repositories.*;
import com.medicore.scheduling.SchedulingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests against the real database (run: MEDICORE_IT=true ./gradlew test).
 * Verifies FR-APT-04 (booking race), FR-EMR-03 and NFR-SEC-04 (integrity triggers).
 */
@SpringBootTest(properties = "medicore.seed=false")
@EnabledIfEnvironmentVariable(named = "MEDICORE_IT", matches = "true")
class BookingRaceIT {

    @Autowired SchedulingService scheduling;
    @Autowired UserRepository users;
    @Autowired StaffRepository staff;
    @Autowired PatientRepository patients;
    @Autowired DepartmentRepository departments;
    @Autowired SlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired JdbcTemplate jdbc;

    private Patient newPatient(String tag) {
        UserAccount u = users.save(new UserAccount("race-" + tag + "-" + UUID.randomUUID() + "@t.test", "x", "patient"));
        return patients.save(new Patient(u.getUserId(), "MRN-RACE-" + UUID.randomUUID(), "P " + tag,
            LocalDate.of(1990, 1, 1), "other", null, null));
    }

    @Test
    void parallelBookingsOfOneSlotExactlyOneWins() throws Exception {
        Department dept = departments.findByName("RaceDept").orElseGet(() ->
            departments.save(new Department("RaceDept", "clinical", new BigDecimal("10"))));
        UserAccount du = users.save(new UserAccount("race-doc-" + UUID.randomUUID() + "@t.test", "x", "doctor"));
        Staff doc = staff.save(new Staff(du.getUserId(), dept.getDepartmentId(), "doctor", "Race Doc"));

        int todayWeekday = LocalDate.now(ZoneOffset.UTC).getDayOfWeek().getValue() % 7;
        scheduling.createScheduleWithSlots(doc.getStaffId(), todayWeekday,
            LocalTime.of(23, 0), LocalTime.of(23, 40), 20, null, 8);
        Slot slot = slots.findFirstByDoctorIdAndStatusOrderByStartsAtAsc(doc.getStaffId(), "available").orElseThrow();

        Patient p1 = newPatient("one");
        Patient p2 = newPatient("two");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Object> attempt1 = () -> { go.await(); return scheduling.bookAppointment(slot.getSlotId(), p1.getPatientId(), du.getUserId()); };
        Callable<Object> attempt2 = () -> { go.await(); return scheduling.bookAppointment(slot.getSlotId(), p2.getPatientId(), du.getUserId()); };
        Future<Object> f1 = pool.submit(attempt1);
        Future<Object> f2 = pool.submit(attempt2);
        go.countDown();

        int wins = 0, conflicts = 0;
        for (Future<Object> f : java.util.List.of(f1, f2)) {
            try { f.get(30, TimeUnit.SECONDS); wins++; }
            catch (ExecutionException e) {
                Throwable c = e.getCause();
                // Loser sees 409 (or the availability pre-check catches it first)
                if (c instanceof ApiException api && api.status() == 409) conflicts++;
                else fail("Unexpected failure: " + c);
            }
        }
        pool.shutdown();
        assertEquals(1, wins, "exactly one booking wins");
        assertEquals(1, conflicts, "loser receives 409");
        assertEquals(1, appointments.countBySlotId(slot.getSlotId()), "DB holds exactly one appointment");
    }

    @Test
    void signedConsultationIsImmutableAtDatabaseLevel() { // FR-EMR-03
        Department dept = departments.findByName("RaceDept").orElseGet(() ->
            departments.save(new Department("RaceDept", "clinical", BigDecimal.TEN)));
        UserAccount du = users.save(new UserAccount("imm-doc-" + UUID.randomUUID() + "@t.test", "x", "doctor"));
        Staff doc = staff.save(new Staff(du.getUserId(), dept.getDepartmentId(), "doctor", "Imm Doc"));
        Patient p = newPatient("imm");
        UUID cid = UUID.randomUUID();
        jdbc.update("INSERT INTO consultations (consultation_id, doctor_id, patient_id, diagnosis, signed_at) " +
            "VALUES (?,?,?,?, now())", cid, doc.getStaffId(), p.getPatientId(), "test");
        var ex = assertThrows(Exception.class, () ->
            jdbc.update("UPDATE consultations SET diagnosis = 'tampered' WHERE consultation_id = ?", cid));
        assertTrue(ex.getMessage().contains("immutable"));
    }

    @Test
    void auditLogRejectsUpdateAndDelete() { // NFR-SEC-04
        jdbc.update("INSERT INTO audit_log (action) VALUES ('it.test')");
        Long id = jdbc.queryForObject("SELECT max(audit_id) FROM audit_log", Long.class);
        assertTrue(assertThrows(Exception.class, () ->
            jdbc.update("UPDATE audit_log SET action = 'x' WHERE audit_id = ?", id)).getMessage().contains("append-only"));
        assertTrue(assertThrows(Exception.class, () ->
            jdbc.update("DELETE FROM audit_log WHERE audit_id = ?", id)).getMessage().contains("append-only"));
    }
}
