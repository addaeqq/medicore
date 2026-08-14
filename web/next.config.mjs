/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    // Same-origin proxy: the browser only ever talks to this app, so the backend's
    // httpOnly session cookie stays first-party (SameSite=lax works in production too).
    return [{ source: "/api/:path*", destination: `${process.env.API_URL ?? "http://localhost:4000"}/api/:path*` }];
  },
};
export default nextConfig;
