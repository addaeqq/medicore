"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, SessionUser } from "./api";

export type Profile = {
  user: SessionUser;
  staff?: { full_name: string; staff_type: string; department_id: string | null; department: string | null };
  patient?: { full_name: string; mrn: string; dob: string; sex: string };
};

/** Loads the signed-in user's profile; redirects to /login when there is no session. */
export function useMe() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  useEffect(() => {
    api.get<Profile>("/api/me/profile")
      .then(setProfile)
      .catch(() => router.replace("/login"))
      .finally(() => setLoading(false));
  }, [router]);
  return { profile, loading };
}
