"use client";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtMoney, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, ErrorNote, Field, Input, PageTitle, PatientBand, Select } from "@/components/ui";

type Consult = {
  consultation_id: string; patient_id: string; complaint: string | null; findings: string | null;
  diagnosis: string | null; signed_at: string | null; created_at: string; doctor: string; doctor_id: string;
  patient_name: string; mrn: string; dob: string; sex: string;
  addendums: { addendum_id: string; body: string; created_at: string; author: string }[];
};

function TextArea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} rows={3}
    className="w-full border border-[var(--hairline)] rounded-sm px-3 py-2 text-sm bg-white focus:border-[var(--theatre)] outline-none" />;
}

export default function ConsultationPage() {
  const { profile, loading } = useMe();
  const { id } = useParams<{ id: string }>();
  const [c, setC] = useState<Consult | null>(null);
  const [notes, setNotes] = useState({ complaint: "", findings: "", diagnosis: "" });
  const [addendum, setAddendum] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  type Drug = { drug_id: string; generic_name: string; strength: string; form: string };
  type RxRow = { drugId: string; dose: string; frequency: string; quantity: string };
  const emptyRow: RxRow = { drugId: "", dose: "", frequency: "", quantity: "" };
  const [drugs, setDrugs] = useState<Drug[]>([]);
  const [rxRows, setRxRows] = useState<RxRow[]>([emptyRow]);
  const [rxSent, setRxSent] = useState<string | null>(null);
  type LabTest = { lab_test_id: string; name: string; specimen: string; price: number; tat_hours: number | null };
  const [labTests, setLabTests] = useState<LabTest[]>([]);
  const [labPicked, setLabPicked] = useState<string[]>([]);
  const [labSent, setLabSent] = useState<string | null>(null);

  const refresh = useCallback(() => {
    api.get<Consult>(`/api/consultations/${id}`).then(d => {
      setC(d);
      setNotes({ complaint: d.complaint ?? "", findings: d.findings ?? "", diagnosis: d.diagnosis ?? "" });
    }).catch(e => setError(e instanceof ApiError ? e.message : "Could not load the consultation"));
  }, [id]);
  useEffect(() => { refresh(); }, [refresh]);
  useEffect(() => {
    if (profile?.user.role === "doctor") {
      api.get<{ drugs: Drug[] }>("/api/inventory/drugs").then(d => setDrugs(d.drugs)).catch(() => null);
      api.get<{ tests: LabTest[] }>("/api/lab/tests").then(d => setLabTests(d.tests)).catch(() => null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profile]);

  if (loading || !profile) return null;
  const isDoctor = profile.user.role === "doctor";
  const isAuthor = isDoctor && !!c && profile.user.staffId === c.doctor_id;
  const rxValid = rxRows.some(r => r.drugId && r.dose.trim() && r.frequency.trim() && parseInt(r.quantity, 10) > 0);
  const signed = !!c?.signed_at;

  async function save() {
    setBusy(true); setError(null);
    try { await api.patch(`/api/consultations/${id}`, notes); refresh(); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Save failed"); }
    finally { setBusy(false); }
  }
  async function sign() {
    setBusy(true); setError(null);
    try { await api.patch(`/api/consultations/${id}`, notes); await api.post(`/api/consultations/${id}/sign`); refresh(); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Sign-off failed"); }
    finally { setBusy(false); }
  }
  async function sendPrescription() {
    setBusy(true); setError(null); setRxSent(null);
    try {
      const items = rxRows
        .filter(r => r.drugId && r.dose.trim() && r.frequency.trim() && parseInt(r.quantity, 10) > 0)
        .map(r => ({ drugId: r.drugId, dose: r.dose.trim(), frequency: r.frequency.trim(), quantity: parseInt(r.quantity, 10) }));
      await api.post("/api/prescriptions", { consultationId: id, items });
      setRxRows([emptyRow]);
      setRxSent(`Prescription sent to pharmacy (${items.length} item${items.length > 1 ? "s" : ""}).`);
    } catch (err) { setError(err instanceof ApiError ? err.message : "Could not send the prescription"); }
    finally { setBusy(false); }
  }

  async function sendLabRequest() {
    setBusy(true); setError(null); setLabSent(null);
    try {
      await api.post("/api/lab/orders", { consultationId: id, testIds: labPicked });
      setLabSent(`Request sent to the laboratory (${labPicked.length} test${labPicked.length > 1 ? "s" : ""}).`);
      setLabPicked([]);
    } catch (err) { setError(err instanceof ApiError ? err.message : "Could not send the laboratory request"); }
    finally { setBusy(false); }
  }

  async function addNote() {
    setBusy(true); setError(null);
    try { await api.post(`/api/consultations/${id}/addendums`, { body: addendum }); setAddendum(""); refresh(); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Addendum failed"); }
    finally { setBusy(false); }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Clinical record">Consultation</PageTitle>
      {c && (
        <>
          <PatientBand name={c.patient_name} mrn={c.mrn} dob={c.dob} sex={c.sex} />
          <ErrorNote message={error} />
          <Card className="mb-4">
            <div className="flex items-center gap-3 mb-4">
              <span className="text-sm text-[var(--ink)]/60">{c.doctor} · opened {fmtWhen(c.created_at)}</span>
              <span className="ml-auto"><Badge status={signed ? "signed" : "open"} /></span>
            </div>
            {signed ? (
              <dl className="space-y-3 text-sm">
                <div><dt className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Complaint</dt><dd>{c.complaint || "—"}</dd></div>
                <div><dt className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Findings</dt><dd>{c.findings || "—"}</dd></div>
                <div><dt className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Diagnosis</dt><dd>{c.diagnosis || "—"}</dd></div>
                <p className="text-xs text-[var(--ink)]/50 pt-2 border-t border-[var(--hairline)]">
                  Signed {fmtWhen(c.signed_at)}. Signed notes are locked; corrections go below as addendums.
                </p>
              </dl>
            ) : isDoctor ? (
              <>
                <Field label="Presenting complaint"><TextArea value={notes.complaint} onChange={e => setNotes(n => ({ ...n, complaint: e.target.value }))} /></Field>
                <Field label="Findings"><TextArea value={notes.findings} onChange={e => setNotes(n => ({ ...n, findings: e.target.value }))} /></Field>
                <Field label="Diagnosis (required to sign)"><TextArea value={notes.diagnosis} onChange={e => setNotes(n => ({ ...n, diagnosis: e.target.value }))} /></Field>
                <div className="flex gap-2 mt-2">
                  <Button kind="quiet" onClick={save} disabled={busy}>Save draft</Button>
                  <Button onClick={sign} disabled={busy || !notes.diagnosis.trim()}>Sign & lock</Button>
                </div>
              </>
            ) : <p className="text-sm text-[var(--ink)]/60">This consultation is still being written.</p>}
          </Card>
          {isAuthor && (
            <Card className="mb-4">
              <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-1">Prescription</h2>
              <p className="text-sm text-[var(--ink)]/60 mb-3">Sent items appear on the pharmacy worklist; batches are drawn earliest-expiry-first at dispensing.</p>
              {rxSent && <p className="text-sm text-[var(--theatre)] border border-[var(--theatre)]/40 bg-emerald-50/40 rounded-sm px-3 py-2 mb-3">{rxSent}</p>}
              {rxRows.map((r, i) => (
                <div key={i} className="grid grid-cols-2 sm:grid-cols-[2fr_1fr_1fr_0.7fr] gap-3 mb-1">
                  <Field label={i === 0 ? "Drug" : ""}>
                    <Select value={r.drugId} onChange={e => setRxRows(rows => rows.map((x, j) => j === i ? { ...x, drugId: e.target.value } : x))}>
                      <option value="">Choose…</option>
                      {drugs.map(d => <option key={d.drug_id} value={d.drug_id}>{d.generic_name} {d.strength} ({d.form})</option>)}
                    </Select>
                  </Field>
                  <Field label={i === 0 ? "Dose" : ""}>
                    <Input placeholder="500mg" value={r.dose} onChange={e => setRxRows(rows => rows.map((x, j) => j === i ? { ...x, dose: e.target.value } : x))} />
                  </Field>
                  <Field label={i === 0 ? "Frequency" : ""}>
                    <Input placeholder="3x daily, 5 days" value={r.frequency} onChange={e => setRxRows(rows => rows.map((x, j) => j === i ? { ...x, frequency: e.target.value } : x))} />
                  </Field>
                  <Field label={i === 0 ? "Qty" : ""}>
                    <Input type="number" min={1} value={r.quantity} onChange={e => setRxRows(rows => rows.map((x, j) => j === i ? { ...x, quantity: e.target.value } : x))} />
                  </Field>
                </div>
              ))}
              <div className="flex gap-2 mt-1">
                <Button kind="quiet" onClick={() => setRxRows(rows => [...rows, emptyRow])}>Add another item</Button>
                <Button onClick={sendPrescription} disabled={busy || !rxValid}>Send to pharmacy</Button>
              </div>
            </Card>
          )}
          {isAuthor && (
            <Card className="mb-4">
              <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-1">Laboratory request</h2>
              <p className="text-sm text-[var(--ink)]/60 mb-3">
                Requested tests appear on the laboratory bench. Results come back to you to release —
                the patient sees nothing until you do.
              </p>
              {labSent && <p className="text-sm text-[var(--theatre)] border border-[var(--theatre)]/40 bg-emerald-50/40 rounded-sm px-3 py-2 mb-3">{labSent}</p>}
              <div className="grid sm:grid-cols-2 gap-x-4 gap-y-1 mb-3">
                {labTests.map(t => (
                  <label key={t.lab_test_id} className="flex items-baseline gap-2 text-sm cursor-pointer">
                    <input type="checkbox" checked={labPicked.includes(t.lab_test_id)}
                      onChange={e => setLabPicked(p => e.target.checked ? [...p, t.lab_test_id] : p.filter(x => x !== t.lab_test_id))} />
                    <span>{t.name}</span>
                    <span className="ml-auto font-chart text-xs text-[var(--ink)]/50">{fmtMoney(t.price)}</span>
                  </label>
                ))}
              </div>
              <Button onClick={sendLabRequest} disabled={busy || labPicked.length === 0}>
                Send request to laboratory
              </Button>
            </Card>
          )}
          {c.addendums.length > 0 && (
            <Card className="mb-4">
              <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">Addendums</h2>
              <ul className="space-y-3">
                {c.addendums.map(a => (
                  <li key={a.addendum_id} className="text-sm border-l-2 border-[var(--theatre)] pl-3">
                    <p>{a.body}</p>
                    <p className="text-xs text-[var(--ink)]/50 mt-1">{a.author} · {fmtWhen(a.created_at)}</p>
                  </li>
                ))}
              </ul>
            </Card>
          )}
          {signed && isDoctor && (
            <Card>
              <Field label="Add an addendum (append-only)"><TextArea value={addendum} onChange={e => setAddendum(e.target.value)} /></Field>
              <Button onClick={addNote} disabled={busy || !addendum.trim()}>Add addendum</Button>
            </Card>
          )}
          <p className="mt-4 text-sm">
            {/* carry the origin so the record can offer a way back (the record is a
                read-only view; the consultation it was opened from stays editable) */}
            <Link className="text-[var(--theatre)] underline" href={`/patients/${c.patient_id}/emr?from=${id}`}>Open full record</Link>
          </p>
        </>
      )}
    </Shell>
  );
}
