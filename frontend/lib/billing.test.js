import { describe, expect, it } from 'vitest';
import { discountError, discountPresets, paymentError, receptionDiscountCap, toPaise } from './billing';

describe('toPaise', () => {
  it('avoids floating-point drift', () => {
    expect(toPaise(0.1 + 0.2)).toBe(30);
    expect(toPaise('1450.50')).toBe(145050);
  });
});

describe('paymentError', () => {
  it('accepts an amount up to the balance', () => {
    expect(paymentError('450', 450)).toBeNull();
    expect(paymentError(100.5, 450)).toBeNull();
  });

  it('rejects empty, zero, over-balance and sub-paisa amounts', () => {
    expect(paymentError('', 450)).toMatch(/Enter an amount/);
    expect(paymentError(0, 450)).toMatch(/Enter an amount/);
    expect(paymentError('abc', 450)).toMatch(/Enter an amount/);
    expect(paymentError(451, 450)).toMatch(/More than the balance/);
    expect(paymentError('10.555', 450)).toMatch(/two decimal/);
  });
});

describe('front-desk discount cap', () => {
  it('rounds the cap down to the paisa', () => {
    expect(receptionDiscountCap(1450, 20)).toBe(290);
    expect(receptionDiscountCap(333.33, 20)).toBe(66.66);
  });

  it('only offers presets within the cap', () => {
    expect(discountPresets(1000, 10, false).map((p) => p.percent)).toEqual([5, 10]);
    expect(discountPresets(1000, 10, true).map((p) => p.percent)).toEqual([5, 10, 15, 20]);
    expect(discountPresets(1450, 20, false).at(-1)).toEqual({ percent: 20, amount: 290 });
  });
});

describe('discountError', () => {
  const base = { gross: 1450, paid: 0, capPercent: 20, canExceedCap: false, reason: 'Senior citizen' };

  it('allows a discount with a reason within the cap, and 0 to remove it', () => {
    expect(discountError({ ...base, amount: 290 })).toBeNull();
    expect(discountError({ ...base, amount: 0, reason: '' })).toBeNull();
  });

  it('needs a reason for any discount', () => {
    expect(discountError({ ...base, amount: 100, reason: '  ' })).toMatch(/why/);
  });

  it('holds the front desk to the cap but lets admins go further', () => {
    expect(discountError({ ...base, amount: 300 })).toMatch(/up to 20%/);
    expect(discountError({ ...base, amount: 300, canExceedCap: true })).toBeNull();
  });

  it('never exceeds the bill or drops below what is already paid', () => {
    expect(discountError({ ...base, amount: 2000, canExceedCap: true })).toMatch(/more than the bill/);
    expect(discountError({ ...base, amount: 400, paid: 1100, canExceedCap: true })).toMatch(/already paid/);
  });
});
