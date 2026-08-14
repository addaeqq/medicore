"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { Button, Card, ErrorNote, Field, Input } from "@/components/ui";

const DEMO = [
  ["patient@medicore.test", "Patient"], ["reception@medicore.test", "Reception"],
  ["doctor@medicore.test", "Doctor"], ["pharmacist@medicore.test", "Pharmacy"],
  ["billing@medicore.test", "Billing"], ["management@medicore.test", "Management"],
  ["admin@medicore.test", "Sys admin"],
];

export default function Login() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      await api.post("/api/auth/login", { email, password });
      router.replace("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Sign-in failed");
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen grid place-items-center px-4">
      <div className="w-full max-w-md">
        <h1 className="font-display text-4xl text-center mb-1">MediCore <span className="text-[var(--theatre)]">HMS</span></h1>
        <p className="text-center text-sm text-[var(--ink)]/60 mb-6">Appointments, records, pharmacy and billing — one chart.</p>
        <Card>
          <form onSubmit={submit}>
            <ErrorNote message={error} />
            <Field label="Email"><Input type="email" value={email} onChange={e => setEmail(e.target.value)} required autoFocus /></Field>
            <Field label="Password"><Input type="password" value={password} onChange={e => setPassword(e.target.value)} required /></Field>
            <div className="flex items-center justify-between mt-4">
              <Button type="submit" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</Button>
              <Link href="/register" className="text-sm text-[var(--theatre)] underline">New patient? Register</Link>
            </div>
          </form>
        </Card>
        <details className="mt-4 text-sm text-[var(--ink)]/60">
          <summary className="cursor-pointer">Demo accounts (password: Password123!)</summary>
          <div className="mt-2 flex flex-wrap gap-2">
            {DEMO.map(([em, label]) => (
              <button key={em} onClick={() => { setEmail(em); setPassword("Password123!"); }}
                className="border border-[var(--hairline)] bg-white rounded-sm px-2 py-1 hover:border-[var(--theatre)]">{label}</button>
            ))}
          </div>
        </details>
      </div>
    </div>
  );
}
