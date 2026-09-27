/** Quick-pick values for the prescription builder (doctors can always type their own). */

/** Morning-afternoon-night notation used on Indian prescriptions, plus common alternatives. */
export const FREQUENCIES = ['1-0-1', '1-0-0', '0-0-1', '1-1-1', '0-1-0', '1-1-0', 'Once a week', 'SOS (when needed)', 'Every 6 hours', 'Every 8 hours'];

export const DURATIONS = ['1 day', '3 days', '5 days', '7 days', '10 days', '14 days', '1 month', '3 months', 'Continue'];

export const INSTRUCTIONS = ['After food', 'Before food', 'Empty stomach', 'At bedtime', 'With milk', 'Apply thin layer', 'Inhale'];

/** Follow-up shortcuts: label → days from today. */
export const FOLLOW_UPS = [
  { label: '3 days', days: 3 },
  { label: '1 week', days: 7 },
  { label: '2 weeks', days: 14 },
  { label: '1 month', days: 30 },
];

export const EMPTY_MEDICINE = { medicine: '', dosage: '', frequency: '', duration: '', instructions: '' };

const VITAL_KEYS = ['bpSystolic', 'bpDiastolic', 'pulseBpm', 'temperatureC', 'spo2Percent', 'weightKg'];

/** Form values from a consultation returned by the API. */
export function formValuesFrom(consultation) {
  const vitals = Object.fromEntries(VITAL_KEYS.map((k) => [k, consultation.vitals?.[k] ?? '']));
  return {
    chiefComplaint: consultation.chiefComplaint ?? '',
    notes: consultation.notes ?? '',
    diagnosis: consultation.diagnosis ?? '',
    advice: consultation.advice ?? '',
    followUpDate: consultation.followUpDate ?? '',
    vitals,
    medicines: (consultation.prescription?.medicines ?? []).map((m) => ({
      medicine: m.medicine ?? '',
      dosage: m.dosage ?? '',
      frequency: m.frequency ?? '',
      duration: m.duration ?? '',
      instructions: m.instructions ?? '',
    })),
  };
}

const text = (v) => (typeof v === 'string' && v.trim() ? v.trim() : null);
const num = (v) => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));

/** A medicine row is complete when it names the medicine, how often and for how long. */
export function isCompleteMedicine(m) {
  return Boolean(text(m.medicine) && text(m.frequency) && text(m.duration));
}

/** A row the doctor started but left unfinished (not just an empty row). */
export function isPartialMedicine(m) {
  return !isCompleteMedicine(m) && Object.values(m).some((v) => text(v));
}

/**
 * The API request body. Only complete medicine rows are sent, so autosave never fails on a row the
 * doctor is still typing; finishing checks for partial rows separately.
 */
export function toRequest(values) {
  return {
    chiefComplaint: text(values.chiefComplaint),
    notes: text(values.notes),
    diagnosis: text(values.diagnosis),
    advice: text(values.advice),
    followUpDate: text(values.followUpDate),
    vitals: Object.fromEntries(VITAL_KEYS.map((k) => [k, num(values.vitals[k])])),
    medicines: values.medicines.filter(isCompleteMedicine).map((m) => ({
      medicine: text(m.medicine),
      dosage: text(m.dosage),
      frequency: text(m.frequency),
      duration: text(m.duration),
      instructions: text(m.instructions),
    })),
  };
}
