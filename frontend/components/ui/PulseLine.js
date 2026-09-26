'use client';

import { motion, useReducedMotion } from 'motion/react';

const PATH =
  'M0 60 H120 L140 60 L152 30 L166 95 L182 12 L198 78 L210 60 H330 L346 60 L356 44 L368 72 L378 60 H600';

/** A slow, looping ECG trace for the brand panel (Design.md §2.4). Static when motion is reduced. */
export function PulseLine({ color = 'currentColor', opacity = 0.9 }) {
  const reduceMotion = useReducedMotion();

  return (
    <svg viewBox="0 0 600 110" width="100%" height="110" preserveAspectRatio="none" aria-hidden="true">
      <path d={PATH} fill="none" stroke={color} strokeOpacity={0.18} strokeWidth="2" />
      <motion.path
        d={PATH}
        fill="none"
        stroke={color}
        strokeOpacity={opacity}
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        initial={{ pathLength: reduceMotion ? 1 : 0, pathOffset: 0 }}
        animate={
          reduceMotion
            ? { pathLength: 1 }
            : { pathLength: [0, 0.35, 0.35], pathOffset: [0, 0.65, 1] }
        }
        transition={
          reduceMotion ? { duration: 0 } : { duration: 3.2, ease: 'easeInOut', repeat: Infinity, repeatDelay: 0.6 }
        }
      />
    </svg>
  );
}
