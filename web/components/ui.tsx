"use client";
import Link from "next/link";
import { ReactNode } from "react";

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`bg-white border border-[var(--hairline)] rounded-sm p-5 ${className}`}>{children}</div>;
}

export function PageTitle({ eyebrow, children }: { eyebrow?: string; children: ReactNode }) {
  return (
    <header className="mb-6">
      {eyebrow && <p className="text-xs uppercase tracking-[0.14em] text-[var(--theatre)] mb-1">{eyebrow}</p>}
      <h1 className="font-display text-3xl">{children}</h1>
    </header>
  );
}

export function Button({ children, onClick, kind = "primary", disabled = false, type = "button" }:
  { children: ReactNode; onClick?: () => void; kind?: "primary" | "quiet" | "danger"; disabled?: boolean; type?: "button" | "submit" }) {
  const styles = {
    primary: "bg-[var(--theatre)] text-white hover:bg-[var(--theatre-deep)]",
    quiet: "bg-white text-[var(--ink)] border border-[var(--hairline)] hover:border-[var(--theatre)]",
    danger: "bg-white text-[var(--triage)] border border-[var(--triage)] hover:bg-red-50",
  }[kind];
  return (
    <button type={type} onClick={onClick} disabled={disabled}
      className={`px-4 py-2 rounded-sm text-sm font-medium disabled:opacity-40 disabled:cursor-not-allowed ${styles}`}>
      {children}
    </button>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block mb-3">
      <span className="block text-xs uppercase tracking-wider text-[var(--ink)]/60 mb-1">{label}</span>
      {children}
    </label>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props}
    className="w-full border border-[var(--hairline)] rounded-sm px-3 py-2 text-sm bg-white focus:border-[var(--theatre)] outline-none" />;
}

export function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props}
    className="w-full border border-[var(--hairline)] rounded-sm px-3 py-2 text-sm bg-white focus:border-[var(--theatre)] outline-none" />;
}

export function Badge({ status }: { status: string }) {
  const tone =
    ["paid", "dispensed", "completed", "available", "signed"].includes(status) ? "text-[var(--theatre)] border-[var(--theatre)]"
    : ["pending", "partially_paid", "partially_dispensed", "booked", "issued", "waiting", "open"].includes(status) ? "text-[var(--amber)] border-[var(--amber)]"
    : ["failed", "void", "cancelled", "no_show"].includes(status) ? "text-[var(--triage)] border-[var(--triage)]"
    : "text-[var(--ink)]/60 border-[var(--hairline)]";
  return <span className={`inline-block border rounded-sm px-1.5 py-0.5 text-[11px] uppercase tracking-wide ${tone}`}>{status.replace(/_/g, " ")}</span>;
}

/** The signature element: a wristband-style identity strip shown wherever a patient is in context. */
export function PatientBand({ name, mrn, dob, sex }: { name: string; mrn: string; dob?: string; sex?: string }) {
  return (
    <div className="patient-band rounded-sm px-4 py-2.5 mb-5 flex flex-wrap items-baseline gap-x-4 gap-y-1">
      <span className="font-chart text-xs bg-[var(--theatre)] text-white px-1.5 py-0.5 rounded-sm">{mrn}</span>
      <span className="font-display text-lg">{name}</span>
      {dob && <span className="text-xs text-[var(--ink)]/60">DOB <span className="font-chart">{String(dob).slice(0, 10)}</span></span>}
      {sex && <span className="text-xs text-[var(--ink)]/60 capitalize">{sex}</span>}
    </div>
  );
}

export function ErrorNote({ message }: { message: string | null }) {
  if (!message) return null;
  return <p className="text-sm text-[var(--triage)] border border-[var(--triage)]/40 bg-red-50/50 rounded-sm px-3 py-2 mb-3">{message}</p>;
}

export function Empty({ children, action }: { children: ReactNode; action?: { href: string; label: string } }) {
  return (
    <div className="text-center py-10 text-sm text-[var(--ink)]/60">
      <p>{children}</p>
      {action && <Link className="text-[var(--theatre)] underline mt-2 inline-block" href={action.href}>{action.label}</Link>}
    </div>
  );
}
