'use client';

import { Burger, Drawer, Kbd, Skeleton, Stack, Tooltip, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { spotlight } from '@mantine/spotlight';
import { IconSearch } from '@tabler/icons-react';
import { motion } from 'motion/react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useMemo } from 'react';
import { roleConfig } from '@/lib/roles';
import { BrandMark } from '@/components/ui/BrandMark';
import { CommandPalette } from './CommandPalette';
import { NotificationsBell } from './NotificationsBell';
import { ShortcutsModal } from './ShortcutsModal';
import { UserMenu } from './UserMenu';
import { useSession } from './useSession';
import { useShellShortcuts } from './useShellShortcuts';
import classes from './AppNav.module.css';

const phaseLabel = (phase) => `P${String(phase).padStart(2, '0')}`;

function NavLink({ item, active, className, children }) {
  if (item.phase) {
    return (
      <Tooltip label={`${item.label} — coming in Phase ${String(item.phase).padStart(2, '0')}`}>
        <span className={className} data-disabled aria-disabled="true">
          {children}
        </span>
      </Tooltip>
    );
  }
  return (
    <Link href={item.href} className={className} data-active={active || undefined} aria-current={active ? 'page' : undefined}>
      {children}
    </Link>
  );
}

/**
 * The workspace shell for every role after login (Design.md §3): a sticky top navbar with the
 * role's links, Ctrl+K search, notifications and account menu. Patients also get a bottom tab bar
 * on phones; staff get a drawer.
 */
export function WorkspaceShell({ role, children }) {
  const config = roleConfig(role);
  const pathname = usePathname();
  const { data: me } = useSession(role);
  const [drawerOpened, { toggle: toggleDrawer, close: closeDrawer }] = useDisclosure(false);
  const [shortcutsOpened, { open: openShortcuts, close: closeShortcuts }] = useDisclosure(false);
  const isPatient = role === 'PATIENT';

  const shortcuts = useMemo(() => ({ '?': openShortcuts }), [openShortcuts]);
  useShellShortcuts(shortcuts);

  return (
    <div className={isPatient ? classes.withBottomBar : undefined}>
      <header className={classes.nav}>
        <div className={classes.inner}>
          {!isPatient && (
            <Burger opened={drawerOpened} onClick={toggleDrawer} hiddenFrom="md" size="sm" aria-label="Open navigation" />
          )}
          <Link href={config.home} className={classes.brand} aria-label="Home">
            <BrandMark subtitle={isPatient ? 'Patient portal' : `${config.label} workspace`} />
          </Link>

          <nav className={classes.links} aria-label="Main">
            {config.nav.map((item) => {
              const active = !item.phase && pathname === item.href;
              return (
                <NavLink key={item.href} item={item} active={active} className={classes.link}>
                  {item.label}
                  {item.phase && <span className={classes.soon}>{phaseLabel(item.phase)}</span>}
                  {active && (
                    <motion.span
                      layoutId="nav-underline"
                      className={classes.underline}
                      transition={{ type: 'spring', stiffness: 500, damping: 40 }}
                    />
                  )}
                </NavLink>
              );
            })}
          </nav>

          <div className={classes.actions}>
            {!isPatient && (
              <>
                <UnstyledButton className={classes.search} onClick={() => spotlight.open()} aria-label="Open command palette">
                  <IconSearch size={16} stroke={1.8} />
                  <span style={{ flex: 1 }}>Search…</span>
                  <Kbd size="xs">Ctrl K</Kbd>
                </UnstyledButton>
                <UnstyledButton className={classes.iconBtn} onClick={() => spotlight.open()} hiddenFrom="md" aria-label="Search">
                  <IconSearch size={19} stroke={1.7} />
                </UnstyledButton>
              </>
            )}
            <NotificationsBell />
            {me ? <UserMenu me={me} onShowShortcuts={isPatient ? undefined : openShortcuts} /> : <Skeleton circle height={34} />}
          </div>
        </div>
      </header>

      <main id="main" className={classes.page}>
        {children}
      </main>

      {isPatient ? (
        <nav className={classes.bottomBar} style={{ '--tabs': config.nav.length }} aria-label="Main">
          {config.nav.map((item) => (
            <NavLink key={item.href} item={item} active={!item.phase && pathname === item.href} className={classes.bottomTab}>
              <item.icon size={22} stroke={1.6} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
      ) : (
        <>
          <Drawer opened={drawerOpened} onClose={closeDrawer} size={300} title={<BrandMark subtitle={`${config.label} workspace`} />}>
            <Stack gap={4} onClick={closeDrawer}>
              {config.nav.map((item) => (
                <NavLink key={item.href} item={item} active={!item.phase && pathname === item.href} className={classes.drawerLink}>
                  <item.icon size={20} stroke={1.6} />
                  <span style={{ flex: 1 }}>{item.label}</span>
                  {item.phase && <span className={classes.soon}>{phaseLabel(item.phase)}</span>}
                </NavLink>
              ))}
            </Stack>
          </Drawer>
          <CommandPalette config={config} onShowShortcuts={openShortcuts} />
          <ShortcutsModal opened={shortcutsOpened} onClose={closeShortcuts} />
        </>
      )}
    </div>
  );
}
