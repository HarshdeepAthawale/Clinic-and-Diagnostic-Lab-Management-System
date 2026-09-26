'use client';

import { ActionIcon, AppShell, Burger, Group, Kbd, ScrollArea, Skeleton, Stack, Text, Tooltip, UnstyledButton } from '@mantine/core';
import { useDisclosure, useLocalStorage } from '@mantine/hooks';
import { spotlight } from '@mantine/spotlight';
import { IconKeyboard, IconLayoutSidebarLeftCollapse, IconLayoutSidebarLeftExpand, IconSearch } from '@tabler/icons-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useMemo } from 'react';
import { roleConfig } from '@/lib/roles';
import { BrandMark } from '@/components/ui/BrandMark';
import { PhaseTag } from '@/components/ui/PhaseTag';
import { CommandPalette } from './CommandPalette';
import { NotificationsBell } from './NotificationsBell';
import { ShortcutsModal } from './ShortcutsModal';
import { ThemeToggle } from './ThemeToggle';
import { UserMenu } from './UserMenu';
import { useSession } from './useSession';
import { useShellShortcuts } from './useShellShortcuts';
import classes from './Shell.module.css';

function NavItem({ item, active, collapsed }) {
  const Icon = item.icon;
  const disabled = Boolean(item.phase);
  const content = (
    <>
      <Icon size={20} stroke={1.6} style={{ flex: 'none' }} />
      {!collapsed && <span className={classes.navLabel}>{item.label}</span>}
      {!collapsed && disabled && <PhaseTag phase={item.phase} />}
    </>
  );
  const tooltip = disabled ? `${item.label} — coming in Phase ${String(item.phase).padStart(2, '0')}` : item.label;

  return (
    <Tooltip label={tooltip} position="right" disabled={!collapsed && !disabled}>
      {disabled ? (
        <div className={classes.navItem} data-disabled aria-disabled="true">
          {content}
        </div>
      ) : (
        <Link href={item.href} className={classes.navItem} data-active={active || undefined} aria-current={active ? 'page' : undefined}>
          {content}
        </Link>
      )}
    </Tooltip>
  );
}

/** Workspace shell for staff roles (Design.md §3.1). */
export function StaffShell({ role, children }) {
  const config = roleConfig(role);
  const pathname = usePathname();
  const session = useSession(role);
  const [mobileOpened, { toggle: toggleMobile, close: closeMobile }] = useDisclosure(false);
  const [shortcutsOpened, { open: openShortcuts, close: closeShortcuts }] = useDisclosure(false);
  const [collapsed, setCollapsed] = useLocalStorage({ key: 'cdlms:sidebar-collapsed', defaultValue: false });

  const shortcuts = useMemo(
    () => ({ '?': openShortcuts, '[': () => setCollapsed((c) => !c) }),
    [openShortcuts, setCollapsed],
  );
  useShellShortcuts(shortcuts);

  const activeItem = config.nav.find((item) => !item.phase && pathname === item.href);
  const me = session.data;

  return (
    <AppShell
      header={{ height: 64 }}
      navbar={{ width: collapsed ? 76 : 256, breakpoint: 'sm', collapsed: { mobile: !mobileOpened } }}
      style={{ '--accent': config.accentVar }}
    >
      <AppShell.Header className={classes.header}>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Group gap="sm" wrap="nowrap">
            <Burger opened={mobileOpened} onClick={toggleMobile} hiddenFrom="sm" size="sm" aria-label="Toggle navigation" />
            <Stack gap={0} visibleFrom="sm">
              <Text size="xs" c="var(--text-muted)" fw={500}>
                {config.label} workspace
              </Text>
              <Text size="sm" fw={600}>
                {activeItem?.label ?? config.nav[0].label}
              </Text>
            </Stack>
          </Group>

          <UnstyledButton className={classes.searchTrigger} onClick={() => spotlight.open()} visibleFrom="sm" aria-label="Open command palette">
            <IconSearch size={16} stroke={1.8} />
            <span style={{ flex: 1 }}>Search or jump to…</span>
            <Kbd size="xs">Ctrl K</Kbd>
          </UnstyledButton>

          <Group gap={6} wrap="nowrap">
            <ActionIcon variant="subtle" color="gray" size="lg" radius="md" hiddenFrom="sm" onClick={() => spotlight.open()} aria-label="Search">
              <IconSearch size={19} stroke={1.6} />
            </ActionIcon>
            <NotificationsBell />
            <ThemeToggle />
            {me ? <UserMenu me={me} onShowShortcuts={openShortcuts} /> : <Skeleton circle height={34} />}
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar className={`${classes.navbar} ${collapsed ? classes.collapsed : ''}`} p="sm">
        <AppShell.Section px={collapsed ? 0 : 6} py={6} mb="sm" style={{ display: 'flex', justifyContent: collapsed ? 'center' : 'flex-start' }}>
          <BrandMark withWordmark={!collapsed} subtitle="Clinic & Diagnostic Lab" />
        </AppShell.Section>

        <AppShell.Section grow component={ScrollArea} scrollbarSize={6}>
          {!collapsed && <div className={classes.sectionLabel}>Workspace</div>}
          <Stack gap={2} onClick={closeMobile}>
            {config.nav.map((item) => (
              <NavItem key={item.href} item={item} active={pathname === item.href} collapsed={collapsed} />
            ))}
          </Stack>
        </AppShell.Section>

        <AppShell.Section>
          <Stack gap={2}>
            <Tooltip label="Keyboard shortcuts" position="right" disabled={!collapsed}>
              <UnstyledButton className={classes.navItem} onClick={openShortcuts}>
                <IconKeyboard size={20} stroke={1.6} />
                {!collapsed && <span className={classes.navLabel}>Shortcuts</span>}
                {!collapsed && <Kbd size="xs">?</Kbd>}
              </UnstyledButton>
            </Tooltip>
            <Tooltip label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} position="right" disabled={!collapsed}>
              <UnstyledButton className={classes.navItem} onClick={() => setCollapsed((c) => !c)} visibleFrom="sm">
                {collapsed ? <IconLayoutSidebarLeftExpand size={20} stroke={1.6} /> : <IconLayoutSidebarLeftCollapse size={20} stroke={1.6} />}
                {!collapsed && <span className={classes.navLabel}>Collapse</span>}
                {!collapsed && <Kbd size="xs">[</Kbd>}
              </UnstyledButton>
            </Tooltip>
          </Stack>
        </AppShell.Section>
      </AppShell.Navbar>

      <AppShell.Main className={classes.main}>
        {/* AppShell.Main already renders <main>; this is the skip-link target. */}
        <div id="main" className={classes.content}>
          {children}
        </div>
      </AppShell.Main>

      <CommandPalette config={config} onShowShortcuts={openShortcuts} />
      <ShortcutsModal opened={shortcutsOpened} onClose={closeShortcuts} />
    </AppShell>
  );
}
