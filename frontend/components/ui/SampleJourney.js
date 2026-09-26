'use client';

import { useEffect, useState } from 'react';
import classes from './SampleJourney.module.css';

/** Patient-facing stages (Design.md §5.1). Internal steps like "Result entered" are hidden. */
export const PATIENT_STAGES = ['Ordered', 'Collected', 'At the lab', 'Testing', 'Verified', 'Report ready'];

/**
 * The sample journey "subway line": horizontal on desktop, vertical on phones.
 *
 * @param {{ stages?: string[], times?: (string|null)[], current: number, currentNote?: string, label?: string }} props
 *   `current` is the index of the stage in progress; earlier stages are done, later ones pending.
 *   `times[i]` is shown under completed stages; `currentNote` under the current one (e.g. "Now").
 */
export function SampleJourney({ stages = PATIENT_STAGES, times = [], current, currentNote = 'In progress', label }) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => {
    const frame = requestAnimationFrame(() => setMounted(true));
    return () => cancelAnimationFrame(frame);
  }, []);

  const progress = stages.length > 1 ? current / (stages.length - 1) : 0;

  return (
    <ol
      className={`${classes.journey} ${mounted ? classes.mounted : ''}`}
      style={{ '--stops': stages.length, '--progress': progress }}
      aria-label={label ?? 'Sample progress'}
    >
      <span className={classes.rail} aria-hidden="true" />
      <span className={classes.fill} aria-hidden="true" />
      {stages.map((stage, index) => {
        const state = index < current ? 'done' : index === current ? 'current' : 'future';
        const detail = state === 'done' ? times[index] : state === 'current' ? currentNote : null;
        return (
          <li
            key={stage}
            className={`${classes.stop} ${classes[state]}`}
            aria-current={state === 'current' ? 'step' : undefined}
          >
            <span className={classes.dot} aria-hidden="true" />
            <div>
              <span className={classes.label}>{stage}</span>
              {detail && <span className={classes.time}>{detail}</span>}
              <span className="visually-hidden">
                {state === 'done' ? ' — completed' : state === 'current' ? ' — current step' : ' — not started'}
              </span>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
