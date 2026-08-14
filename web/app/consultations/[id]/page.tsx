"use client";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, ErrorNote, Field, PageTitle, PatientBand } from "@/components/ui";

type Consult = {
  consultation_id: string; patient_id: string; complaint: string | null; findings: string | null;
  diagnosis: string | null; signed_at: string | null; created_at: string; doctor: string;
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

  const refresh = useCallback(() => {
    api.get<Consult>(`/api/consultations/${id}`).then(d => {
      setC(d);
      setNotes({ complaint: d.complaint ?? "", findings: d.findings ?? "", diagnosis: d.diagnosis ?? "" });
    }).catch(e => setError(e instanceof ApiError ? e.message : "Could not load the consultation"));
  }, [id]);
  useEffect(() => { refresh(); }, [refresh]);

  if (loading || !profile) return null;
  const isDoctor = profile.user.role === "doctor";
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
            <Link className="text-[var(--theatre)] underline" href={`/patients/${c.patient_id}/emr`}>Open full record</Link>
          </p>
        </>
      )}
    </Shell>
  );
}
