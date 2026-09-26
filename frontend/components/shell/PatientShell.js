'use client';

import { Group, Skeleton, Tooltip } from '@mantine/core';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { roleConfig } from '@/lib/roles';
import { BrandMark } from '@/components/ui/BrandMark';
import { ThemeToggle } from './ThemeToggle';
import { UserMenu } from './UserMenu';
import { useSession } from './useSession';
import classes from './Shell.module.css';

function Tab({ item, active, className, showIcon }) {
  const Icon = item.icon;
  const disabled = Boolean(item.phase);
  const body = (
    <>
      {showIcon && <Icon size={22} stroke={1.6} />}
      <span>{item.label}</span>
    </>
  );
  if (disabled) {
    return (
      <Tooltip label={`Coming in Phase ${String(item.phase).padStart(2, '0')}`}>
        <span className={className} data-disabled aria-disabled="true">
          {body}
        </span>
      </Tooltip>
    );
  }
  return (
    <Link href={item.href} className={className} data-active={active || undefined} aria-current={active ? 'page' : undefined}>
      {body}
    </Link>
  );
}

/** Mobile-first patient shell: top nav on desktop, bottom tab bar on phones (Design.md §3.2). */
export function PatientShell({ children }) {
  const config = roleConfig('PATIENT');
  const pathname = usePathname();
  const { data: me } = useSession('PATIENT');

  return (
    <>
      <header className={classes.patientHeader}>
        <div className={classes.patientInner}>
          <Group justify="space-between" wrap="nowrap">
            <BrandMark subtitle="Patient portal" />
            <Group gap={4} visibleFrom="sm" component="nav" aria-label="Main">
              {config.nav.map((item) => (
                <Tab key={item.href} item={item} active={pathname === item.href} className={classes.topTab} />
              ))}
            </Group>
            <Group gap={6} wrap="nowrap">
              <ThemeToggle />
              {me ? <UserMenu me={me} compact /> : <Skeleton circle height={34} />}
            </Group>
          </Group>
        </div>
      </header>

      <main id="main" className={`${classes.patientInner} ${classes.patientMain}`}>
        {children}
      </main>

      <nav className={classes.bottomBar} aria-label="Main">
        {config.nav.map((item) => (
          <Tab key={item.href} item={item} active={pathname === item.href} className={classes.bottomTab} showIcon />
        ))}
      </nav>
    </>
  );
}
