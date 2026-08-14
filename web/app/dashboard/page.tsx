"use client";
import Link from "next/link";
import Shell from "@/components/Shell";
import { useMe } from "@/lib/useMe";
import { Card, PageTitle, PatientBand } from "@/components/ui";
import { roleLabels } from "@/lib/api";

const CARDS: Record<string, { href: string; title: string; body: string }[]> = {
  patient: [
    { href: "/book", title: "Book an appointment", body: "Browse open clinic slots by department or doctor." },
    { href: "/appointments", title: "My appointments", body: "Upcoming visits, history, and cancellation." },
  ],
  receptionist: [
    { href: "/book", title: "Book for a patient", body: "Find the patient, pick a slot, confirm." },
    { href: "/appointments", title: "Check-in", body: "Mark arrivals; they join the department queue." },
    { href: "/queue", title: "Department queue", body: "Who is waiting, in order." },
  ],
  doctor: [
    { href: "/queue", title: "My clinic queue", body: "Checked-in patients; start a consultation." },
  ],
  pharmacist: [
    { href: "/pharmacy", title: "Dispensing worklist", body: "Open prescriptions; FEFO picks the batch for you." },
  ],
  billing_clerk: [
    { href: "/billing", title: "Billing workspace", body: "Invoices, charges from consults and pharmacy, payments." },
  ],
  management: [
    { href: "/billing", title: "Invoices", body: "Review and void with a recorded reason." },
  ],
  sys_admin: [
    { href: "/admin", title: "Doctor schedules", body: "Publish weekly clinics; slots generate automatically." },
    { href: "/book", title: "Book for a patient", body: "Front-desk booking tools." },
  ],
};

export default function Dashboard() {
  const { profile, loading } = useMe();
  if (loading || !profile) return null;
  const cards = CARDS[profile.user.role] ?? [];
  return (
    <Shell profile={profile}>
      <PageTitle eyebrow={roleLabels[profile.user.role]}>
        {profile.staff?.department ? `${profile.staff.department} — ` : ""}Today’s work
      </PageTitle>
      {profile.patient && (
        <PatientBand name={profile.patient.full_name} mrn={profile.patient.mrn}
          dob={profile.patient.dob} sex={profile.patient.sex} />
      )}
      <div className="grid sm:grid-cols-2 gap-4">
        {cards.map(c => (
          <Link key={c.href} href={c.href}>
            <Card className="h-full hover:border-[var(--theatre)] transition-colors">
              <h2 className="font-display text-xl mb-1">{c.title}</h2>
              <p className="text-sm text-[var(--ink)]/60">{c.body}</p>
            </Card>
          </Link>
        ))}
        {profile.user.role === "patient" && (
          <Link href={`/patients/${profile.user.patientId}/emr`}>
            <Card className="h-full hover:border-[var(--theatre)] transition-colors">
              <h2 className="font-display text-xl mb-1">My medical record</h2>
              <p className="text-sm text-[var(--ink)]/60">Consultations, allergies and prescriptions.</p>
            </Card>
          </Link>
        )}
        {profile.user.role === "patient" && (
          <Link href="/billing">
            <Card className="h-full hover:border-[var(--theatre)] transition-colors">
              <h2 className="font-display text-xl mb-1">My bills</h2>
              <p className="text-sm text-[var(--ink)]/60">Invoices and online payment.</p>
            </Card>
          </Link>
        )}
      </div>
    </Shell>
  );
}
