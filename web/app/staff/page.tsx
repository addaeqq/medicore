"use client";
import { useCallback, useEffect, useState } from "react";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { api, ApiError, roleLabels } from "@/lib/api";
import { Badge, Button, Card, Empty, ErrorNote, Field, Input, PageTitle, Select } from "@/components/ui";

type StaffRow = {
  staff_id: string; full_name: string; staff_type: string; email: string; role: string;
  is_active: boolean; locked: boolean; department: string | null; ward: string | null;
};
type Dept = { department_id: string; name: string };
type Ward = { ward_id: string; name: string };

const EMPTY = { email: "", password: "", fullName: "", role: "doctor", departmentId: "", wardId: "" };

/** Mirrors StaffRoles on the server, so the form asks for what the role actually needs. */
const NEEDS_DEPARTMENT = ["doctor"];
const NEEDS_WARD = ["nurse"];

export default function StaffAdmin() {
  const { profile, loading } = useMe();
  const [rows, setRows] = useState<StaffRow[]>([]);
  const [roles, setRoles] = useState<string[]>([]);
  const [departments, setDepartments] = useState<Dept[]>([]);
  const [wards, setWards] = useState<Ward[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.get<{ staff: StaffRow[]; roles: string[] }>("/api/admin/staff")
      .then(d => { setRows(d.staff); setRoles(d.roles); })
      .catch(e => setError(e instanceof ApiError ? e.message : "Could not load the staff list"));
  }, []);

  useEffect(() => { if (profile) refresh(); }, [profile, refresh]);
  useEffect(() => {
    if (!profile) return;
    api.get<{ departments: Dept[] }>("/api/departments").then(d => setDepartments(d.departments)).catch(() => null);
    api.get<{ wards: Ward[] }>("/api/nursing/wards").then(d => setWards(d.wards)).catch(() => null);
  }, [profile]);

  if (loading || !profile) return null;

  const needsDept = NEEDS_DEPARTMENT.includes(form.role);
  const needsWard = NEEDS_WARD.includes(form.role);
  const complete = form.email.trim() && form.password.length >= 8 && form.fullName.trim()
    && (!needsDept || form.departmentId) && (!needsWard || form.wardId);

  async function run(fn: () => Promise<unknown>, failure: string, ok?: string) {
    setBusy(true); setError(null); setNote(null);
    try { await fn(); refresh(); if (ok) setNote(ok); }
    catch (err) { setError(err instanceof ApiError ? err.message : failure); }
    finally { setBusy(false); }
  }

  const create = () => run(async () => {
    await api.post("/api/admin/staff", {
      email: form.email.trim(), password: form.password, fullName: form.fullName.trim(), role: form.role,
      departmentId: form.departmentId || null, wardId: needsWard ? form.wardId || null : null,
    });
    setForm(EMPTY);
  }, "Could not create the account", `${form.fullName.trim()} can now sign in.`);

  const active = rows.filter(r => r.is_active);
  const inactive = rows.filter(r => !r.is_active);

  return (
    <Shell profile={profile}>
      <PageTitle eyebrow="Administration">Staff accounts</PageTitle>
      <ErrorNote message={error} />
      {note && <p className="text-sm text-[var(--theatre)] border border-[var(--theatre)]/40 bg-emerald-50/40 rounded-sm px-3 py-2 mb-3">{note}</p>}

      <div className="grid lg:grid-cols-[1fr_20rem] gap-4">
        <section>
          <h2 className="text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-3">
            On the roster — {active.length} active{inactive.length > 0 ? `, ${inactive.length} deactivated` : ""}
          </h2>
          {rows.length === 0 && <Empty>No staff accounts yet.</Empty>}
          <div className="space-y-2">
            {rows.map(s => (
              <Card key={s.staff_id} className={s.is_active ? "" : "opacity-60"}>
                <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                  <span className="text-sm font-medium">{s.full_name}</span>
                  <Badge status={roleLabels[s.role] ?? s.role} />
                  {s.locked && <span className="text-xs text-[var(--triage)]">locked out</span>}
                  {!s.is_active && <span className="text-xs text-[var(--ink)]/60">deactivated</span>}
                  <span className="ml-auto flex gap-2">
                    {s.locked && (
                      <Button kind="quiet" disabled={busy}
                        onClick={() => run(() => api.post(`/api/admin/staff/${s.staff_id}/unlock`),
                          "Could not clear the lockout", `${s.full_name} can sign in again.`)}>Clear lockout</Button>
                    )}
                    {s.is_active ? (
                      <Button kind="danger" disabled={busy}
                        onClick={() => run(() => api.post(`/api/admin/staff/${s.staff_id}/deactivate`),
                          "Could not deactivate", `${s.full_name} can no longer sign in.`)}>Deactivate</Button>
                    ) : (
                      <Button kind="quiet" disabled={busy}
                        onClick={() => run(() => api.post(`/api/admin/staff/${s.staff_id}/reactivate`),
                          "Could not reactivate", `${s.full_name} can sign in again.`)}>Reactivate</Button>
                    )}
                  </span>
                </div>
                <p className="text-xs text-[var(--ink)]/60 mt-1">
                  <span className="font-chart">{s.email}</span>
                  {s.department ? ` · ${s.department}` : ""}{s.ward ? ` · ${s.ward}` : ""}
                </p>
              </Card>
            ))}
          </div>
        </section>

        <section>
          <Card className="border-[var(--theatre)]">
            <h2 className="font-display text-lg mb-1">Add a staff member</h2>
            <p className="text-sm text-[var(--ink)]/60 mb-3">
              They can sign in immediately. Patients register themselves; family access is granted by the patient.
            </p>
            <Field label="Full name">
              <Input value={form.fullName} onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))} />
            </Field>
            <Field label="Work email">
              <Input type="email" value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
            </Field>
            <Field label="Temporary password (min 8 characters)">
              <Input type="password" value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))} />
            </Field>
            <Field label="Role">
              <Select value={form.role} onChange={e => setForm(f => ({ ...f, role: e.target.value }))}>
                {(roles.length ? roles : ["doctor"]).map(r => (
                  <option key={r} value={r}>{roleLabels[r] ?? r}</option>
                ))}
              </Select>
            </Field>
            <Field label={needsDept ? "Department (required for a doctor)" : "Department (optional)"}>
              <Select value={form.departmentId} onChange={e => setForm(f => ({ ...f, departmentId: e.target.value }))}>
                <option value="">—</option>
                {departments.map(d => <option key={d.department_id} value={d.department_id}>{d.name}</option>)}
              </Select>
            </Field>
            {needsWard && (
              <Field label="Assigned ward (required for a nurse)">
                <Select value={form.wardId} onChange={e => setForm(f => ({ ...f, wardId: e.target.value }))}>
                  <option value="">—</option>
                  {wards.map(w => <option key={w.ward_id} value={w.ward_id}>{w.name}</option>)}
                </Select>
              </Field>
            )}
            {needsDept && <p className="text-xs text-[var(--ink)]/50 mb-2">A doctor without a department cannot be booked.</p>}
            {needsWard && <p className="text-xs text-[var(--ink)]/50 mb-2">Ward access is scoped to this ward (AC-03).</p>}
            <Button onClick={create} disabled={busy || !complete}>{busy ? "Creating…" : "Create account"}</Button>
          </Card>

          <Card className="mt-4">
            <p className="text-xs text-[var(--ink)]/60">
              Accounts are deactivated, never deleted — the audit trail references them, and it is append-only.
              A new doctor becomes bookable once you publish their clinic under <span className="font-medium">Schedules</span>.
            </p>
          </Card>
        </section>
      </div>
    </Shell>
  );
}
