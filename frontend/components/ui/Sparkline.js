import { useId } from 'react';

/** Tiny trend line with a soft fill (Design.md §4 "Trend sparkline"). */
export function Sparkline({ values, width = 96, height = 32, color = 'var(--accent)', label }) {
  const id = useId();
  if (!values?.length) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const x = (i) => (i / Math.max(values.length - 1, 1)) * width;
  const y = (v) => height - 3 - ((v - min) / (max - min || 1)) * (height - 6);
  const line = values.map((v, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join(' ');

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={label ?? 'Trend'}>
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.25" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={`${line} L${width} ${height} L0 ${height} Z`} fill={`url(#${id})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={x(values.length - 1)} cy={y(values[values.length - 1])} r="2.6" fill={color} />
    </svg>
  );
}
