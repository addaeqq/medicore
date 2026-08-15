/** @type {import('next').NextConfig} */
const nextConfig = {
  // Self-contained server bundle for the container image (Dockerfile, Railway).
  // Vercel builds and hosts the app itself and has no use for it, so it is left
  // off there — VERCEL=1 is set during every Vercel build.
  output: process.env.VERCEL ? undefined : "standalone",
  async rewrites() {
    // NOTE: Next resolves rewrites at BUILD time into .next/routes-manifest.json —
    // API_URL must therefore be set during `next build`, not just at run time.
    // The Dockerfile takes it as a build arg for exactly this reason.
    // Same-origin proxy: the browser only ever talks to this app, so the backend's
    // httpOnly session cookie stays first-party (SameSite=lax works in production too).
    return [{ source: "/api/:path*", destination: `${process.env.API_URL ?? "http://localhost:4000"}/api/:path*` }];
  },
};
export default nextConfig;
