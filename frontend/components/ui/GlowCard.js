'use client';

import { Box } from '@mantine/core';
import classes from './GlowCard.module.css';

/**
 * A card with a faint accent light that follows the cursor. `interactive` adds a hover lift for
 * cards that are clickable. Padding follows Mantine spacing (`p`).
 */
export function GlowCard({ children, interactive = false, p = 'lg', className = '', ...props }) {
  const onMouseMove = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    event.currentTarget.style.setProperty('--mx', `${event.clientX - rect.left}px`);
    event.currentTarget.style.setProperty('--my', `${event.clientY - rect.top}px`);
  };

  return (
    <Box
      p={p}
      className={`${classes.card} ${interactive ? classes.interactive : ''} ${className}`}
      onMouseMove={onMouseMove}
      {...props}
    >
      {children}
    </Box>
  );
}
