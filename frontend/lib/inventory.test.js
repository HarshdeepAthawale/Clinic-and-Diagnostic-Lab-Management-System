import { describe, expect, it } from 'vitest';
import { adjustProblem, categoryLabel, deltaFor, formatDelta, REASON_LABELS, stockFill, stockState } from './inventory';

const item = { active: true, currentStock: 8, lowStockThreshold: 10, lowStock: true, outOfStock: false, unit: 'tubes' };

describe('stockState', () => {
  it('follows the server flags, with retired items set apart', () => {
    expect(stockState(item)).toBe('low');
    expect(stockState({ ...item, currentStock: 0, outOfStock: true })).toBe('out');
    expect(stockState({ ...item, currentStock: 40, lowStock: false })).toBe('ok');
    expect(stockState({ ...item, active: false })).toBe('retired');
  });
});

describe('stockFill', () => {
  it('shows the level against twice the threshold, capped, and nothing for unwatched items', () => {
    expect(stockFill({ currentStock: 10, lowStockThreshold: 10 })).toBe(50);
    expect(stockFill({ currentStock: 0, lowStockThreshold: 10 })).toBe(0);
    expect(stockFill({ currentStock: 500, lowStockThreshold: 10 })).toBe(100);
    expect(stockFill({ currentStock: 5, lowStockThreshold: 0 })).toBeNull();
  });
});

describe('deltaFor', () => {
  it('applies the direction each reason requires', () => {
    expect(deltaFor('RESTOCK', '12')).toBe(12);
    expect(deltaFor('USED', 3)).toBe(-3);
    expect(deltaFor('WASTAGE', 1)).toBe(-1);
  });

  it('lets a correction go either way', () => {
    expect(deltaFor('CORRECTION', 4, 1)).toBe(4);
    expect(deltaFor('CORRECTION', 4, -1)).toBe(-4);
  });

  it('rejects anything but a whole number above zero', () => {
    expect(deltaFor('RESTOCK', '')).toBeNull();
    expect(deltaFor('RESTOCK', 0)).toBeNull();
    expect(deltaFor('RESTOCK', -2)).toBeNull();
    expect(deltaFor('RESTOCK', 1.5)).toBeNull();
    expect(deltaFor('RESTOCK', 'abc')).toBeNull();
  });
});

describe('adjustProblem', () => {
  it('blocks taking more than is there, and says how much is left', () => {
    expect(adjustProblem({ reason: 'USED', quantity: 9, item })).toBe('Only 8 tubes left — that would take it below zero');
    expect(adjustProblem({ reason: 'USED', quantity: 8, item })).toBeNull();
  });

  it('allows any restock and rejects nonsense quantities', () => {
    expect(adjustProblem({ reason: 'RESTOCK', quantity: 500, item })).toBeNull();
    expect(adjustProblem({ reason: 'RESTOCK', quantity: '', item })).toBe('Enter a whole number above zero');
    expect(adjustProblem({ reason: 'RESTOCK', quantity: 2_000_000, item })).toBe('That is too many at once');
  });
});

describe('labels', () => {
  it('formats changes with a real minus sign', () => {
    expect(formatDelta(12)).toBe('+12');
    expect(formatDelta(-3)).toBe('−3');
  });

  it('names categories and reasons, including the opening count', () => {
    expect(categoryLabel('REAGENT')).toBe('Reagents');
    expect(categoryLabel('ODD')).toBe('ODD');
    expect(REASON_LABELS.OPENING).toBe('Opening stock');
    expect(REASON_LABELS.WASTAGE).toBe('Wastage');
  });
});
