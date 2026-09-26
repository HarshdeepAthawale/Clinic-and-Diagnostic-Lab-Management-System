/**
 * DEMO DATA — fictional people and values, used to show each role's designed dashboard before the
 * features behind it exist. Every panel that reads from here shows a <DemoBadge phase={…} />.
 * Each phase replaces its panels' demo data with real API calls and deletes the matching export.
 */

export const DOCTOR = {
  kpis: { patientsToday: 14, seen: 6, waiting: 3, resultsToReview: 4 },
  schedule: [
    { time: '09:00', name: 'Rahul Verma', meta: '42 M', reason: 'Diabetes follow-up', status: 'done' },
    { time: '09:20', name: 'Neha Kulkarni', meta: '29 F', reason: 'Fever, 3 days', status: 'done' },
    { time: '09:40', name: 'Arjun Shah', meta: '61 M', reason: 'BP review', status: 'done' },
    { time: '10:00', name: 'Sana Qureshi', meta: '35 F', reason: 'Thyroid follow-up', status: 'done' },
    { time: '10:20', name: 'Vikram Joshi', meta: '55 M', reason: 'Chest discomfort', status: 'done' },
    { time: '10:40', name: 'Meena Pillai', meta: '47 F', reason: 'Knee pain', status: 'done' },
    { time: '11:00', name: 'Asha Rao', meta: '32 F', reason: 'Lab results review', status: 'in-consult' },
    { time: '11:20', name: 'Karan Malhotra', meta: '24 M', reason: 'Walk-in · Ankle sprain', status: 'waiting', token: 'A-17' },
    { time: '11:40', name: 'Fatima Sheikh', meta: '38 F', reason: 'Migraine', status: 'waiting' },
    { time: '12:00', name: 'Dev Patel', meta: '8 M', reason: 'Vaccination', status: 'waiting' },
    { time: '12:20', name: 'Lakshmi Iyer', meta: '66 F', reason: 'Post-op review', status: 'booked' },
    { time: '12:40', name: 'Imran Khan', meta: '51 M', reason: 'Cholesterol', status: 'booked' },
  ],
  current: {
    name: 'Asha Rao',
    meta: '32 yrs · Female · PID-000184',
    allergies: 'Penicillin (rash, 2019)',
    reason: 'Review of lab results ordered on 20 Sep',
    lastVisit: '20 Sep 2026 · Fatigue, weight gain',
    vitals: [
      { label: 'BP', value: '124/82', unit: 'mmHg' },
      { label: 'Pulse', value: '78', unit: 'bpm' },
      { label: 'SpO₂', value: '98', unit: '%' },
      { label: 'BMI', value: '26.4', unit: '' },
    ],
    labs: [
      { test: 'HbA1c', value: 7.2, unit: '%', low: 4, high: 5.6, trend: [5.9, 6.3, 6.8, 7.2] },
      { test: 'Fasting glucose', value: 132, unit: 'mg/dL', low: 70, high: 99, trend: [104, 118, 126, 132] },
      { test: 'Hemoglobin', value: 11.2, unit: 'g/dL', low: 12, high: 15.5, trend: [12.4, 12.1, 11.6, 11.2] },
      { test: 'TSH', value: 2.1, unit: 'mIU/L', low: 0.4, high: 4, trend: [2.6, 2.3, 2.2, 2.1] },
    ],
  },
  resultsReady: [
    { patient: 'Rahul Verma', test: 'Lipid profile', when: '8:42 AM', flag: 'high' },
    { patient: 'Sana Qureshi', test: 'Thyroid profile', when: 'Yesterday', flag: 'normal' },
    { patient: 'Arjun Shah', test: 'Kidney function', when: 'Yesterday', flag: 'high' },
    { patient: 'Neha Kulkarni', test: 'CBC', when: '2 days ago', flag: 'normal' },
  ],
};

