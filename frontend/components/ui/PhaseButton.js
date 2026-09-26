'use client';

import { Button } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconClockHour4 } from '@tabler/icons-react';

/**
 * A real-looking action for a feature that lands in a later phase. Clicking it says exactly what
 * it will do and when, instead of silently doing nothing.
 */
export function PhaseButton({ phase, what, children, ...props }) {
  const tag = `Phase ${String(phase).padStart(2, '0')}`;
  return (
    <Button
      {...props}
      onClick={() =>
        notifications.show({
          title: `${children} — coming in ${tag}`,
          message: what,
          icon: <IconClockHour4 size={18} />,
          color: 'dark',
          radius: 'lg',
        })
      }
    >
      {children}
    </Button>
  );
}
