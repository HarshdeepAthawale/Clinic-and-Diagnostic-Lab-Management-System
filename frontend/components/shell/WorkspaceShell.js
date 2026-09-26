'use client';

import { Skeleton, Tooltip, UnstyledButton } from '@mantine/core';
import { useDisclosure, useHotkeys, useWindowScroll } from '@mantine/hooks';
import { spotlight } from '@mantine/spotlight';
import { IconArrowRight, IconMenu2, IconSearch, IconX } from '@tabler/icons-react';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useMemo, useSyncExternalStore } from 'react';
import { roleConfig } from '@/lib/roles';
import { CommandPalette } from './CommandPalette';
import { NotificationsBell } from './NotificationsBell';
import { ShortcutsModal } from './ShortcutsModal';
import { UserMenu } from './UserMenu';
import { useSession } from './useSession';
import { useShellShortcuts } from './useShellShortcuts';
import classes from './AppNav.module.css';

const phaseLabel = (phase) => `Phase ${String(phase).padStart(2, '0')}`;

function isActive(item, pathname, home) {
  if (item.phase) return false;
  return item.href === home ? pathname === home : pathname === item.href || pathname.startsWith(`${item.href}/`);
}

/** Clock store: ticks every 15s; the server snapshot is null so SSR renders nothing. */
function subscribeClock(onChange) {
  const timer = setInterval(onChange, 15_000);
  return () => clearInterval(timer);
}
const minuteNow = () => Math.floor(Date.now() / 60_000) * 60_000;

