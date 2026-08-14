import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "MediCore HMS",
  description: "Hospital management for clinics in Ghana - appointments, records, pharmacy and billing.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
