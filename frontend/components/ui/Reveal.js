'use client';

import { motion, useReducedMotion } from 'motion/react';

/** Fades and rises into place the first time it scrolls into view (Design.md §2.5). */
export function Reveal({ children, delay = 0, y = 18, style }) {
  const reduceMotion = useReducedMotion();
  return (
    <motion.div
      initial={reduceMotion ? false : { opacity: 0, y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-40px' }}
      transition={{ duration: 0.5, delay, ease: [0.22, 1, 0.36, 1] }}
      style={style}
    >
      {children}
    </motion.div>
  );
}
