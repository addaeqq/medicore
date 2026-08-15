"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ReactNode } from "react";
import { api, roleLabels } from "@/lib/api";
import { Profile } from "@/lib/useMe";

const NAV: Record<string, { href: string; label: string }[]> = {
  patient: [
    { href: "/dashboard", label: "Overview" },
    { href: "/book", label: "Book appointment" },
    { href: "/appointments", label: "My appointments" },
  ],
  receptionist: [
    { href: "/dashboard", label: "Overview" },
    { href: "/book", label: "Book for patient" },
    { href: "/appointments", label: "Check-in" },
    { href: "/queue", label: "Queue" },
  ],
  doctor: [
    { href: "/dashboard", label: "Overview" },
    { href: "/queue", label: "My queue" },
  ],
  nurse: [
    { href: "/dashboard", label: "Overview" },
    { href: "/ward", label: "Ward board" },
    { href: "/queue", label: "Queue" },
  ],
  pharmacist: [
    { href: "/dashboard", label: "Overview" },
    { href: "/pharmacy", label: "Dispensing & stock" },
  ],
  lab_tech: [
    { href: "/dashboard", label: "Overview" },
    { href: "/lab", label: "Bench worklist" },
  ],
  billing_clerk: [
    { href: "/dashboard", label: "Overview" },
    { href: "/billing", label: "Billing" },
  ],
  management: [
    { href: "/dashboard", label: "Overview" },
    { href: "/billing", label: "Invoices" },
  ],
  sys_admin: [
    { href: "/dashboard", label: "Overview" },
    { href: "/staff", label: "Staff" },
    { href: "/admin", label: "Schedules" },
    { href: "/book", label: "Book for patient" },
  ],
};

export default function Shell({ profile, children }: { profile: Profile; children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const links = NAV[profile.user.role] ?? [{ href: "/dashboard", label: "Overview" }];
  const displayName = profile.staff?.full_name ?? profile.patient?.full_name ?? "Account";

  async function logout() {
    await api.post("/api/auth/logout");
    router.replace("/login");
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-[var(--hairline)] bg-white">
        <div className="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between gap-4">
          <Link href="/dashboard" className="font-display text-xl">
            MediCore <span className="text-[var(--theatre)]">HMS</span>
          </Link>
          <nav className="flex gap-1 overflow-x-auto">
            {links.map(l => (
              <Link key={l.href} href={l.href}
                className={`px-3 py-1.5 text-sm rounded-sm whitespace-nowrap ${pathname === l.href
                  ? "bg-[var(--theatre)] text-white" : "hover:bg-[var(--paper)]"}`}>
                {l.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-3 text-sm">
            <span className="hidden sm:block text-[var(--ink)]/70">
              {displayName} · {roleLabels[profile.user.role] ?? profile.user.role}
            </span>
            <button onClick={logout} className="text-[var(--triage)] hover:underline">Sign out</button>
          </div>
        </div>
      </header>
      <main className="max-w-5xl mx-auto px-4 py-8">{children}</main>
    </div>
  );
}
