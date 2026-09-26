const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080';

/** @type {import('next').NextConfig} */
const nextConfig = {
  // The browser only ever talks to this origin. /api/* is forwarded to Spring Boot, so the
  // httpOnly JWT cookie is first-party and no CORS is needed (ADR-009, ADR-014).
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${backendUrl}/api/:path*` }];
  },
  experimental: {
    optimizePackageImports: ['@mantine/core', '@mantine/hooks', '@tabler/icons-react'],
  },
};

export default nextConfig;
