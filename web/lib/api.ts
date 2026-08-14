// Thin JSON client. Same-origin (/api is proxied to the backend), cookies included.
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    credentials: "include",
    headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) throw new ApiError(res.status, data.error ?? `Request failed (${res.status})`);
  return data as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
};

export type SessionUser = { userId: string; role: string; staffId: string | null; patientId: string | null };

export const roleLabels: Record<string, string> = {
  patient: "Patient", doctor: "Doctor", nurse: "Nurse", receptionist: "Reception",
  pharmacist: "Pharmacy", billing_clerk: "Billing", management: "Management",
  lab_tech: "Laboratory", family: "Family", sys_admin: "System admin",
};

export function fmtMoney(v: unknown): string {
  const n = typeof v === "number" ? v : parseFloat(String(v ?? 0));
  return `GHS ${n.toFixed(2)}`;
}

export function fmtWhen(v: unknown): string {
  if (!v) return "—";
  const d = new Date(String(v));
  return d.toLocaleString("en-GB", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}
