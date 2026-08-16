"use client";
import { useCallback, useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, fmtMoney } from "@/lib/api";
import { Button, Card, Empty, ErrorNote, Field, Input, PageTitle, Select } from "@/components/ui";

type LabTest = { lab_test_id: string; name: string; specimen: string; price: number; tat_hours: number | null };
type Dept = { department_id: string; name: string; dept_type: string; consult_fee: number };

export default function Catalogues() {
  const { profile, loading } = useMe();
  const [tests, setTests] = useState<LabTest[]>([]);
  const [depts, setDepts] = useState<Dept[]>([]);
  const [test, setTest] = useState({ name: "", specimen: "blood", price: "", tatHours: "" });
  const [dept, setDept] = useState({ name: "", deptType: "clinical", consultFee: "" });
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.get<{ tests: LabTest[] }>("/api/lab/tests").then(d => setTests(d.tests)).catch(() => null);
    api.get<{ departments: Dept[] }>("/api/departments").then(d => setDepts(d.departments)).catch(() => null);
  }, []);
  useEffect(() => { if (profile) refresh(); }, [profile, refresh]);

  if (loading || !profile) return null;
  const isAdmin = profile.user.role === "sys_admin";

  async function run(fn: () => Promise<unknown>, done: string, failMessage: string) {
    setBusy(true); setError(null); setResult(null);
    try { await fn(); setResult(done); refresh(); }
    catch (err) { setError(err instanceof ApiError ? err.message : failMessage); }
    finally { setBusy(false); }
  }

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Administration">Reference catalogues</PageTitle>
      <ErrorNote message={error} />
      {result && <p className="text-sm text-[var(--theatre)] border border-[var(--theatre)]/40 bg-emerald-50/40 rounded-sm px-3 py-2 mb-4">{result}</p>}
      <div className="grid lg:grid-cols-2 gap-4">
        <section className="space-y-4">
          <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Laboratory tests</h2>
          <Card>
            {tests.length === 0 ? <Empty>No tests in the catalogue.</Empty> : (
              <table className="w-full text-sm">
                <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--ink)]/50">
                  <th className="py-1">Test</th><th>Specimen</th><th className="text-right">Price</th><th className="text-right">TAT</th></tr></thead>
                <tbody>
                  {tests.map(t => (
                    <tr key={t.lab_test_id} className="border-t border-[var(--hairline)]">
                      <td className="py-2">{t.name}</td>
                      <td className="text-xs text-[var(--ink)]/60">{t.specimen}</td>
                      <td className="text-right font-chart">{fmtMoney(t.price)}</td>
                      <td className="text-right font-chart text-xs">{t.tat_hours ? `${t.tat_hours}h` : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
          {isAdmin && (
            <Card>
              <h3 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">Add a test</h3>
              <p className="text-xs text-[var(--ink)]/50 mb-3">
                A test must exist here before a doctor can request it from the consultation screen.
              </p>
              <Field label="Name"><Input value={test.name} onChange={e => setTest(t => ({ ...t, name: e.target.value }))} /></Field>
              <div className="grid grid-cols-3 gap-3">
                <Field label="Specimen">
                  <Select value={test.specimen} onChange={e => setTest(t => ({ ...t, specimen: e.target.value }))}>
                    <option value="blood">Blood</option>
                    <option value="urine">Urine</option>
                    <option value="stool">Stool</option>
                    <option value="swab">Swab</option>
                    <option value="sputum">Sputum</option>
                    <option value="tissue">Tissue</option>
                  </Select>
                </Field>
                <Field label="Price (GHS)"><Input type="number" min={0} step="0.01" value={test.price} onChange={e => setTest(t => ({ ...t, price: e.target.value }))} /></Field>
                <Field label="Turnaround (h)"><Input type="number" min={1} max={720} value={test.tatHours} onChange={e => setTest(t => ({ ...t, tatHours: e.target.value }))} /></Field>
              </div>
              <Button disabled={busy || !test.name.trim() || test.price === ""}
                onClick={() => run(async () => {
                  await api.post("/api/admin/lab-tests", {
                    name: test.name.trim(), specimen: test.specimen,
                    price: parseFloat(test.price),
                    tatHours: test.tatHours === "" ? null : parseInt(test.tatHours, 10),
                  });
                  setTest({ name: "", specimen: "blood", price: "", tatHours: "" });
                }, "Test added to the catalogue.", "Could not add the test")}>
                Add test
              </Button>
            </Card>
          )}
        </section>

        <section className="space-y-4">
          <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60">Departments</h2>
          <Card>
            {depts.length === 0 ? <Empty>No departments.</Empty> : (
              <table className="w-full text-sm">
                <thead><tr className="text-left text-xs uppercase tracking-wider text-[var(--ink)]/50">
                  <th className="py-1">Department</th><th>Type</th><th className="text-right">Consult fee</th></tr></thead>
                <tbody>
                  {depts.map(d => (
                    <tr key={d.department_id} className="border-t border-[var(--hairline)]">
                      <td className="py-2">{d.name}</td>
                      <td className="text-xs text-[var(--ink)]/60">{d.dept_type}</td>
                      <td className="text-right font-chart">{fmtMoney(d.consult_fee)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
          {isAdmin && (
            <Card>
              <h3 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">Add a department</h3>
              <p className="text-xs text-[var(--ink)]/50 mb-3">
                The consult fee is what billing posts for a signed consultation by a doctor in this department.
              </p>
              <Field label="Name"><Input value={dept.name} onChange={e => setDept(d => ({ ...d, name: e.target.value }))} /></Field>
              <div className="grid grid-cols-2 gap-3">
                <Field label="Type">
                  <Select value={dept.deptType} onChange={e => setDept(d => ({ ...d, deptType: e.target.value }))}>
                    <option value="clinical">Clinical</option>
                    <option value="diagnostic">Diagnostic</option>
                    <option value="support">Support</option>
                  </Select>
                </Field>
                <Field label="Consult fee (GHS)"><Input type="number" min={0} step="0.01" value={dept.consultFee} onChange={e => setDept(d => ({ ...d, consultFee: e.target.value }))} /></Field>
              </div>
              <Button disabled={busy || !dept.name.trim() || dept.consultFee === ""}
                onClick={() => run(async () => {
                  await api.post("/api/admin/departments", {
                    name: dept.name.trim(), deptType: dept.deptType, consultFee: parseFloat(dept.consultFee),
                  });
                  setDept({ name: "", deptType: "clinical", consultFee: "" });
                }, "Department added.", "Could not add the department")}>
                Add department
              </Button>
            </Card>
          )}
        </section>
      </div>
    </Shell>
  );
}
