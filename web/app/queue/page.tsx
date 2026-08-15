"use client";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, Empty, ErrorNote, Field, PageTitle, Select } from "@/components/ui";

type Entry = { queue_entry_id: string; status: string; checked_in_at: string; priority: number; full_name: string; mrn: string; appointment_id: string; patient_id: string };
type Dept = { department_id: string; name: string };

export default function Queue() {
  const { profile, loading } = useMe();
  const router = useRouter();
  const [departments, setDepartments] = useState<Dept[]>([]);
  const [departmentId, setDepartmentId] = useState("");
  const [entries, setEntries] = useState<Entry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [starting, setStarting] = useState<string | null>(null);

  useEffect(() => {
    if (!profile) return;
    api.get<{ departments: Dept[] }>("/api/departments").then(d => {
      setDepartments(d.departments);
      const own = profile.staff?.department_id;
      setDepartmentId(own ?? d.departments[0]?.department_id ?? "");
    });
  }, [profile]);

  const refresh = useCallback(() => {
    if (!departmentId) return;
    api.get<{ queue: Entry[] }>(`/api/appointments/queue/${departmentId}`).then(d => setEntries(d.queue));
  }, [departmentId]);
  useEffect(() => { refresh(); const t = setInterval(refresh, 15000); return () => clearInterval(t); }, [refresh]);

  if (loading || !profile) return null;
  const isDoctor = profile.user.role === "doctor";

  async function startConsultation(entry: Entry) {
    setError(null); setStarting(entry.queue_entry_id);
    try {
      // FR-APT-08 carries the appointment id on the queue row, so the consultation starts
      // directly. Resolving it client-side via /patients/search was denied for doctors
      // (patient.read_profile is RELATIONSHIP-scoped, TD-10).
      const out = await api.post<{ consultationId: string }>("/api/consultations/start",
        { appointmentId: entry.appointment_id });
      router.push(`/consultations/${out.consultationId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start the consultation");
      setStarting(null);
    }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Front desk & clinic">Department queue</PageTitle>
      <div className="max-w-xs mb-5">
        <Field label="Department">
          <Select value={departmentId} onChange={e => setDepartmentId(e.target.value)}>
            {departments.map(d => <option key={d.department_id} value={d.department_id}>{d.name}</option>)}
          </Select>
        </Field>
      </div>
      <ErrorNote message={error} />
      {entries.length === 0
        ? <Empty>The queue is clear. Checked-in patients appear here in order.</Empty>
        : <ol className="space-y-3">
            {entries.map((q, i) => (
              <li key={q.queue_entry_id}>
                <Card className="flex flex-wrap items-center gap-3">
                  <span className="font-display text-2xl w-8 text-[var(--theatre)]">{i + 1}</span>
                  <span className="font-chart text-xs text-[var(--theatre)]">{q.mrn}</span>
                  <span className="text-sm">{q.full_name}</span>
                  <span className="text-xs text-[var(--ink)]/60">arrived {fmtWhen(q.checked_in_at)}</span>
                  <span className="ml-auto"><Badge status={q.status} /></span>
                  {isDoctor && q.status === "waiting" && (
                    <Button onClick={() => startConsultation(q)} disabled={starting === q.queue_entry_id}>
                      {starting === q.queue_entry_id ? "Opening…" : "Start consultation"}
                    </Button>
                  )}
                </Card>
              </li>
            ))}
          </ol>}
    </Shell>
  );
}
