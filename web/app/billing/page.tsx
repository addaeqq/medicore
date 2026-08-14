"use client";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import Shell from "@/components/Shell";
import PatientPicker, { PatientRow } from "@/components/PatientPicker";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtMoney, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, Empty, ErrorNote, PageTitle, PatientBand } from "@/components/ui";

type InvoiceRow = { invoice_id: string; status: string; visit_ref: string | null; created_at: string; total: number; paid: number };

export default function Billing() {
  const { profile, loading } = useMe();
  const [target, setTarget] = useState<PatientRow | null>(null);
  const [rows, setRows] = useState<InvoiceRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isPatient = profile?.user.role === "patient";
  const isClerk = profile?.user.role === "billing_clerk";
  const patientId = isPatient ? profile?.user.patientId : target?.patient_id;

  const refresh = useCallback(() => {
    if (!patientId) { setRows(null); return; }
    api.get<{ invoices: InvoiceRow[] }>(`/api/patients/${patientId}/invoices`)
      .then(d => setRows(d.invoices))
      .catch(e => setError(e instanceof ApiError ? e.message : "Could not load invoices"));
  }, [patientId]);
  useEffect(() => { refresh(); }, [refresh]);

  if (loading || !profile) return null;

  async function createInvoice() {
    if (!patientId) return;
    setError(null);
    try {
      await api.post("/api/invoices", { patientId, visitRef: `VISIT-${new Date().toISOString().slice(0, 10)}` });
      refresh();
    } catch (err) { setError(err instanceof ApiError ? err.message : "Could not create the invoice"); }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Billing">{isPatient ? "My bills" : "Billing workspace"}</PageTitle>
      {!isPatient && (
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
        ? (!isPatient ? <Empty>Search for a patient to see and raise their invoices.</Empty> : null)
        : rows === null ? null
        : <>
            {isClerk && <div className="mb-4"><Button onClick={createInvoice}>New invoice for this patient</Button></div>}
            {rows.length === 0
              ? <Empty>No invoices for this patient yet.</Empty>
              : <div className="space-y-3">
                  {rows.map(r => (
                    <Link key={r.invoice_id} href={`/invoices/${r.invoice_id}`} className="block">
                      <Card className="flex flex-wrap items-center gap-3 hover:border-[var(--theatre)] transition-colors">
                        <span className="font-chart text-xs text-[var(--ink)]/50">{r.invoice_id.slice(0, 8)}</span>
                        <span className="text-sm">{r.visit_ref ?? "Visit"}</span>
                        <span className="text-xs text-[var(--ink)]/50">{fmtWhen(r.created_at)}</span>
                        <span className="ml-auto font-chart text-sm">{fmtMoney(r.total)} <span className="text-[var(--ink)]/40">/ {fmtMoney(r.paid)} paid</span></span>
                        <Badge status={r.status} />
                      </Card>
                    </Link>
                  ))}
                </div>}
          </>}
    </Shell>
  );
}
