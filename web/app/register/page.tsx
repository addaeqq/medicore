"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Input, Select } from "@/components/ui";

export default function Register() {
  const router = useRouter();
  const [form, setForm] = useState({ fullName: "", email: "", password: "", dob: "", sex: "female", phone: "" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      await api.post("/api/auth/register", form);
      await api.post("/api/auth/login", { email: form.email, password: form.password });
      router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed");
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen grid place-items-center px-4 py-8">
      <div className="w-full max-w-md">
        <h1 className="font-display text-3xl text-center mb-6">Register as a patient</h1>
        <Card>
          <form onSubmit={submit}>
            <ErrorNote message={error} />
            <Field label="Full name"><Input value={form.fullName} onChange={set("fullName")} required minLength={2} /></Field>
            <Field label="Email"><Input type="email" value={form.email} onChange={set("email")} required /></Field>
            <Field label="Password (8+ characters)"><Input type="password" value={form.password} onChange={set("password")} required minLength={8} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Date of birth"><Input type="date" value={form.dob} onChange={set("dob")} required /></Field>
              <Field label="Sex">
                <Select value={form.sex} onChange={set("sex")}>
                  <option value="female">Female</option><option value="male">Male</option><option value="other">Other</option>
                </Select>
              </Field>
            </div>
            <Field label="Phone (optional)"><Input value={form.phone} onChange={set("phone")} /></Field>
            <div className="flex items-center justify-between mt-4">
              <Button type="submit" disabled={busy}>{busy ? "Creating…" : "Create account"}</Button>
              <Link href="/login" className="text-sm text-[var(--theatre)] underline">Back to sign in</Link>
            </div>
          </form>
        </Card>
      </div>
    </div>
  );
}
