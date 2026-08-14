"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import { Badge, Card } from "@/components/ui";

export default function PaymentSuccess() {
  const [status, setStatus] = useState<string>("verifying");
  useEffect(() => {
    const paymentId = sessionStorage.getItem("medicore.lastPaymentId");
    if (!paymentId) { setStatus("unknown"); return; }
    api.post<{ status: string }>(`/api/payments/${paymentId}/verify`)
      .then(d => setStatus(d.status))
      .catch(() => setStatus("pending"));
  }, []);
  return (
    <div className="min-h-screen grid place-items-center px-4">
      <Card className="max-w-md text-center">
        <h1 className="font-display text-2xl mb-2">Payment received — confirming</h1>
        <p className="text-sm text-[var(--ink)]/60 mb-3">
          We confirm every payment directly with the gateway before marking your bill paid.
        </p>
        <p className="mb-4"><Badge status={status} /></p>
        {status === "pending" && <p className="text-sm text-[var(--ink)]/60 mb-3">Still confirming — your bill updates automatically once the gateway settles.</p>}
        <Link href="/billing" className="text-[var(--theatre)] underline text-sm">Back to my bills</Link>
      </Card>
    </div>
  );
}