export const PATHOLOGIST = {
  kpis: { pending: 23, critical: 2, retests: 3, verifiedToday: 41, avgWaitMin: 38 },
  hourly: [2, 4, 7, 9, 6, 8, 5],
  queue: [
    { code: 'LAB-20260927-0042', patient: 'Vikram Joshi', test: 'Serum potassium', value: 6.8, unit: 'mmol/L', low: 3.5, high: 5.1, criticalLow: 2.5, criticalHigh: 6.5, waited: '52 min', attempt: 1 },
    { code: 'LAB-20260927-0039', patient: 'Lakshmi Iyer', test: 'Hemoglobin', value: 6.4, unit: 'g/dL', low: 12, high: 15.5, criticalLow: 7, criticalHigh: 20, waited: '47 min', attempt: 2 },
    { code: 'LAB-20260927-0036', patient: 'Asha Rao', test: 'HbA1c', value: 7.2, unit: '%', low: 4, high: 5.6, waited: '41 min', attempt: 1 },
    { code: 'LAB-20260927-0035', patient: 'Imran Khan', test: 'LDL cholesterol', value: 168, unit: 'mg/dL', low: 0, high: 130, waited: '39 min', attempt: 1 },
    { code: 'LAB-20260927-0031', patient: 'Neha Kulkarni', test: 'Platelets', value: 238, unit: '×10³/µL', low: 150, high: 410, waited: '33 min', attempt: 1 },
    { code: 'LAB-20260927-0029', patient: 'Rahul Verma', test: 'Creatinine', value: 1.0, unit: 'mg/dL', low: 0.7, high: 1.3, waited: '28 min', attempt: 1 },
    { code: 'LAB-20260927-0027', patient: 'Sana Qureshi', test: 'Free T4', value: 1.3, unit: 'ng/dL', low: 0.8, high: 1.8, waited: '21 min', attempt: 1 },
  ],
};

export const RECEPTION = {
  kpis: { appointments: 38, walkIns: 9, avgWaitMin: 14, collections: 42600 },
  board: {
    waiting: [
      { token: 'A-17', name: 'Karan Malhotra', doctor: 'Dr. Kabir Mehta', waited: 26, type: 'Walk-in' },
      { token: 'B-08', name: 'Pooja Nair', doctor: 'Dr. Sneha Rao', waited: 18, type: 'Appointment' },
      { token: 'A-18', name: 'Fatima Sheikh', doctor: 'Dr. Kabir Mehta', waited: 9, type: 'Appointment' },
      { token: 'A-19', name: 'Dev Patel', doctor: 'Dr. Kabir Mehta', waited: 4, type: 'Appointment' },
    ],
    withDoctor: [
      { token: 'A-16', name: 'Asha Rao', doctor: 'Dr. Kabir Mehta', waited: 0, type: 'Appointment', room: 'Room 2' },
      { token: 'B-07', name: 'Ritu Agarwal', doctor: 'Dr. Sneha Rao', waited: 0, type: 'Walk-in', room: 'Room 4' },
    ],
    done: [
      { token: 'A-15', name: 'Meena Pillai', doctor: 'Dr. Kabir Mehta', type: 'Appointment' },
      { token: 'B-06', name: 'Suresh Menon', doctor: 'Dr. Sneha Rao', type: 'Appointment' },
      { token: 'A-14', name: 'Vikram Joshi', doctor: 'Dr. Kabir Mehta', type: 'Walk-in' },
    ],
  },
  rejections: [
    { code: 'LAB-20260927-0033', patient: 'Lakshmi Iyer', test: 'CBC', reason: 'Hemolyzed', phone: '98******12', at: '10:42 AM' },
    { code: 'LAB-20260927-0024', patient: 'Arjun Shah', test: 'Kidney function', reason: 'Sample exhausted during retest', phone: '99******40', at: '9:58 AM' },
  ],
  hourlyArrivals: [4, 9, 12, 8, 6, 5, 7],
};

export const LAB = {
  pipeline: { toCollect: 7, toReceive: 5, inTesting: 12, retests: 1 },
  retest: {
    code: 'LAB-20260927-0039',
    patient: 'Lakshmi Iyer',
    test: 'Hemoglobin',
    tube: 'EDTA',
    reason: 'Critical value needs confirmation',
    by: 'Dr. Meera Iyer',
    attempt: 2,
  },
  queue: [
    { code: 'LAB-20260927-0044', patient: 'Karan Malhotra', tests: 'CBC, ESR', tube: 'EDTA', status: 'to-collect', due: 'Now' },
    { code: 'LAB-20260927-0045', patient: 'Pooja Nair', tests: 'Lipid profile', tube: 'SST', status: 'to-collect', due: '11:30' },
    { code: 'LAB-20260927-0043', patient: 'Dev Patel', tests: 'Blood glucose (F)', tube: 'FLUORIDE', status: 'to-receive', due: '11:05', mismatch: 'Collected in SST — needs Fluoride' },
    { code: 'LAB-20260927-0041', patient: 'Fatima Sheikh', tests: 'PT / INR', tube: 'CITRATE', status: 'to-receive', due: '10:58' },
    { code: 'LAB-20260927-0040', patient: 'Ritu Agarwal', tests: 'Thyroid profile', tube: 'SST', status: 'in-testing', due: '10:40' },
    { code: 'LAB-20260927-0038', patient: 'Suresh Menon', tests: 'Ammonia', tube: 'HEPARIN', status: 'in-testing', due: '10:31' },
  ],
  lowStock: [
    { item: 'EDTA tubes (3 mL)', left: 42, threshold: 100, unit: 'tubes' },
    { item: 'Glucose reagent kit', left: 2, threshold: 5, unit: 'kits' },
  ],
};

