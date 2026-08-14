"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Shell from "@/components/Shell";
import PatientPicker, { PatientRow } from "@/components/PatientPicker";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtMoney, fmtWhen } from "@/lib/api";
import { Button, Card, Empty, ErrorNote, Field, PageTitle, PatientBand, Select } from "@/components/ui";

type Slot = { slot_id: string; starts_at: string; ends_at: string; doctor: string; department: string | null; consult_fee: number };
type Dept = { department_id: string; name: string };
type Doctor = { staff_id: string; full_name: string; department: string | null };

export default function Book() {
  const { profile, loading } = useMe();
  const router = useRouter();
  const [departments, setDepartments] = useState<Dept[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [departmentId, setDepartmentId] = useState("");
  const [doctorId, setDoctorId] = useState("");
  const [slots, setSlots] = useState<Slot[]>([]);
  const [target, setTarget] = useState<PatientRow | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [booking, setBooking] = useState<string | null>(null);

  useEffect(() => {
    if (!profile) return;
    api.get<{ departments: Dept[] }>("/api/departments").then(d => setDepartments(d.departments));
    api.get<{ doctors: Doctor[] }>("/api/doctors").then(d => setDoctors(d.doctors));
  }, [profile]);

  useEffect(() => {
    if (!profile) return;
    const params = new URLSearchParams();
    if (departmentId) params.set("departmentId", departmentId);
    if (doctorId) params.set("doctorId", doctorId);
    api.get<{ slots: Slot[] }>(`/api/appointments/slots?${params}`).then(d => setSlots(d.slots));
  }, [profile, departmentId, doctorId]);

  if (loading || !profile) return null;
  const isStaff = profile.user.role !== "patient";

  async function book(slotId: string) {
    setError(null); setBooking(slotId);
    try {
      const body: { slotId: string; patientId?: string } = { slotId };
      if (isStaff) {
        if (!target) { setError("Choose the patient first."); setBooking(null); return; }
        body.patientId = target.patient_id;
      }
      await api.post("/api/appointments", body);
      router.push("/appointments" + (isStaff && target ? `?patientId=${target.patient_id}` : ""));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Booking failed");
      setBooking(null);
      if (err instanceof ApiError && err.status === 409) {
        const params = new URLSearchParams();
        if (departmentId) params.set("departmentId", departmentId);
        if (doctorId) params.set("doctorId", doctorId);
        api.get<{ slots: Slot[] }>(`/api/appointments/slots?${params}`).then(d => setSlots(d.slots));
      }
    }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Scheduling">Book an appointment</PageTitle>
      {isStaff && (
        <Card className="mb-5">
          <p className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-2">Booking for</p>
          {target
            ? <PatientBand name={target.full_name} mrn={target.mrn} dob={target.dob} sex={target.sex} />
            : <PatientPicker onPick={setTarget} />}
          {target && <button className="text-sm text-[var(--theatre)] underline" onClick={() => setTarget(null)}>Change patient</button>}
        </Card>
      )}
      <ErrorNote message={error} />
      <div className="grid sm:grid-cols-2 gap-3 mb-5">
        <Field label="Department">
          <Select value={departmentId} onChange={e => setDepartmentId(e.target.value)}>
            <option value="">All departments</option>
            {departments.map(d => <option key={d.department_id} value={d.department_id}>{d.name}</option>)}
          </Select>
        </Field>
        <Field label="Doctor">
          <Select value={doctorId} onChange={e => setDoctorId(e.target.value)}>
            <option value="">All doctors</option>
            {doctors.map(d => <option key={d.staff_id} value={d.staff_id}>{d.full_name}{d.department ? ` — ${d.department}` : ""}</option>)}
          </Select>
        </Field>
      </div>
      {slots.length === 0
        ? <Empty>No open slots match these filters. Try another department or check back after schedules are published.</Empty>
        : <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {slots.map(s => (
              <Card key={s.slot_id}>
                <p className="font-chart text-sm">{fmtWhen(s.starts_at)}</p>
                <p className="text-sm mt-1">{s.doctor}</p>
                <p className="text-xs text-[var(--ink)]/60">{s.department ?? "—"} · {fmtMoney(s.consult_fee)}</p>
                <div className="mt-3">
                  <Button onClick={() => book(s.slot_id)} disabled={booking === s.slot_id}>
                    {booking === s.slot_id ? "Booking…" : "Book this slot"}
                  </Button>
                </div>
              </Card>
            ))}
          </div>}
    </Shell>
  );
}
