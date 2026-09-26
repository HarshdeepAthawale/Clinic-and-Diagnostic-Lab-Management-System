import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/spotlight/styles.css';
import '@mantine/charts/styles.css';
import '@/styles/tokens.css';

import { ColorSchemeScript, mantineHtmlProps } from '@mantine/core';
import { IBM_Plex_Mono, Outfit } from 'next/font/google';
import { Providers } from './providers';

const sans = Outfit({ subsets: ['latin'], variable: '--font-sans', display: 'swap' });
const mono = IBM_Plex_Mono({
  subsets: ['latin'],
  weight: ['400', '500', '600'],
  variable: '--font-mono',
  display: 'swap',
});

export const metadata = {
  title: { default: 'CDLMS — Clinic & Diagnostic Lab', template: '%s · CDLMS' },
  description: 'One record from consultation to verified lab report.',
};

export const viewport = { themeColor: '#f5f4f1', colorScheme: 'light' };

export default function RootLayout({ children }) {
  return (
    <html lang="en" {...mantineHtmlProps} className={`${sans.variable} ${mono.variable}`}>
      <head>
        <ColorSchemeScript forceColorScheme="light" />
      </head>
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
