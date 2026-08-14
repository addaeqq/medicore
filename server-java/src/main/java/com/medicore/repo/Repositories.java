package com.medicore.repo;

import com.medicore.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface Repositories {
    interface UserRepository extends JpaRepository<UserAccount, UUID> {
        Optional<UserAccount> findByEmail(String email);
    }
    interface PatientRepository extends JpaRepository<Patient, UUID> {
        Optional<Patient> findByUserId(UUID userId);
    }
    interface StaffRepository extends JpaRepository<Staff, UUID> {
        Optional<Staff> findByUserId(UUID userId);
    }
    interface DepartmentRepository extends JpaRepository<Department, UUID> {
        Optional<Department> findByName(String name);
    }
    interface ScheduleRepository extends JpaRepository<Schedule, UUID> {}
    interface SlotRepository extends JpaRepository<Slot, UUID> {
        Optional<Slot> findFirstByDoctorIdAndStatusOrderByStartsAtAsc(UUID doctorId, String status);
    }
    interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
        long countBySlotId(UUID slotId);
    }
    interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {}
    interface ConsultationRepository extends JpaRepository<Consultation, UUID> {}
    interface AddendumRepository extends JpaRepository<Addendum, UUID> {}
    interface AllergyRepository extends JpaRepository<Allergy, UUID> {
        java.util.List<Allergy> findByPatientId(UUID patientId);
    }
    interface DrugRepository extends JpaRepository<Drug, UUID> {
        Optional<Drug> findByGenericNameAndStrength(String genericName, String strength);
    }
    interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {}
    interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {}
    interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, UUID> {
        java.util.List<PrescriptionItem> findByPrescriptionId(UUID prescriptionId);
    }
    interface DispenseRepository extends JpaRepository<Dispense, UUID> {}
}
