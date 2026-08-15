"use client";
import { useCallback, useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, Empty, ErrorNote, Field, Input, PageTitle, PatientBand, Select } from "@/components/ui";

type Bed = {
  bed_id: string; label: string; bed_status: string; room_no: string;
  admission_id: string | null; admitted_at: string | null; day_number: number | null;
  patient_id: string | null; patient: string | null; mrn: string | null; dob: string | null; sex: string | null;
  admitting_doctor: string | null; allergy_count: number; last_observation: string | null;
};
type Ward = { ward_id: string; name: string; daily_tariff: number };
type Board = { ward: Ward; beds: Bed[]; pinned: boolean };
type Vital = {
  vitals_id: string; bp_sys: number | null; bp_dia: number | null; temp_c: string | null;
  pulse: number | null; spo2: number | null; weight_kg: string | null;
  recorded_at: string; recorded_by: string;
};
type Allergy = { substance: string; severity: string };

const EMPTY_OBS = { bpSys: "", bpDia: "", tempC: "", pulse: "", spo2: "", weightKg: "" };

/** Observations outside these bands are worth a second look before they are filed. */
function flags(o: typeof EMPTY_OBS): string[] {
  const out: string[] = [];
  const n = (v: string) => (v.trim() === "" ? null : Number(v));
  const sys = n(o.bpSys), dia = n(o.bpDia), t = n(o.tempC), p = n(o.pulse), s = n(o.spo2);
  if (sys !== null && (sys >= 140 || sys < 90)) out.push(`systolic ${sys}`);
  if (dia !== null && (dia >= 90 || dia < 60)) out.push(`diastolic ${dia}`);
  if (t !== null && (t >= 38 || t < 35.5)) out.push(`temperature ${t}°C`);
  if (p !== null && (p > 100 || p < 50)) out.push(`pulse ${p}`);
  if (s !== null && s < 94) out.push(`SpO₂ ${s}%`);
  return out;
}