/** Live clinic clock (Design.md §3.1). */
function ClinicClock() {
  const minute = useSyncExternalStore(subscribeClock, minuteNow, () => null);
  if (minute == null) return null;
  const now = new Date(minute);
  return (
    <div className={classes.clock} aria-label="Current time">
      <span className={classes.clockTime}>{now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}</span>
      <span className={classes.clockDate}>{now.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' })}</span>
    </div>
  );
}

function NavItem({ item, active, reduceMotion }) {
  const Icon = item.icon;
  const content = (
    <span className={classes.linkInner}>
      <Icon size={17} stroke={1.8} />
      <span className={classes.linkText}>{item.label}</span>
    </span>
  );
  if (item.phase) {
    return (
      <Tooltip label={`${item.label} — coming in ${phaseLabel(item.phase)}`}>
        <span className={classes.link} data-disabled aria-disabled="true" tabIndex={0}>
          {content}
        </span>
      </Tooltip>
    );
  }
  return (
    <Tooltip label={item.label} openDelay={400}>
      <Link href={item.href} className={classes.link} data-active={active || undefined} aria-current={active ? 'page' : undefined} aria-label={item.label}>
        {active && (
          <motion.span
            layoutId="nav-pill"
            className={classes.pill}
            transition={reduceMotion ? { duration: 0 } : { type: 'spring', stiffness: 500, damping: 40 }}
          />
        )}
        {content}
      </Link>
    </Tooltip>
  );
}

/**
 * Clinical command bar for every role after login (Design.md §3.1): wordmark + role, labelled
 * links with a sliding ink pill, search (the primary tool), live clinic clock, notifications, one
 * accent call-to-action, account menu. Below 900px the links move into a menu sheet.
 */
export function WorkspaceShell({ role, children }) {
  const config = roleConfig(role);
  const pathname = usePathname();
  const { data: me } = useSession(role);
  const reduceMotion = useReducedMotion();
  const [{ y }] = useWindowScroll();
  const [menuOpened, { toggle: toggleMenu, close: closeMenu }] = useDisclosure(false);
  const [shortcutsOpened, { open: openShortcuts, close: closeShortcuts }] = useDisclosure(false);
  const isPatient = role === 'PATIENT';
  const cta = config.cta;

  const shortcuts = useMemo(() => ({ '?': openShortcuts }), [openShortcuts]);
  useShellShortcuts(shortcuts);
  useHotkeys([['Escape', closeMenu]]);
  useEffect(closeMenu, [pathname, closeMenu]);

  return (
    <>
      <div className={`${classes.bar} ${y > 8 ? classes.scrolled : ''}`}>
        <motion.header
          className={classes.nav}
          initial={reduceMotion ? false : { y: -12, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
        >
          <Link href={config.home} className={classes.brand} aria-label={`${config.label} home`}>
            <span className={classes.wordmark}>CDLMS</span>
            <span className={classes.roleChip}>{config.label}</span>
          </Link>

          <nav className={classes.links} aria-label="Main">
            {config.nav.map((item) => (
              <NavItem key={item.href} item={item} active={isActive(item, pathname, config.home)} reduceMotion={reduceMotion} />
            ))}
          </nav>

          <div className={classes.actions}>
            {!isPatient && (
              <UnstyledButton className={classes.search} onClick={() => spotlight.open()} aria-label="Search patients and samples (Ctrl K)">
                <IconSearch size={16} stroke={1.8} />
                <span className={classes.searchText}>Search patients, samples…</span>
                <span className={classes.kbd}>Ctrl K</span>
              </UnstyledButton>
            )}
            <ClinicClock />
            <NotificationsBell buttonClassName={classes.iconBtn} />
            {cta && (
              <Link href={cta.href} className={classes.cta}>
                {cta.label}
              </Link>
            )}
            {me ? (
              <UserMenu me={me} compact onShowShortcuts={isPatient ? undefined : openShortcuts} />
            ) : (
              <Skeleton circle height={34} />
            )}
            <UnstyledButton
              className={`${classes.iconBtn} ${classes.menuBtn}`}
              onClick={toggleMenu}
              aria-label={menuOpened ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpened}
            >
              {menuOpened ? <IconX size={20} /> : <IconMenu2 size={20} />}
            </UnstyledButton>
          </div>

          <AnimatePresence>
            {menuOpened && (
              <motion.div
                className={classes.sheet}
                initial={reduceMotion ? false : { opacity: 0, y: -6, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -6, scale: 0.98 }}
                transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
              >
                <nav className={classes.sheetLinks} aria-label="Main">
                  {config.nav.map((item, i) => {
                    const Icon = item.icon;
                    const active = isActive(item, pathname, config.home);
                    return (
                      <motion.div
                        key={item.href}
                        initial={reduceMotion ? false : { opacity: 0, x: -8 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.28, delay: 0.035 * i, ease: [0.22, 1, 0.36, 1] }}
                      >
                        {item.phase ? (
                          <span className={classes.sheetLink} data-disabled aria-disabled="true">
                            <Icon size={22} stroke={1.6} />
                            {item.label}
                            <span className={classes.sheetSoon}>{phaseLabel(item.phase)}</span>
                          </span>
                        ) : (
                          <Link href={item.href} className={classes.sheetLink} data-active={active || undefined}>
                            <Icon size={22} stroke={1.6} />
                            {item.label}
                          </Link>
                        )}
                      </motion.div>
                    );
                  })}
                </nav>
                <div className={classes.sheetActions}>
                  {!isPatient && (
                    <UnstyledButton
                      className={classes.sheetButton}
                      onClick={() => {
                        closeMenu();
                        spotlight.open();
                      }}
                    >
                      <IconSearch size={18} />
                      Search
                    </UnstyledButton>
                  )}
                  {cta && (
                    <Link href={cta.href} className={`${classes.sheetButton} ${classes.sheetPrimary}`}>
                      {cta.label}
                      <IconArrowRight size={18} />
                    </Link>
                  )}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.header>
      </div>

      <AnimatePresence>
        {menuOpened && (
          <motion.div
            className={classes.backdrop}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={closeMenu}
            aria-hidden="true"
          />
        )}
      </AnimatePresence>

      <main id="main" className={classes.page}>
        {children}
      </main>

      {!isPatient && (
        <>
          <CommandPalette config={config} onShowShortcuts={openShortcuts} />
          <ShortcutsModal opened={shortcutsOpened} onClose={closeShortcuts} />
        </>
      )}
    </>
  );
}
