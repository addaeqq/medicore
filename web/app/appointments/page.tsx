"use client";
import { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Shell from "@/components/Shell";
import PatientPicker, { PatientRow } from "@/components/PatientPicker";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, Empty, ErrorNote, PageTitle, PatientBand } from "@/components/ui";

type Appt = { appointment_id: string; status: string; starts_at: string; doctor: string; department: string };

function AppointmentsInner() {
  const { profile, loading } = useMe();
  const params = useSearchParams();
  const [target, setTarget] = useState<PatientRow | null>(null);
  const [rows, setRows] = useState<Appt[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isStaff = profile ? profile.user.role !== "patient" : false;
  const patientId = isStaff ? target?.patient_id : profile?.user.patientId;

  const refresh = useCallback(() => {
    if (!patientId) { setRows(null); return; }
    api.get<{ appointments: Appt[] }>(`/api/appointments/patient/${patientId}`)
      .then(d => setRows(d.appointments))
      .catch(e => setError(e instanceof ApiError ? e.message : "Could not load appointments"));
  }, [patientId]);

  useEffect(() => { refresh(); }, [refresh]);
  useEffect(() => {
    const pid = params.get("patientId");
    if (pid && isStaff && !target) {
      api.get<{ patients: PatientRow[] }>(`/api/patients/search?q=`).catch(() => null);
    }
  }, [params, isStaff, target]);

  if (loading || !profile) return null;

  async function act(id: string, action: "cancel" | "checkin") {
    setError(null);
    try {
      await api.post(`/api/appointments/${id}/${action}`);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Scheduling">{isStaff ? "Patient appointments & check-in" : "My appointments"}</PageTitle>
      {isStaff && (
        <Card className="mb-5">
          {target
            ? <>
                <PatientBand name={target.full_name} mrn={target.mrn} dob={target.dob} sex={target.sex} />
                <button className="text-sm text-[var(--theatre)] underline" onClick={() => { setTarget(null); setRows(null); }}>Change patient</button>
              </>
            : <PatientPicker onPick={setTarget} />}
        </Card>
      )}
      <ErrorNote message={error} />
      {!patientId
        ? (isStaff ? <Empty>Search for a patient to see their appointments.</Empty> : null)
        : rows === null ? null
        : rows.length === 0
          ? <Empty action={{ href: "/book", label: "Book an appointment" }}>No appointments yet.</Empty>
          : <div className="space-y-3">
              {rows.map(a => (
                <Card key={a.appointment_id} className="flex flex-wrap items-center gap-3">
                  <span className="font-chart text-sm">{fmtWhen(a.starts_at)}</span>
                  <span className="text-sm">{a.doctor}</span>
                  <span className="text-xs text-[var(--ink)]/60">{a.department}</span>
                  <span className="ml-auto"><Badge status={a.status} /></span>
                  {a.status === "booked" && (
                    <div className="flex gap-2">
                      {isStaff && profile.user.role !== "patient" &&
                        <Button onClick={() => act(a.appointment_id, "checkin")}>Check in</Button>}
                      <Button kind="danger" onClick={() => act(a.appointment_id, "cancel")}>Cancel</Button>
                    </div>
                  )}
                </Card>
              ))}
            </div>}
    </Shell>
  );
}

export default function Appointments() {
  return <Suspense fallback={null}><AppointmentsInner /></Suspense>;
}
