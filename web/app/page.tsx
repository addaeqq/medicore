"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";

export default function Home() {
  const router = useRouter();
  useEffect(() => {
    api.get("/api/me/profile")
      .then(() => router.replace("/dashboard"))
      .catch(() => router.replace("/login"));
  }, [router]);
  return <div className="min-h-screen grid place-items-center text-sm text-[var(--ink)]/50">Opening MediCore…</div>;
}
