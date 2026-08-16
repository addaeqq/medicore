"use client";
import { useCallback, useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtMoney, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, Empty, ErrorNote, Field, Input, PageTitle, Select } from "@/components/ui";

type WorkRow = { prescription_id: string; status: string; created_at: string; patient: string; mrn: string; item_count: number };
type Detail = {
  prescriptionId: string; status: string;
  items: { rx_item_id: string; dose: string; frequency: string; quantity: number; generic_name: string; strength: string; remaining: number }[];
  allergies: { substance: string; severity: string }[];
};
type DrugRow = { drug_id: string; generic_name: string; strength: string; form: string; unit_price: number; reorder_level: number; total_on_hand: number };

export default function Pharmacy() {
  const { profile, loading } = useMe();
  const [work, setWork] = useState<WorkRow[]>([]);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [qty, setQty] = useState<Record<string, string>>({});
  const [drugs, setDrugs] = useState<DrugRow[]>([]);
  const [batch, setBatch] = useState({ drugId: "", batchNo: "", expiryDate: "", qty: "" });
  const [drug, setDrug] = useState({ genericName: "", brandName: "", form: "tablet", strength: "", unitPrice: "", reorderLevel: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.get<{ prescriptions: WorkRow[] }>("/api/prescriptions/open").then(d => setWork(d.prescriptions));
    api.get<{ drugs: DrugRow[] }>("/api/inventory/drugs").then(d => setDrugs(d.drugs));
  }, []);
  useEffect(() => { if (profile) refresh(); }, [profile, refresh]);

  if (loading || !profile) return null;

  async function open(id: string) {
    setError(null);
    const d = await api.get<Detail>(`/api/prescriptions/${id}`);
    setDetail(d);
    setQty(Object.fromEntries(d.items.map(i => [i.rx_item_id, String(i.remaining)])));
  }

  async function dispense() {
    if (!detail) return;
    setBusy(true); setError(null);
    try {
      const items = detail.items
        .map(i => ({ rxItemId: i.rx_item_id, qty: parseInt(qty[i.rx_item_id] || "0", 10) }))
        .filter(i => i.qty > 0);
      await api.post(`/api/prescriptions/${detail.prescriptionId}/dispense`, { items });
      setDetail(null); refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dispense failed");
    } finally { setBusy(false); }
  }

  /** FR-PHM-04: add a drug to the formulary, so stock can be received against it and
   *  doctors can prescribe it — prescribing is formulary-only by design (drug_id is an FK,
   *  and FEFO dispensing, stock decrement and billing all key off the drug row). */
  async function addDrug() {
    setBusy(true); setError(null);
    try {
      const out = await api.post<{ drugId: string }>("/api/inventory/drugs", {
        genericName: drug.genericName.trim(),
        brandName: drug.brandName.trim() || null,
        form: drug.form.trim(),
        strength: drug.strength.trim(),
        unitPrice: parseFloat(drug.unitPrice),
        // left blank means "use the house default" (10), not "never warn" (0)
        reorderLevel: drug.reorderLevel === "" ? null : parseInt(drug.reorderLevel, 10),
      });
      setDrug({ genericName: "", brandName: "", form: "tablet", strength: "", unitPrice: "", reorderLevel: "" });
      refresh();
      setBatch(b => ({ ...b, drugId: out.drugId })); // pre-select it for the first delivery
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not add the drug");
    } finally { setBusy(false); }
  }

  async function receive() {
    setBusy(true); setError(null);
    try {
      await api.post("/api/inventory/batches", {
        drugId: batch.drugId, batchNo: batch.batchNo, expiryDate: batch.expiryDate, qty: parseInt(batch.qty, 10),
      });
      setBatch({ drugId: "", batchNo: "", expiryDate: "", qty: "" }); refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not receive stock");
    } finally { setBusy(false); }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Pharmacy">Dispensing & stock</PageTitle>
      <ErrorNote message={error} />
      <div className="grid lg:grid-cols-2 gap-4">
        <section>
          <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">Worklist</h2>
          {work.length === 0 && <Empty>No open prescriptions. New ones appear as doctors sign them.</Empty>}
          <div className="space-y-3">
            {work.map(w => (
              <Card key={w.prescription_id} className="flex items-center gap-3">
                <span className="font-chart text-xs text-[var(--theatre)]">{w.mrn}</span>
                <span className="text-sm">{w.patient}</span>
                <span className="text-xs text-[var(--ink)]/50">{w.item_count} item(s) · {fmtWhen(w.created_at)}</span>
                <span className="ml-auto"><Badge status={w.status} /></span>
                <Button kind="quiet" onClick={() => open(w.prescription_id)}>Open</Button>
              </Card>
            ))}
          </div>
          {detail && (
            <Card className="mt-4 border-[var(--theatre)]">
              <h3 className="font-display text-lg mb-1">Dispense</h3>
              {detail.allergies.length > 0 && (
                <p className="text-sm text-[var(--triage)] mb-3">
                  Allergies on file: {detail.allergies.map(a => `${a.substance} (${a.severity})`).join(", ")}
                </p>
              )}
              <table className="w-full text-sm">
                <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--ink)]/50">
                  <th className="py-1">Drug</th><th>Remaining</th><th className="w-24">Qty now</th></tr></thead>
                <tbody>
                  {detail.items.map(i => (
                    <tr key={i.rx_item_id} className="border-t border-[var(--hairline)]">
                      <td className="py-2">{i.generic_name} {i.strength} <span className="text-xs text-[var(--ink)]/50">{i.dose} · {i.frequency}</span></td>
                      <td className="font-chart">{i.remaining}</td>
                      <td><Input type="number" min={0} max={i.remaining} value={qty[i.rx_item_id] ?? ""}
                        onChange={e => setQty(q => ({ ...q, [i.rx_item_id]: e.target.value }))} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="text-xs text-[var(--ink)]/50 my-2">Batches are drawn earliest-expiry-first automatically.</p>
              <div className="flex gap-2">
                <Button onClick={dispense} disabled={busy}>{busy ? "Dispensing…" : "Dispense"}</Button>
                <Button kind="quiet" onClick={() => setDetail(null)}>Close</Button>
              </div>
            </Card>
          )}
        </section>
        <section>
          <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">Stock on hand</h2>
          <Card>
            <table className="w-full text-sm">
              <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--ink)]/50">
                <th className="py-1">Drug</th><th>Price</th><th>On hand</th></tr></thead>
              <tbody>
                {drugs.map(d => (
                  <tr key={d.drug_id} className="border-t border-[var(--hairline)]">
                    <td className="py-2">{d.generic_name} {d.strength}</td>
                    <td className="font-chart">{fmtMoney(d.unit_price)}</td>
                    <td className={`font-chart ${Number(d.total_on_hand) <= d.reorder_level ? "text-[var(--triage)]" : ""}`}>
                      {String(d.total_on_hand)}{Number(d.total_on_hand) <= d.reorder_level ? " · reorder" : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
          <Card className="mt-4">
            <h3 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">Add a drug to the formulary</h3>
            <p className="text-xs text-[var(--ink)]/50 mb-3">
              A drug must exist here before stock can be received against it or a doctor can prescribe it.
            </p>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Generic name"><Input value={drug.genericName} onChange={e => setDrug(d => ({ ...d, genericName: e.target.value }))} /></Field>
              <Field label="Brand name (optional)"><Input value={drug.brandName} onChange={e => setDrug(d => ({ ...d, brandName: e.target.value }))} /></Field>
              <Field label="Form">
                <Select value={drug.form} onChange={e => setDrug(d => ({ ...d, form: e.target.value }))}>
                  <option value="tablet">Tablet</option>
                  <option value="capsule">Capsule</option>
                  <option value="syrup">Syrup</option>
                  <option value="suspension">Suspension</option>
                  <option value="injection">Injection</option>
                  <option value="cream">Cream</option>
                  <option value="drops">Drops</option>
                </Select>
              </Field>
              <Field label="Strength"><Input value={drug.strength} onChange={e => setDrug(d => ({ ...d, strength: e.target.value }))} /></Field>
              <Field label="Unit price (GHS)"><Input type="number" min={0} step="0.01" value={drug.unitPrice} onChange={e => setDrug(d => ({ ...d, unitPrice: e.target.value }))} /></Field>
              <Field label="Reorder level"><Input type="number" min={0} value={drug.reorderLevel} onChange={e => setDrug(d => ({ ...d, reorderLevel: e.target.value }))} /></Field>
            </div>
            <Button onClick={addDrug}
              disabled={busy || !drug.genericName.trim() || !drug.strength.trim() || drug.unitPrice === ""}>
              Add drug
            </Button>
          </Card>
          <Card className="mt-4">
            <h3 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">Receive stock</h3>
            <Field label="Drug">
              <select value={batch.drugId} onChange={e => setBatch(b => ({ ...b, drugId: e.target.value }))}
                className="w-full border border-[var(--hairline)] rounded-sm px-3 py-2 text-sm bg-white focus:border-[var(--theatre)] outline-none">
                <option value="">Choose…</option>
                {drugs.map(d => <option key={d.drug_id} value={d.drug_id}>{d.generic_name} {d.strength}</option>)}
              </select>
            </Field>
            <div className="grid grid-cols-3 gap-3">
              <Field label="Batch no."><Input value={batch.batchNo} onChange={e => setBatch(b => ({ ...b, batchNo: e.target.value }))} /></Field>
              <Field label="Expiry"><Input type="date" value={batch.expiryDate} onChange={e => setBatch(b => ({ ...b, expiryDate: e.target.value }))} /></Field>
              <Field label="Quantity"><Input type="number" min={1} value={batch.qty} onChange={e => setBatch(b => ({ ...b, qty: e.target.value }))} /></Field>
            </div>
            <Button onClick={receive} disabled={busy || !batch.drugId || !batch.batchNo || !batch.expiryDate || !batch.qty}>Receive batch</Button>
          </Card>
        </section>
      </div>
    </Shell>
  );
}
