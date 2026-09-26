'use client';

import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { useEffect, useState } from 'react';

/**
 * Rolls through words in place, each sliding up and out (text-reel effect).
 * Screen readers get the first word only; with reduced motion the first word stays put.
 */
export function TextReel({ words, interval = 2600, className }) {
  const reduceMotion = useReducedMotion();
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (reduceMotion || words.length < 2) return undefined;
    const timer = setInterval(() => setIndex((i) => (i + 1) % words.length), interval);
    return () => clearInterval(timer);
  }, [reduceMotion, words.length, interval]);

  return (
    <span className={className}>
      <span className="visually-hidden">{words[0]}</span>
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={words[index]}
          aria-hidden="true"
          initial={{ y: '100%', opacity: 0, filter: 'blur(6px)' }}
          animate={{ y: '0%', opacity: 1, filter: 'blur(0px)' }}
          exit={{ y: '-100%', opacity: 0, filter: 'blur(6px)' }}
          transition={{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
        >
          {words[index]}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