export const ADMIN = {
  kpis: {
    patients: { value: 126, delta: 8, trend: [88, 94, 102, 97, 110, 118, 126] },
    revenue: { value: 184200, delta: 12, trend: [131, 142, 150, 139, 161, 170, 184] },
    inFlight: { value: 57, delta: -4, trend: [61, 58, 64, 60, 55, 59, 57] },
    tat: { value: 5.4, delta: -6, trend: [6.3, 6.1, 5.9, 6, 5.7, 5.6, 5.4] },
  },
  revenue: [
    { day: '14 Sep', consultations: 48, lab: 83 },
    { day: '15 Sep', consultations: 52, lab: 90 },
    { day: '16 Sep', consultations: 55, lab: 95 },
    { day: '17 Sep', consultations: 50, lab: 89 },
    { day: '18 Sep', consultations: 58, lab: 103 },
    { day: '19 Sep', consultations: 61, lab: 109 },
    { day: '20 Sep', consultations: 44, lab: 76 },
    { day: '21 Sep', consultations: 39, lab: 64 },
    { day: '22 Sep', consultations: 57, lab: 101 },
    { day: '23 Sep', consultations: 60, lab: 108 },
    { day: '24 Sep', consultations: 63, lab: 112 },
    { day: '25 Sep', consultations: 59, lab: 104 },
    { day: '26 Sep', consultations: 66, lab: 118 },
    { day: '27 Sep', consultations: 64, lab: 120 },
  ],
  topTests: [
    { test: 'CBC', orders: 312 },
    { test: 'Lipid profile', orders: 188 },
    { test: 'HbA1c', orders: 164 },
    { test: 'Thyroid profile', orders: 141 },
    { test: 'Liver function', orders: 122 },
    { test: 'Kidney function', orders: 109 },
  ],
  tatDays: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
  tat: [
    { test: 'CBC', hours: [2.1, 2.4, 2.0, 2.2, 2.6, 3.1, 3.4] },
    { test: 'Lipid profile', hours: [4.8, 5.1, 4.6, 4.9, 5.5, 6.2, 6.8] },
    { test: 'HbA1c', hours: [5.2, 5.0, 5.4, 5.1, 5.9, 7.4, 8.1] },
    { test: 'Thyroid profile', hours: [7.9, 8.3, 7.6, 8.0, 9.2, 11.5, 12.4] },
    { test: 'Culture & sensitivity', hours: [46, 48, 45, 47, 50, 55, 58] },
  ],
  staff: [
    { name: 'Priya Nair', role: 'LAB_TECHNICIAN', metric: '64 samples', detail: 'avg 6.1 min / receipt' },
    { name: 'Dr. Meera Iyer', role: 'PATHOLOGIST', metric: '41 verified', detail: 'avg 3.2 min / result' },
    { name: 'Dr. Kabir Mehta', role: 'DOCTOR', metric: '14 consults', detail: 'avg 17 min / visit' },
    { name: 'Rohan Das', role: 'RECEPTIONIST', metric: '38 check-ins', detail: 'avg wait 14 min' },
  ],
  accessLog: [
    { who: 'Dr. Kabir Mehta', patient: 'Asha Rao', what: 'Full record', when: '11:02 AM' },
    { who: 'Dr. Meera Iyer', patient: 'Vikram Joshi', what: 'Lab history', when: '10:57 AM' },
    { who: 'Dr. Kabir Mehta', patient: 'Meena Pillai', what: 'Prescription', when: '10:44 AM' },
  ],
};

export const PATIENT = {
  nextAppointment: {
    doctor: 'Dr. Kabir Mehta',
    specialty: 'General Medicine',
    when: 'Tue, 30 Sep · 10:20 AM',
    where: 'Room 2, Ground floor',
    prep: 'Fast for 10–12 hours before your lipid test (water is fine).',
  },
  activeSample: {
    code: 'LAB-20260927-0036',
    test: 'HbA1c + Fasting glucose',
    current: 3,
    times: ['8:10 AM', '8:32 AM', '9:05 AM'],
    eta: 'usually ready by 5 PM',
  },
  latestReport: {
    title: 'Thyroid profile',
    date: '12 Sep 2026',
    verifiedBy: 'Dr. Meera Iyer, MD Pathology',
    results: [
      { test: 'TSH', value: 2.1, unit: 'mIU/L', low: 0.4, high: 4 },
      { test: 'Free T4', value: 1.3, unit: 'ng/dL', low: 0.8, high: 1.8 },
      { test: 'Free T3', value: 3.1, unit: 'pg/mL', low: 2.3, high: 4.2 },
    ],
  },
  bill: { number: 'INV-2026-0918', amount: 1450, due: 'Due 30 Sep' },
};
