import { createTheme } from '@mantine/core';

/** Mantine theme mapped to the tokens in styles/tokens.css (Docs/Design.md §2). */
export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 6, dark: 4 },
  // Dark mode uses a bright teal; pick dark text on it so buttons keep WCAG contrast.
  autoContrast: true,
  luminanceThreshold: 0.4,
  colors: {
    brand: [
      '#e6f6f4',
      '#c3ebe6',
      '#98ddd4',
      '#63cbbe',
      '#2ed3b7',
      '#15b79e',
      '#0e9384',
      '#107569',
      '#125d56',
      '#134e48',
    ],
  },
  fontFamily: 'var(--font-sans), system-ui, -apple-system, Segoe UI, sans-serif',
  fontFamilyMonospace: 'var(--font-mono), ui-monospace, monospace',
  headings: {
    fontFamily: 'var(--font-sans), system-ui, sans-serif',
    fontWeight: '600',
  },
  defaultRadius: 'md',
  radius: { xs: '4px', sm: '6px', md: '8px', lg: '12px', xl: '16px' },
  cursorType: 'pointer',
  focusRing: 'auto',
  components: {
    Button: { defaultProps: { radius: 'md' } },
    Card: { defaultProps: { radius: 'lg', withBorder: true, padding: 'lg' } },
    Paper: { defaultProps: { radius: 'lg' } },
    TextInput: { defaultProps: { radius: 'md' } },
    PasswordInput: { defaultProps: { radius: 'md' } },
    Modal: { defaultProps: { radius: 'lg', centered: true, overlayProps: { blur: 3 } } },
    Tooltip: { defaultProps: { openDelay: 300, withArrow: true } },
  },
});
