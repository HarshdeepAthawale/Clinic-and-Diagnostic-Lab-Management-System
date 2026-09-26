import { createTheme } from '@mantine/core';
import buttonClasses from '@/styles/Button.module.css';

/** Mantine theme mapped to styles/tokens.css (Docs/Design.md §2). Light mode only (ADR-017). */
export const theme = createTheme({
  primaryColor: 'accent',
  primaryShade: 6,
  autoContrast: true,
  colors: {
    // Built around the accent #b42318 at index 6.
    accent: [
      '#fef3f2',
      '#fee4e2',
      '#fecdca',
      '#fda29b',
      '#f97066',
      '#d92d20',
      '#b42318',
      '#9c1d13',
      '#84180f',
      '#6e140c',
    ],
  },
  black: '#1c1b19',
  fontFamily: 'var(--font-sans), ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif',
  fontFamilyMonospace: 'var(--font-mono), "SF Mono", Consolas, monospace',
  headings: {
    fontFamily: 'var(--font-sans), ui-sans-serif, system-ui, sans-serif',
    fontWeight: '600',
  },
  defaultRadius: 'md',
  radius: { xs: '6px', sm: '8px', md: '10px', lg: '14px', xl: '18px' },
  shadows: {
    xs: '0 1px 2px rgb(28 27 25 / 0.06)',
    sm: '0 1px 2px rgb(28 27 25 / 0.06)',
    md: '0 8px 24px -8px rgb(28 27 25 / 0.14)',
    lg: '0 24px 60px -20px rgb(28 27 25 / 0.22)',
    xl: '0 24px 60px -20px rgb(28 27 25 / 0.22)',
  },
  cursorType: 'pointer',
  focusRing: 'auto',
  components: {
    Button: { defaultProps: { radius: 'md' }, classNames: buttonClasses },
    Card: { defaultProps: { radius: 'xl', withBorder: true, padding: 'lg' } },
    Paper: { defaultProps: { radius: 'xl' } },
    TextInput: { defaultProps: { radius: 'md' } },
    PasswordInput: { defaultProps: { radius: 'md' } },
    Modal: { defaultProps: { radius: 'xl', centered: true, overlayProps: { blur: 3 } } },
    Menu: { defaultProps: { radius: 'lg', shadow: 'md' } },
    Popover: { defaultProps: { radius: 'lg', shadow: 'md' } },
    Tooltip: { defaultProps: { openDelay: 250, withArrow: true, color: 'dark' } },
  },
});
