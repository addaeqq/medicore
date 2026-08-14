"use client";
import Link from "next/link";
import { Card } from "@/components/ui";

export default function PaymentFailure() {
  return (
    <div className="min-h-screen grid place-items-center px-4">
      <Card className="max-w-md text-center">
        <h1 className="font-display text-2xl mb-2">Payment didn’t go through</h1>
        <p className="text-sm text-[var(--ink)]/60 mb-4">
          Nothing was charged to your bill. You can try again from the invoice, or pay at the cash point.
        </p>
        <Link href="/billing" className="text-[var(--theatre)] underline text-sm">Back to my bills</Link>
      </Card>
    </div>
  );
}
