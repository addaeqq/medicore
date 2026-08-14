"use client";
import { useState } from "react";
import { api } from "@/lib/api";
import { Input } from "./ui";

export type PatientRow = { patient_id: string; mrn: string; full_name: string; dob: string; sex: string };

/** Staff-side patient lookup by name or MRN; selection is explicit and banded upstream. */
export default function PatientPicker({ onPick }: { onPick: (p: PatientRow) => void }) {
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<PatientRow[]>([]);
  const [searched, setSearched] = useState(false);

  async function search(term: string) {
    setQ(term);
    if (term.trim().length < 2) { setRows([]); setSearched(false); return; }
    const out = await api.get<{ patients: PatientRow[] }>(`/api/patients/search?q=${encodeURIComponent(term)}`);
    setRows(out.patients); setSearched(true);
  }

  return (
    <div>
      <Input placeholder="Search patient by name or MRN…" value={q} onChange={e => search(e.target.value)} />
      {searched && rows.length === 0 && <p className="text-sm text-[var(--ink)]/50 mt-2">No patient matches “{q}”.</p>}
      <ul className="mt-2 divide-y divide-[var(--hairline)] border border-[var(--hairline)] rounded-sm bg-white empty:hidden">
        {rows.map(p => (
          <li key={p.patient_id}>
            <button onClick={() => onPick(p)} className="w-full text-left px-3 py-2 hover:bg-[var(--paper)] flex gap-3 items-baseline">
              <span className="font-chart text-xs text-[var(--theatre)]">{p.mrn}</span>
              <span className="text-sm">{p.full_name}</span>
              <span className="text-xs text-[var(--ink)]/50 ml-auto">{String(p.dob).slice(0, 10)}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
