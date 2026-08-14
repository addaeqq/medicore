"use client";
import { useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Input, PageTitle, Select } from "@/components/ui";

type Doctor = { staff_id: string; full_name: string; department: string | null };
const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

export default function Admin() {
  const { profile, loading } = useMe();
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [form, setForm] = useState({ doctorId: "", weekday: "1", startTime: "09:00", endTime: "12:00", slotMinutes: "20", room: "" });
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (profile) api.get<{ doctors: Doctor[] }>("/api/doctors").then(d => setDoctors(d.doctors));
  }, [profile]);

  if (loading || !profile) return null;

  async function create() {
    setBusy(true); setError(null); setResult(null);
    try {
      const out = await api.post<{ scheduleId: string; slotsCreated: number }>("/api/schedules", {
        doctorId: form.doctorId, weekday: parseInt(form.weekday, 10),
        startTime: form.startTime, endTime: form.endTime,
        slotMinutes: parseInt(form.slotMinutes, 10), room: form.room || null,
      });
      setResult(`Schedule published — ${out.slotsCreated} slots generated for the next 4 weeks.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not publish the schedule");
    } finally { setBusy(false); }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Administration">Publish a weekly clinic</PageTitle>
      <Card className="max-w-lg">
        <ErrorNote message={error} />
        {result && <p className="text-sm text-[var(--theatre)] border border-[var(--theatre)]/40 bg-emerald-50/40 rounded-sm px-3 py-2 mb-3">{result}</p>}
        <Field label="Doctor">
          <Select value={form.doctorId} onChange={e => setForm(f => ({ ...f, doctorId: e.target.value }))}>
            <option value="">Choose…</option>
            {doctors.map(d => <option key={d.staff_id} value={d.staff_id}>{d.full_name}{d.department ? ` — ${d.department}` : ""}</option>)}
          </Select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Weekday">
            <Select value={form.weekday} onChange={e => setForm(f => ({ ...f, weekday: e.target.value }))}>
              {WEEKDAYS.map((w, i) => <option key={w} value={i}>{w}</option>)}
            </Select>
          </Field>
          <Field label="Room (optional)"><Input value={form.room} onChange={e => setForm(f => ({ ...f, room: e.target.value }))} /></Field>
          <Field label="Starts"><Input type="time" value={form.startTime} onChange={e => setForm(f => ({ ...f, startTime: e.target.value }))} /></Field>
          <Field label="Ends"><Input type="time" value={form.endTime} onChange={e => setForm(f => ({ ...f, endTime: e.target.value }))} /></Field>
          <Field label="Slot length (minutes)"><Input type="number" min={5} max={120} value={form.slotMinutes} onChange={e => setForm(f => ({ ...f, slotMinutes: e.target.value }))} /></Field>
        </div>
        <p className="text-xs text-[var(--ink)]/50 mb-3">Slots are generated for the next 4 weeks; overlapping publishes never duplicate a slot.</p>
        <Button onClick={create} disabled={busy || !form.doctorId}>{busy ? "Publishing…" : "Publish clinic"}</Button>
      </Card>
    </Shell>
  );
}
