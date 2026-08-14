"use client";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtMoney, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, ErrorNote, Field, Input, PageTitle, Select } from "@/components/ui";

type Detail = {
  invoiceId: string; patientId: string; status: string;
  items: { item_id: string; source_type: string; description: string; amount: number; posted_at: string }[];
  payments: { payment_id: string; method: string; amount: number; status: string; paid_at: string | null }[];
  total: number; paid: number; balance: number;
};
type Billables = {
  consultations: { consultation_id: string; signed_at: string; department: string; consult_fee: number; doctor: string }[];
  prescriptions: { prescription_id: string; status: string; dispensed_value: number }[];
};

export default function InvoicePage() {
  const { profile, loading } = useMe();
  const { id } = useParams<{ id: string }>();
  const [d, setD] = useState<Detail | null>(null);
  const [billables, setBillables] = useState<Billables | null>(null);
  const [manual, setManual] = useState({ description: "", amount: "" });
  const [payment, setPayment] = useState({ method: "cash", amount: "" });
  const [voidReason, setVoidReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.get<Detail>(`/api/invoices/${id}`).then(det => {
      setD(det);
      if (profile?.user.role === "billing_clerk") {
        api.get<Billables>(`/api/patients/${det.patientId}/billables`).then(setBillables).catch(() => null);
      }
    }).catch(e => setError(e instanceof ApiError ? e.message : "Could not load the invoice"));
  }, [id, profile]);
  useEffect(() => { if (profile) refresh(); }, [profile, refresh]);

  if (loading || !profile) return null;
  const role = profile.user.role;
  const isClerk = role === "billing_clerk";
  const isPatient = role === "patient";
  const isManagement = role === "management";
  const live = d && (d.status === "draft" || d.status === "issued" || d.status === "partially_paid");

  async function run(fn: () => Promise<unknown>, failMessage: string) {
    setBusy(true); setError(null);
    try { await fn(); refresh(); }
    catch (err) { setError(err instanceof ApiError ? err.message : failMessage); }
    finally { setBusy(false); }
  }

  async function payOnline() {
    setBusy(true); setError(null);
    try {
      const out = await api.post<{ paymentId: string; redirectUrl: string }>("/api/payments/init", { invoiceId: id });
      sessionStorage.setItem("medicore.lastPaymentId", out.paymentId);
      if (out.redirectUrl) window.location.href = out.redirectUrl;
      else { setError("The payment gateway did not return a checkout link."); setBusy(false); }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not start the payment");
      setBusy(false);
    }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Billing">Invoice</PageTitle>
      <ErrorNote message={error} />
      {d && (
        <div className="grid lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 space-y-4">
            <Card>
              <div className="flex items-center gap-3 mb-3">
                <span className="font-chart text-xs text-[var(--ink)]/50">{d.invoiceId}</span>
                <span className="ml-auto"><Badge status={d.status} /></span>
              </div>
              <table className="w-full text-sm">
                <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--ink)]/50">
                  <th className="py-1">Item</th><th>Source</th><th className="text-right">Amount</th></tr></thead>
                <tbody>
                  {d.items.map(i => (
                    <tr key={i.item_id} className="border-t border-[var(--hairline)]">
                      <td className="py-2">{i.description}<span className="block text-xs text-[var(--ink)]/40">{fmtWhen(i.posted_at)}</span></td>
                      <td className="text-xs text-[var(--ink)]/60">{i.source_type.replace(/_/g, " ")}</td>
                      <td className="text-right font-chart">{fmtMoney(i.amount)}</td>
                    </tr>
                  ))}
                  {d.items.length === 0 && <tr><td colSpan={3} className="py-4 text-center text-[var(--ink)]/50">No charges posted yet.</td></tr>}
                </tbody>
                <tfoot className="border-t-2 border-[var(--ink)]">
                  <tr><td className="py-2" colSpan={2}>Total</td><td className="text-right font-chart">{fmtMoney(d.total)}</td></tr>
                  <tr><td colSpan={2}>Paid</td><td className="text-right font-chart">{fmtMoney(d.paid)}</td></tr>
                  <tr className="font-medium"><td className="py-1" colSpan={2}>Balance due</td><td className="text-right font-chart">{fmtMoney(d.balance)}</td></tr>
                </tfoot>
              </table>
            </Card>
            {d.payments.length > 0 && (
              <Card>
                <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-2">Payments</h2>
                <ul className="space-y-1 text-sm">
                  {d.payments.map(p => (
                    <li key={p.payment_id} className="flex items-center gap-3">
                      <span className="uppercase text-xs text-[var(--ink)]/60">{p.method}</span>
                      <span className="font-chart">{fmtMoney(p.amount)}</span>
                      <span className="text-xs text-[var(--ink)]/40">{p.paid_at ? fmtWhen(p.paid_at) : "—"}</span>
                      <span className="ml-auto"><Badge status={p.status} /></span>
                    </li>
                  ))}
                </ul>
              </Card>
            )}
          </div>
          <div className="space-y-4">
            {isPatient && (d.status === "issued" || d.status === "partially_paid") && Number(d.balance) > 0 && (
              <Card className="border-[var(--theatre)]">
                <h2 className="font-display text-lg mb-1">Pay online</h2>
                <p className="text-sm text-[var(--ink)]/60 mb-3">Mobile money or card via ITC Transflow. You&apos;ll be taken to a secure checkout page for {fmtMoney(d.balance)}.</p>
                <Button onClick={payOnline} disabled={busy}>{busy ? "Opening checkout…" : `Pay ${fmtMoney(d.balance)}`}</Button>
              </Card>
            )}
            {isClerk && live && (
              <>
                {billables && (billables.consultations.length > 0 || billables.prescriptions.length > 0) && (
                  <Card>
                    <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-2">Post charges</h2>
                    <ul className="space-y-2 text-sm">
                      {billables.consultations.map(c => (
                        <li key={c.consultation_id} className="flex items-center gap-2">
                          <span>Consultation · {c.department}</span>
                          <span className="font-chart ml-auto">{fmtMoney(c.consult_fee)}</span>
                          <Button kind="quiet" onClick={() => run(
                            () => api.post(`/api/invoices/${id}/charges/consultation`, { consultationId: c.consultation_id }),
                            "Charge failed")}>Post</Button>
                        </li>
                      ))}
                      {billables.prescriptions.map(p => (
                        <li key={p.prescription_id} className="flex items-center gap-2">
                          <span>Pharmacy · dispensed</span>
                          <span className="font-chart ml-auto">{fmtMoney(p.dispensed_value)}</span>
                          <Button kind="quiet" onClick={() => run(
                            () => api.post(`/api/invoices/${id}/charges/prescription`, { prescriptionId: p.prescription_id }),
                            "Charge failed")}>Post</Button>
                        </li>
                      ))}
                    </ul>
                  </Card>
                )}
                <Card>
                  <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-2">Add a line item</h2>
                  <Field label="Description"><Input value={manual.description} onChange={e => setManual(m => ({ ...m, description: e.target.value }))} /></Field>
                  <Field label="Amount (GHS)"><Input type="number" min={0} step="0.01" value={manual.amount} onChange={e => setManual(m => ({ ...m, amount: e.target.value }))} /></Field>
                  <Button kind="quiet" disabled={busy || !manual.description || !manual.amount}
                    onClick={() => run(async () => {
                      await api.post(`/api/invoices/${id}/items`, { sourceType: "other", description: manual.description, amount: parseFloat(manual.amount) });
                      setManual({ description: "", amount: "" });
                    }, "Could not add the item")}>Add item</Button>
                </Card>
                {d.status === "draft" && (
                  <Card>
                    <Button disabled={busy || Number(d.total) <= 0} onClick={() => run(() => api.post(`/api/invoices/${id}/issue`), "Issue failed")}>
                      Issue invoice
                    </Button>
                    {Number(d.total) <= 0 && <p className="text-xs text-[var(--ink)]/50 mt-2">Post at least one charge first.</p>}
                  </Card>
                )}
                {d.status !== "draft" && (
                  <Card>
                    <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-2">Record payment (cash point)</h2>
                    <div className="grid grid-cols-2 gap-3">
                      <Field label="Method">
                        <Select value={payment.method} onChange={e => setPayment(p => ({ ...p, method: e.target.value }))}>
                          <option value="cash">Cash</option><option value="pos">POS</option>
                        </Select>
                      </Field>
                      <Field label="Amount (GHS)"><Input type="number" min={0.01} step="0.01" value={payment.amount} onChange={e => setPayment(p => ({ ...p, amount: e.target.value }))} /></Field>
                    </div>
                    <Button disabled={busy || !payment.amount}
                      onClick={() => run(async () => {
                        await api.post(`/api/invoices/${id}/payments`, { method: payment.method, amount: parseFloat(payment.amount) });
                        setPayment({ method: "cash", amount: "" });
                      }, "Payment failed")}>Record payment</Button>
                  </Card>
                )}
              </>
            )}
            {isManagement && d.status !== "void" && d.status !== "paid" && (
              <Card>
                <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-2">Void invoice</h2>
                <Field label="Reason (recorded in the audit trail)"><Input value={voidReason} onChange={e => setVoidReason(e.target.value)} /></Field>
                <Button kind="danger" disabled={busy || !voidReason.trim()}
                  onClick={() => run(() => api.post(`/api/invoices/${id}/void`, { reason: voidReason }), "Void failed")}>
                  Void with reason
                </Button>
              </Card>
            )}
          </div>
        </div>
      )}
    </Shell>
  );
}
