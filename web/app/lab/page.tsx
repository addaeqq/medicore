"use client";
import { useCallback, useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtWhen } from "@/lib/api";
import { Badge, Button, Card, Empty, ErrorNote, Field, Input, PageTitle, PatientBand } from "@/components/ui";

type Order = {
  lab_order_id: string; status: string; created_at: string;
  patient: string; mrn: string; ordered_by: string;
  test_count: number; results_in: number;
};
type Released = { lab_order_id: string; patient: string; mrn: string; test_count: number; released_at: string | null };
type Item = {
  order_item_id: string; name: string; specimen: string; tat_hours: number | null;
  result_value: string | null; ref_range: string | null; released_at: string | null; entered_by: string | null;
};
type Detail = {
  labOrderId: string; status: string; nextStatus: string;
  patient: { full_name: string; mrn: string; dob: string; sex: string };
  items: Item[];
};

const STEP_LABEL: Record<string, string> = {
  sample_collected: "Mark sample collected",
  in_progress: "Start processing",
  result_entered: "Mark results complete",
};

export default function LabPage() {
  const { profile, loading } = useMe();
  const [orders, setOrders] = useState<Order[]>([]);
  const [released, setReleased] = useState<Released[]>([]);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [draft, setDraft] = useState<Record<string, { result: string; range: string }>>({});
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.get<{ orders: Order[]; released: Released[] }>("/api/lab/orders")
      .then(d => { setOrders(d.orders); setReleased(d.released); })
      .catch(e => setError(e instanceof ApiError ? e.message : "Could not load the worklist"));
  }, []);
  useEffect(() => { if (profile) refresh(); }, [profile, refresh]);

  if (loading || !profile) return null;

  async function open(id: string) {
    setError(null); setNote(null);
    const d = await api.get<Detail>(`/api/lab/orders/${id}`);
    setDetail(d);
    setDraft(Object.fromEntries(d.items.map(i => [i.order_item_id, { result: i.result_value ?? "", range: i.ref_range ?? "" }])));
  }

  async function run(fn: () => Promise<unknown>, failure: string, ok?: string) {
    setBusy(true); setError(null); setNote(null);
    try {
      await fn();
      if (detail) await open(detail.labOrderId);
      refresh();
      if (ok) setNote(ok);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : failure);
    } finally { setBusy(false); }
  }

  const saveResult = (item: Item) => run(
    () => api.post(`/api/lab/items/${item.order_item_id}/result`, {
      resultValue: draft[item.order_item_id]?.result?.trim(),
      refRange: draft[item.order_item_id]?.range?.trim() || null,
    }), "Could not save the result", `${item.name} recorded.`);

  const advance = (to: string) => run(
    () => api.post(`/api/lab/orders/${detail!.labOrderId}/advance`, { status: to }),
    "Could not advance this order", `Order moved to ${to.replace(/_/g, " ")}.`);

  const canEnter = detail?.status === "in_progress" || detail?.status === "result_entered";
  const answered = detail?.items.filter(i => i.result_value).length ?? 0;

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Laboratory">Bench worklist</PageTitle>
      <ErrorNote message={error} />
      {note && <p className="text-sm text-[var(--theatre)] border border-[var(--theatre)]/40 bg-emerald-50/40 rounded-sm px-3 py-2 mb-3">{note}</p>}

      <div className="grid lg:grid-cols-2 gap-4">
        <section>
          <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">In the laboratory</h2>
          {orders.length === 0 && <Empty>Nothing on the bench. New requests appear as doctors order tests.</Empty>}
          <div className="space-y-3">
            {orders.map(o => (
              <Card key={o.lab_order_id} className="flex flex-wrap items-center gap-3">
                <span className="font-chart text-xs text-[var(--theatre)]">{o.mrn}</span>
                <span className="text-sm">{o.patient}</span>
                <span className="text-xs text-[var(--ink)]/50">
                  {o.results_in}/{o.test_count} results · requested by {o.ordered_by} · {fmtWhen(o.created_at)}
                </span>
                <span className="ml-auto"><Badge status={o.status} /></span>
                <Button kind="quiet" onClick={() => open(o.lab_order_id)}>Open</Button>
              </Card>
            ))}
          </div>

          {released.length > 0 && (
            <>
              <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mt-6 mb-3">Recently released to the patient</h2>
              <Card>
                <ul className="space-y-1 text-sm">
                  {released.map(r => (
                    <li key={r.lab_order_id} className="flex items-baseline gap-2">
                      <span className="font-chart text-xs text-[var(--theatre)]">{r.mrn}</span>
                      <span>{r.patient}</span>
                      <span className="text-xs text-[var(--ink)]/50">{r.test_count} test(s)</span>
                      <span className="ml-auto text-xs text-[var(--ink)]/50">{fmtWhen(r.released_at)}</span>
                    </li>
                  ))}
                </ul>
              </Card>
            </>
          )}
        </section>

        <section>
          {!detail
            ? <Empty>Open a request to collect the sample and enter results.</Empty>
            : <Card className="border-[var(--theatre)]">
                <PatientBand name={detail.patient.full_name} mrn={detail.patient.mrn}
                  dob={detail.patient.dob} sex={detail.patient.sex} />
                <div className="flex items-center gap-3 mb-3">
                  <Badge status={detail.status} />
                  <span className="text-xs text-[var(--ink)]/60">{answered} of {detail.items.length} tests answered</span>
                </div>

                <table className="w-full text-sm">
                  <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--ink)]/50">
                    <th className="py-1">Test</th><th className="w-40">Result</th><th className="w-32">Reference</th><th />
                  </tr></thead>
                  <tbody>
                    {detail.items.map(i => (
                      <tr key={i.order_item_id} className="border-t border-[var(--hairline)] align-top">
                        <td className="py-2">
                          {i.name}
                          <span className="block text-xs text-[var(--ink)]/50">
                            {i.specimen}{i.tat_hours ? ` · ${i.tat_hours}h turnaround` : ""}
                            {i.entered_by ? ` · entered by ${i.entered_by}` : ""}
                          </span>
                        </td>
                        <td className="py-2">
                          {canEnter
                            ? <Input value={draft[i.order_item_id]?.result ?? ""} placeholder="value"
                                onChange={e => setDraft(d => ({ ...d, [i.order_item_id]: { ...d[i.order_item_id], result: e.target.value } }))} />
                            : <span className="font-chart">{i.result_value ?? "—"}</span>}
                        </td>
                        <td className="py-2">
                          {canEnter
                            ? <Input value={draft[i.order_item_id]?.range ?? ""} placeholder="range"
                                onChange={e => setDraft(d => ({ ...d, [i.order_item_id]: { ...d[i.order_item_id], range: e.target.value } }))} />
                            : <span className="text-xs text-[var(--ink)]/60">{i.ref_range ?? "—"}</span>}
                        </td>
                        <td className="py-2">
                          {canEnter && (
                            <Button kind="quiet" disabled={busy || !draft[i.order_item_id]?.result?.trim()}
                              onClick={() => saveResult(i)}>Save</Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <div className="flex flex-wrap gap-2 mt-4">
                  {detail.nextStatus && STEP_LABEL[detail.nextStatus] && (
                    <Button onClick={() => advance(detail.nextStatus)} disabled={busy}>
                      {STEP_LABEL[detail.nextStatus]}
                    </Button>
                  )}
                  <Button kind="quiet" onClick={() => setDetail(null)}>Close</Button>
                </div>
                <p className="text-xs text-[var(--ink)]/50 mt-3">
                  {detail.status === "result_entered"
                    ? "Results are complete. The ordering doctor releases them — only then can the patient see the values (FR-LAB-05)."
                    : "An order moves one step at a time: sample collected, in progress, results complete."}
                </p>
              </Card>}
        </section>
      </div>
    </Shell>
  );
}
