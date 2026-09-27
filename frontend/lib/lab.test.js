import { describe, expect, it } from 'vitest';
import { formatTurnaround, groupByCategory, orderTotal, prepItems, slowestTurnaround, tubeSummary } from './lab';

const CBC = { name: 'Complete Blood Count', category: 'Haematology', requiredTubeType: 'EDTA', price: 350, turnaroundHours: 4 };
const ESR = { name: 'ESR', category: 'Haematology', requiredTubeType: 'EDTA', price: '150.00', turnaroundHours: 4 };
const LIPID = {
  name: 'Lipid Profile',
  category: 'Biochemistry',
  requiredTubeType: 'SST',
  price: 600,
  turnaroundHours: 12,
  prepInstructions: 'Fast for 10–12 hours.',
};

describe('tubeSummary', () => {
  it('counts tests per tube, most-used first', () => {
    expect(tubeSummary([LIPID, CBC, ESR])).toEqual([
      { tube: 'EDTA', count: 2 },
      { tube: 'SST', count: 1 },
    ]);
  });

  it('reads order lines (tubeType) as well as catalog rows', () => {
    expect(tubeSummary([{ tubeType: 'FLUORIDE' }])).toEqual([{ tube: 'FLUORIDE', count: 1 }]);
  });
});

describe('order helpers', () => {
  it('lists only tests with prep instructions', () => {
    expect(prepItems([CBC, LIPID])).toEqual([{ name: 'Lipid Profile', prep: 'Fast for 10–12 hours.' }]);
  });

  it('adds prices given as numbers or decimal strings', () => {
    expect(orderTotal([CBC, ESR, LIPID])).toBe(1100);
  });

  it('finds the slowest turnaround and formats it', () => {
    expect(slowestTurnaround([CBC, LIPID])).toBe(12);
    expect(formatTurnaround(12)).toBe('12 h');
    expect(formatTurnaround(24)).toBe('1 day');
    expect(formatTurnaround(72)).toBe('3 days');
  });

  it('groups by category in first-seen order', () => {
    expect(groupByCategory([CBC, LIPID, ESR]).map((g) => [g.category, g.items.length])).toEqual([
      ['Haematology', 2],
      ['Biochemistry', 1],
    ]);
  });
});
