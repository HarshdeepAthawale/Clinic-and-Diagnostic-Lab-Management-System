'use client';

import { ActionIcon, Tooltip, useComputedColorScheme, useMantineColorScheme } from '@mantine/core';
import { IconMoon, IconSun } from '@tabler/icons-react';

export function useToggleColorScheme() {
  const { setColorScheme } = useMantineColorScheme();
  const computed = useComputedColorScheme('light', { getInitialValueInEffect: true });
  return () => setColorScheme(computed === 'dark' ? 'light' : 'dark');
}

export function ThemeToggle() {
  const toggle = useToggleColorScheme();
  const computed = useComputedColorScheme('light', { getInitialValueInEffect: true });
  const label = computed === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';

  return (
    <Tooltip label={label}>
      <ActionIcon variant="subtle" color="gray" size="lg" radius="md" onClick={toggle} aria-label={label}>
        {computed === 'dark' ? <IconSun size={19} stroke={1.6} /> : <IconMoon size={19} stroke={1.6} />}
      </ActionIcon>
    </Tooltip>
  );
}
