import { describe, expect, it } from 'vitest';
import { formValuesFrom, isPartialMedicine, toRequest } from './presets';

const blank = formValuesFrom({});

describe('toRequest', () => {
  it('turns empty strings into nulls and numbers into numbers', () => {
    const body = toRequest({ ...blank, diagnosis: '  Viral fever ', vitals: { ...blank.vitals, pulseBpm: '88', temperatureC: 38.4 } });
    expect(body.diagnosis).toBe('Viral fever');
    expect(body.notes).toBeNull();
    expect(body.vitals.pulseBpm).toBe(88);
    expect(body.vitals.temperatureC).toBe(38.4);
    expect(body.vitals.spo2Percent).toBeNull();
  });

  it('sends only complete medicine rows', () => {
    const body = toRequest({
      ...blank,
      medicines: [
        { medicine: 'Paracetamol', dosage: '500 mg', frequency: '1-0-1', duration: '3 days', instructions: '' },
        { medicine: 'Azith', dosage: '', frequency: '', duration: '', instructions: '' },
      ],
    });
    expect(body.medicines).toEqual([
      { medicine: 'Paracetamol', dosage: '500 mg', frequency: '1-0-1', duration: '3 days', instructions: null },
    ]);
  });
});

describe('isPartialMedicine', () => {
  it('flags started but unfinished rows, not empty ones', () => {
    expect(isPartialMedicine({ medicine: 'Azith', dosage: '', frequency: '', duration: '', instructions: '' })).toBe(true);
    expect(isPartialMedicine({ medicine: '', dosage: '', frequency: '', duration: '', instructions: '' })).toBe(false);
    expect(isPartialMedicine({ medicine: 'A', dosage: '', frequency: '1-0-1', duration: '3 days', instructions: '' })).toBe(false);
  });
});

describe('formValuesFrom', () => {
  it('round-trips an API consultation', () => {
    const values = formValuesFrom({
      diagnosis: 'Asthma',
      vitals: { spo2Percent: 95 },
      prescription: { medicines: [{ medicine: 'Salbutamol', dosage: null, frequency: 'SOS', duration: '1 month', instructions: 'Inhale' }] },
    });
    expect(values.vitals.spo2Percent).toBe(95);
    expect(values.vitals.pulseBpm).toBe('');
    expect(toRequest(values).medicines[0].dosage).toBeNull();
  });
});