export default function WardPage() {
  const { profile, loading } = useMe();
  const [board, setBoard] = useState<Board | null>(null);
  const [wards, setWards] = useState<Ward[]>([]);
  const [wardId, setWardId] = useState<string>("");
  const [open, setOpen] = useState<Bed | null>(null);
  const [vitals, setVitals] = useState<Vital[]>([]);
  const [allergies, setAllergies] = useState<Allergy[]>([]);
  const [obs, setObs] = useState(EMPTY_OBS);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    const q = wardId ? `?wardId=${wardId}` : "";
    api.get<Board>(`/api/nursing/ward${q}`)
      .then(b => { setBoard(b); if (!wardId) setWardId(b.ward.ward_id); })
      .catch(e => setError(e instanceof ApiError ? e.message : "Could not load the ward board"));
  }, [wardId]);

  useEffect(() => { if (profile) refresh(); }, [profile, refresh]);
  useEffect(() => {
    if (profile && profile.user.role !== "nurse")
      api.get<{ wards: Ward[] }>("/api/nursing/wards").then(d => setWards(d.wards)).catch(() => null);
  }, [profile]);

  if (loading || !profile) return null;

  async function openBed(b: Bed) {
    if (!b.patient_id) return;
    setOpen(b); setObs(EMPTY_OBS); setError(null); setNote(null);
    const [v, a] = await Promise.all([
      api.get<{ vitals: Vital[] }>(`/api/patients/${b.patient_id}/vitals`).catch(() => ({ vitals: [] })),
      api.get<{ allergies: Allergy[] }>(`/api/patients/${b.patient_id}/allergies`).catch(() => ({ allergies: [] })),
    ]);
    setVitals(v.vitals); setAllergies(a.allergies);
  }

  async function saveObservations() {
    if (!open?.patient_id) return;
    setBusy(true); setError(null); setNote(null);
    try {
      const num = (v: string) => (v.trim() === "" ? undefined : Number(v));
      await api.post(`/api/patients/${open.patient_id}/vitals`, {
        bpSys: num(obs.bpSys), bpDia: num(obs.bpDia), tempC: num(obs.tempC),
        pulse: num(obs.pulse), spo2: num(obs.spo2), weightKg: num(obs.weightKg),
      });
      const v = await api.get<{ vitals: Vital[] }>(`/api/patients/${open.patient_id}/vitals`);
      setVitals(v.vitals); setObs(EMPTY_OBS); setNote("Observations filed to the chart.");
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not record the observations");
    } finally { setBusy(false); }
  }

  const anything = Object.values(obs).some(v => v.trim() !== "");
  const warn = flags(obs);
  const occupied = board?.beds.filter(b => b.patient_id).length ?? 0;

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Nursing">{board ? board.ward.name : "Ward board"}</PageTitle>

      {board && !board.pinned && wards.length > 0 && (
        <div className="max-w-xs mb-5">
          <Field label="Ward">
            <Select value={wardId} onChange={e => { setWardId(e.target.value); setOpen(null); }}>
              {wards.map(w => <option key={w.ward_id} value={w.ward_id}>{w.name}</option>)}
            </Select>
          </Field>
        </div>
      )}

      <ErrorNote message={error} />

      {board && (
        <p className="text-sm text-[var(--ink)]/60 mb-4">
          <span className="font-chart">{occupied}</span> of <span className="font-chart">{board.beds.length}</span> beds occupied
          · bed day charged at <span className="font-chart">GHS {Number(board.ward.daily_tariff).toFixed(2)}</span>
        </p>
      )}

      {board && board.beds.length === 0 && <Empty>This ward has no beds configured yet.</Empty>}

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {board?.beds.map(b => (
          <Card key={b.bed_id} className={b.patient_id ? "border-[var(--theatre)]/40" : ""}>
            <div className="flex items-baseline gap-2 mb-2">
              <span className="font-chart text-xs text-[var(--theatre)]">{b.room_no} · {b.label}</span>
              <span className="ml-auto"><Badge status={b.bed_status} /></span>
            </div>
            {b.patient_id ? (
              <>
                <p className="font-display text-lg leading-tight">{b.patient}</p>
                <p className="font-chart text-xs text-[var(--ink)]/60">{b.mrn}</p>
                <p className="text-xs text-[var(--ink)]/60 mt-2">
                  Day {b.day_number} · under {b.admitting_doctor}
                </p>
                <p className="text-xs text-[var(--ink)]/50">
                  Last obs {b.last_observation ? fmtWhen(b.last_observation) : "— none recorded"}
                </p>
                {Number(b.allergy_count) > 0 && (
                  <p className="text-xs text-[var(--triage)] mt-1">{b.allergy_count} allergy alert(s) on file</p>
                )}
                <div className="mt-3">
                  <Button kind="quiet" onClick={() => openBed(b)}>Open chart</Button>
                </div>
              </>
            ) : (
              <p className="text-sm text-[var(--ink)]/50 py-2">Empty</p>
            )}
          </Card>
        ))}
      </div>

      {open && open.patient_id && (
        <Card className="mt-5 border-[var(--theatre)]">
          <PatientBand name={open.patient ?? ""} mrn={open.mrn ?? ""} dob={open.dob ?? undefined} sex={open.sex ?? undefined} />
          {allergies.length > 0 && (
            <p className="text-sm text-[var(--triage)] mb-3">
              Allergies: {allergies.map(a => `${a.substance} (${a.severity})`).join(", ")}
            </p>
          )}
          {note && <p className="text-sm text-[var(--theatre)] border border-[var(--theatre)]/40 bg-emerald-50/40 rounded-sm px-3 py-2 mb-3">{note}</p>}

          <h3 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-2">Record observations</h3>
          <div className="grid grid-cols-2 sm:grid-cols-6 gap-3">
            <Field label="BP sys"><Input type="number" min={50} max={300} value={obs.bpSys} onChange={e => setObs(o => ({ ...o, bpSys: e.target.value }))} /></Field>
            <Field label="BP dia"><Input type="number" min={20} max={200} value={obs.bpDia} onChange={e => setObs(o => ({ ...o, bpDia: e.target.value }))} /></Field>
            <Field label="Temp °C"><Input type="number" step="0.1" min={25} max={45} value={obs.tempC} onChange={e => setObs(o => ({ ...o, tempC: e.target.value }))} /></Field>
            <Field label="Pulse"><Input type="number" min={20} max={250} value={obs.pulse} onChange={e => setObs(o => ({ ...o, pulse: e.target.value }))} /></Field>
            <Field label="SpO₂ %"><Input type="number" min={50} max={100} value={obs.spo2} onChange={e => setObs(o => ({ ...o, spo2: e.target.value }))} /></Field>
            <Field label="Weight kg"><Input type="number" step="0.1" min={0.5} max={400} value={obs.weightKg} onChange={e => setObs(o => ({ ...o, weightKg: e.target.value }))} /></Field>
          </div>
          {warn.length > 0 && (
            <p className="text-sm text-[var(--amber)] mb-2">Outside the normal range: {warn.join(", ")}. File it and escalate if the patient looks unwell.</p>
          )}
          <div className="flex gap-2">
            <Button onClick={saveObservations} disabled={busy || !anything}>{busy ? "Filing…" : "File observations"}</Button>
            <Button kind="quiet" onClick={() => setOpen(null)}>Close chart</Button>
          </div>

          <h3 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mt-5 mb-2">Observation chart</h3>
          {vitals.length === 0
            ? <p className="text-sm text-[var(--ink)]/60">Nothing recorded for this patient yet.</p>
            : <div className="overflow-x-auto">
                <table className="w-full text-sm min-w-[34rem]">
                  <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--ink)]/50">
                    <th className="py-1">When</th><th>BP</th><th>Temp</th><th>Pulse</th><th>SpO₂</th><th>Weight</th><th>By</th>
                  </tr></thead>
                  <tbody>
                    {vitals.map(v => (
                      <tr key={v.vitals_id} className="border-t border-[var(--hairline)]">
                        <td className="py-2 whitespace-nowrap">{fmtWhen(v.recorded_at)}</td>
                        <td className="font-chart">{v.bp_sys && v.bp_dia ? `${v.bp_sys}/${v.bp_dia}` : "—"}</td>
                        <td className="font-chart">{v.temp_c ?? "—"}</td>
                        <td className="font-chart">{v.pulse ?? "—"}</td>
                        <td className="font-chart">{v.spo2 ?? "—"}</td>
                        <td className="font-chart">{v.weight_kg ?? "—"}</td>
                        <td className="text-xs text-[var(--ink)]/60">{v.recorded_by}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>}
        </Card>
      )}
    </Shell>
  );
}
