"use client";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, ErrorNote, Field, Input, PageTitle, Select } from "@/components/ui";

type Emr = {
  consultations: { consultation_id: string; complaint: string; findings: string; diagnosis: string; signed_at: string | null; created_at: string; doctor: string }[];
  addendums: { addendum_id: string; consultation_id: string; body: string; created_at: string; author: string }[];
  allergies: { substance: string; severity: string }[];
  prescriptions: { prescription_id: string; status: string; created_at: string; generic_name: string; dose: string; frequency: string; quantity: number }[];
};

export default function EmrPage() {
  const { profile, loading } = useMe();
  const { id } = useParams<{ id: string }>();
  const [emr, setEmr] = useState<Emr | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [allergy, setAllergy] = useState({ substance: "", severity: "moderate" });

  const refresh = useCallback(() => {
    api.get<Emr>(`/api/patients/${id}/emr`).then(setEmr)
      .catch(e => setError(e instanceof ApiError && e.status === 403
        ? "You don't have access to this record. Access follows the active-care rule."
        : "Could not load the record"));
  }, [id]);
  useEffect(() => { refresh(); }, [refresh]);

  if (loading || !profile) return null;
  const isDoctor = profile.user.role === "doctor";

  async function addAllergy() {
    try { await api.post(`/api/patients/${id}/allergies`, allergy); setAllergy({ substance: "", severity: "moderate" }); refresh(); }
    catch (err) { setError(err instanceof ApiError ? err.message : "Could not record the allergy"); }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Clinical record">Medical record</PageTitle>
      <ErrorNote message={error} />
      {emr && (
        <div className="grid lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 space-y-4">
            <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Consultations</h2>
            {emr.consultations.length === 0 && <Card><p className="text-sm text-[var(--ink)]/60">No consultations recorded yet.</p></Card>}
            {emr.consultations.map(c => (
              <Card key={c.consultation_id}>
                <div className="flex items-center gap-3 mb-2">
                  <span className="text-sm">{c.doctor}</span>
                  <span className="text-xs text-[var(--ink)]/50">{fmtWhen(c.created_at)}</span>
                  <span className="ml-auto"><Badge status={c.signed_at ? "signed" : "open"} /></span>
                </div>
                <p className="text-sm"><span className="text-[var(--ink)]/50">Dx:</span> {c.diagnosis || "—"}</p>
                {c.complaint && <p className="text-sm text-[var(--ink)]/70 mt-1">{c.complaint}</p>}
                {emr.addendums.filter(a => a.consultation_id === c.consultation_id).map(a => (
                  <p key={a.addendum_id} className="text-sm border-l-2 border-[var(--theatre)] pl-3 mt-2">
                    {a.body} <span className="text-xs text-[var(--ink)]/50">— {a.author}</span>
                  </p>
                ))}
              </Card>
            ))}
          </div>
          <div className="space-y-4">
            <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Allergies</h2>
            <Card>
              {emr.allergies.length === 0 && <p className="text-sm text-[var(--ink)]/60 mb-2">No known allergies recorded.</p>}
              <ul className="space-y-1 mb-3">
                {emr.allergies.map(a => (
                  <li key={a.substance} className="text-sm flex justify-between">
                    <span>{a.substance}</span><Badge status={a.severity} />
                  </li>
                ))}
              </ul>
              {isDoctor && (
                <>
                  <Field label="Substance"><Input value={allergy.substance} onChange={e => setAllergy(v => ({ ...v, substance: e.target.value }))} /></Field>
                  <Field label="Severity">
                    <Select value={allergy.severity} onChange={e => setAllergy(v => ({ ...v, severity: e.target.value }))}>
                      <option value="mild">Mild</option><option value="moderate">Moderate</option><option value="severe">Severe</option>
                    </Select>
                  </Field>
                  <Button onClick={addAllergy} disabled={!allergy.substance.trim()}>Record allergy</Button>
                </>
              )}
            </Card>
            <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Prescriptions</h2>
            <Card>
              {emr.prescriptions.length === 0 && <p className="text-sm text-[var(--ink)]/60">None yet.</p>}
              <ul className="space-y-2">
                {emr.prescriptions.map((p, i) => (
                  <li key={`${p.prescription_id}-${i}`} className="text-sm flex items-baseline gap-2">
                    <span>{p.generic_name}</span>
                    <span className="text-xs text-[var(--ink)]/50">{p.dose} · {p.frequency} · x{p.quantity}</span>
                    <span className="ml-auto"><Badge status={p.status} /></span>
                  </li>
                ))}
              </ul>
            </Card>
          </div>
        </div>
      )}
    </Shell>
  );
}
