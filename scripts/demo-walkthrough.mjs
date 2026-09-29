#!/usr/bin/env node
/**
 * Demo walkthrough and seed (Phase 10). Drives the REAL API as each role, end to end:
 *   register -> token -> consult -> prescribe -> order -> collect -> (reject/redraw) -> test -> (retest)
 *   -> verify -> report -> dispatch -> invoice -> payment
 * so the demo database holds genuine records, made by the same code paths a user would hit. Exits non-zero
 * at the first step that fails, which makes it the rehearsal as well as the seeder.
 *
 * Local development ONLY. History is made by running visits "now" and then moving the whole database
 * timeline back (all timestamps, triggers off) — that is done through the dev Postgres container, and the
 * script refuses to run against anything else.
 *
 *   docker compose up -d
 *   (start the backend with the dev profile)
 *   node scripts/demo-walkthrough.mjs            # seed once (stops if the demo patients already exist)
 *   node scripts/demo-walkthrough.mjs --force    # seed again on top
 *
 * Demo accounts and password: see backend/src/main/resources/db/seed/R__dev_seed.sql (Demo@12345).
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const API = process.env.CDLMS_API ?? 'http://localhost:8080/api';
const PASSWORD = 'Demo@12345';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const FORCE = process.argv.includes('--force');
const STATE_FILE = resolve(ROOT, 'scripts', '.demo-state.json');

if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(API)) {
  console.error(`Refusing to run against ${API}: this script only seeds a local development backend.`);
  process.exit(2);
}

// ------------------------------------------------------------------ talking to the API

class Actor {
  constructor(label, email) {
    this.label = label;
    this.email = email;
    this.cookies = '';
  }

  async login() {
    const res = await this.raw('POST', '/auth/login', { email: this.email, password: PASSWORD }, false);
    if (!res.ok) throw new Error(`${this.label}: cannot sign in as ${this.email} (${res.status}). Is the dev seed loaded?`);
    this.cookies = res.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ');
    return this;
  }

  raw(method, path, body, withCookies = true) {
    return fetch(`${API}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        'X-CSRF-Protection': '1',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(withCookies && this.cookies ? { Cookie: this.cookies } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  }

  async call(method, path, body) {
    const res = await this.raw(method, path, body);
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error(`${this.label} ${method} ${path} -> ${res.status} ${data?.code ?? ''} ${data?.error ?? text}`);
    return data;
  }

  get = (path) => this.call('GET', path);
  post = (path, body) => this.call('POST', path, body ?? {});
  patch = (path, body) => this.call('PATCH', path, body);
  put = (path, body) => this.call('PUT', path, body);
}

const step = (text) => console.log(`  - ${text}`);
const heading = (text) => console.log(`\n${text}`);

// ------------------------------------------------------------------ resuming

/**
 * What has been done so far, kept in scripts/.demo-state.json. If a run stops halfway (a service was down, a
 * step failed), running the script again carries on where it stopped instead of repeating visits.
 * `--force` starts over. Delete the file if you reset the database.
 */
const state = { done: [], paced: [] };
if (FORCE && existsSync(STATE_FILE)) unlinkSync(STATE_FILE);
if (existsSync(STATE_FILE)) Object.assign(state, JSON.parse(readFileSync(STATE_FILE, 'utf8')));
const save = () => writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));

/** Runs `work` once: skipped when a previous run already finished it. */
async function once(key, work) {
  if (state.done.includes(key)) {
    step(`(already done: ${key})`);
    return null;
  }
  const result = await work();
  state.done.push(key);
  save();
  return result;
}

// ------------------------------------------------------------------ the dev database (history)

function psql(sql) {
  execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'cdlms', '-d', 'cdlms', '-v', 'ON_ERROR_STOP=1', '-q'], {
    cwd: ROOT,
    input: sql,
    stdio: ['pipe', 'ignore', 'inherit'],
  });
}

/** Moves every timestamp in the database back by `days`, so what was just made becomes history. */
function shiftTimeline(days) {
  psql(`
    SET session_replication_role = replica;
    DO $$
    DECLARE r record;
    BEGIN
      FOR r IN SELECT c.table_name, c.column_name
               FROM information_schema.columns c
               JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
               WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE'
                 AND c.data_type = 'timestamp with time zone' AND c.table_name <> 'flyway_schema_history'
      LOOP
        EXECUTE format('UPDATE %I SET %I = %I - interval ''${days} days'' WHERE %I IS NOT NULL', r.table_name, r.column_name, r.column_name, r.column_name);
      END LOOP;
    END $$;
  `);
}

/**
 * Spaces a sample's journey out the way a real one goes, so turnaround charts have something to show:
 * minutes from collection to each step. Applied to the status log (the source of turnaround).
 */
function paceSample(sampleId, [toLab, waiting, testing, verifying, sending]) {
  const at = { RECEIVED_AT_LAB: toLab };
  at.IN_TESTING = at.RECEIVED_AT_LAB + waiting;
  at.RESULT_ENTERED = at.IN_TESTING + testing;
  at.VERIFIED = at.RESULT_ENTERED + verifying;
  at.REPORT_GENERATED = at.VERIFIED;
  at.DISPATCHED = at.REPORT_GENERATED + sending;
  const rows = Object.entries(at).map(([status, minutes]) => `('${status}', ${minutes})`).join(', ');
  psql(`
    SET session_replication_role = replica;
    UPDATE sample_status_events e SET occurred_at = c.t + o.mins * interval '1 minute'
    FROM (SELECT occurred_at AS t FROM sample_status_events WHERE sample_id = '${sampleId}' AND status = 'COLLECTED') c,
         (VALUES ${rows}) AS o (status, mins)
    WHERE e.sample_id = '${sampleId}' AND e.status = o.status;
  `);
}

// ------------------------------------------------------------------ the clinic's people

const who = {
  reception: new Actor('reception', 'reception@demo.cdlms.dev'),
  kabir: new Actor('Dr. Kabir', 'doctor@demo.cdlms.dev'),
  sana: new Actor('Dr. Sana', 'doctor2@demo.cdlms.dev'),
  lab: new Actor('lab', 'lab@demo.cdlms.dev'),
  pathologist: new Actor('pathologist', 'pathologist@demo.cdlms.dev'),
  admin: new Actor('admin', 'admin@demo.cdlms.dev'),
};

const ctx = { doctors: {}, tests: {}, patients: {} };

/** Patients who have an account (the rest get their reports as a download link, not an email). */
const HAS_LOGIN = new Set(['Asha Rao']);

async function registerPatient(key, p) {
  const found = await who.reception.get(`/patients?q=${encodeURIComponent(p.fullName)}`);
  const existing = found.content.find((c) => c.fullName === p.fullName);
  if (existing) {
    ctx.patients[key] = { id: existing.id, code: existing.patientCode, name: p.fullName };
    return ctx.patients[key];
  }
  const res = await who.reception.post('/patients', {
    fullName: p.fullName, dob: p.dob, gender: p.gender, phone: p.phone, address: p.address,
    knownAllergies: p.allergies ?? null, bloodGroup: p.bloodGroup ?? null, medicalHistory: p.history ?? null,
    emergencyContactName: p.emergency ?? null, emergencyContactPhone: p.emergency ? p.phone : null,
  });
  ctx.patients[key] = { id: res.patient.id, code: res.patient.patientCode, name: p.fullName };
  step(`registered ${p.fullName} (${res.patient.patientCode})`);
  return ctx.patients[key];
}

// ------------------------------------------------------------------ one visit, end to end

/**
 * A walk-in token. If the patient already has a visit with this doctor today (the dev seed makes a booking for
 * the demo patient, and earlier testing may have left one waiting), that one is used or cancelled first,
 * because a new token can't be issued on top of it.
 */
async function checkIn(patient, doctorId, reason) {
  try {
    return await who.reception.post('/queue/tokens', { patientId: patient.id, doctorId, reason });
  } catch (error) {
    if (!String(error.message).includes('ALREADY_BOOKED')) throw error;
    const today = new Date().toISOString().slice(0, 10);
    const existing = (await who.reception.get(`/appointments?from=${today}&to=${today}&doctorId=${doctorId}`))
      .find((a) => a.patient.id === patient.id && ['BOOKED', 'CHECKED_IN', 'IN_CONSULTATION'].includes(a.status));
    if (!existing) throw error;
    if (existing.status !== 'BOOKED') return existing; // already waiting or with the doctor: just use it
    await who.reception.patch(`/appointments/${existing.id}/status`, { status: 'CANCELLED', note: 'Replaced by a walk-in for the demo' });
    return who.reception.post('/queue/tokens', { patientId: patient.id, doctorId, reason });
  }
}

const VITALS = { bpSystolic: 118, bpDiastolic: 76, pulseBpm: 72, temperatureC: 36.8, spo2Percent: 98, weightKg: 58.5 };

/** A value that is in range for a parameter (its middle), unless overridden by name. */
function valueFor(param, overrides) {
  if (overrides && param.name in overrides) return String(overrides[param.name]);
  if (param.valueType === 'TEXT') return 'Negative';
  const { refLow: lo, refHigh: hi } = param;
  if (lo != null && hi != null) return ((Number(lo) + Number(hi)) / 2).toFixed(1);
  if (hi != null) return (Number(hi) * 0.6).toFixed(1);
  if (lo != null) return (Number(lo) * 1.5).toFixed(1);
  return '1';
}

async function enterResults(sampleId, overrides, analyzer = 'Sysmex XN-1000') {
  const { sheet } = await who.lab.get(`/samples/${sampleId}/results`);
  const values = sheet.flatMap((t) => t.parameters).map((param) => ({ parameterId: param.parameterId, value: valueFor(param, overrides) }));
  return who.lab.post(`/samples/${sampleId}/results`, { analyzer, values });
}

const ORDER_OF_STAGES = ['ordered', 'collected', 'received', 'testing', 'entered', 'verified', 'dispatched'];
const reached = (stage, to) => ORDER_OF_STAGES.indexOf(to) >= ORDER_OF_STAGES.indexOf(stage);

/**
 * Takes a sample through the lab as far as `to`. Options: `values` (name -> value), `channel` (dispatch),
 * `reject` (hemolyzed at receipt, redraw waits), `retest` (pathologist sends it back once, then it is
 * re-entered as `retestValues`).
 */
async function advance(sample, { to, values, channel = 'EMAIL', reject = false, retest = false, retestValues }) {
  const id = sample.id;
  if (!reached('collected', to)) return;
  await who.lab.post(`/samples/${id}/collect`, { tubeTypeUsed: sample.requiredTubeType, bodySite: 'Left arm' });
  if (!reached('received', to)) return;
  if (reject) {
    await who.lab.post(`/samples/${id}/receive`, { accepted: false, reason: 'HEMOLYZED', note: 'Visibly haemolysed — please redraw' });
    return;
  }
  await who.lab.post(`/samples/${id}/receive`, { accepted: true });
  if (!reached('testing', to)) return;
  await who.lab.post(`/samples/${id}/start-testing`);
  if (!reached('entered', to)) return;
  await enterResults(id, values);
  if (retest) {
    await who.pathologist.post(`/samples/${id}/return-for-retest`, { reason: 'IMPLAUSIBLE_VALUE', note: 'Please repeat on the analyzer' });
    if (retestValues) await enterResults(id, retestValues);
    else return;
  }
  if (!reached('verified', to)) return;
  await who.pathologist.post(`/samples/${id}/verify`);
  if (!reached('dispatched', to)) return;
  await who.lab.post(`/reports/${id}/dispatch`, { channel });
}

/**
 * One visit: a walk-in token, the consultation with a prescription and lab orders, the bill and payment,
 * then the samples through the lab as far as `to`. Returns what was made.
 */
async function visit(spec) {
  return once(spec.key, () => runVisit(spec));
}

async function runVisit({ key, patient, doctor, complaint, notes, diagnosis, advice, medicines = [], tests = [], priority = 'ROUTINE',
                       to = 'dispatched', values, channel = HAS_LOGIN.has(patient.name) ? 'EMAIL' : 'DOWNLOAD_LINK', pay = 1, method = 'UPI', reject, retest, retestValues, pace, finish = true }) {
  const doctorId = ctx.doctors[doctor.label].id;
  const appointment = await checkIn(patient, doctorId, complaint);
  const consultation = await doctor.post('/consultations', { appointmentId: appointment.id });
  const body = { chiefComplaint: complaint, notes, diagnosis, advice, vitals: VITALS, medicines };
  let order = null;
  if (tests.length) {
    order = await doctor.post('/lab-orders', {
      consultationId: consultation.id, testIds: tests.map((code) => ctx.tests[code]), priority, clinicalNotes: `Ordered for ${complaint.toLowerCase()}`,
    });
  }
  await doctor.post(`/consultations/${consultation.id}/complete`, body);

  let invoice = null;
  const bills = await who.reception.get(`/invoices?status=OUTSTANDING&q=${encodeURIComponent(patient.code)}`);
  // The newest outstanding bill is the one this visit just made (older unpaid ones stay unpaid).
  invoice = [...bills.content].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0] ?? null;
  if (invoice && pay > 0) {
    const amount = pay === 1 ? Number(invoice.balance) : Math.round(Number(invoice.balance) * pay);
    await who.reception.post(`/invoices/${invoice.id}/payments`, { amount, method, reference: method === 'UPI' ? `UPI-${Date.now() % 1e8}` : null });
  }

  const samples = order ? await who.lab.get(`/lab-orders/${order.id}/samples`) : [];
  for (const sample of samples) {
    await advance(sample, { to, values, channel, reject, retest, retestValues });
    if (pace && to === 'dispatched' && !retest) state.paced.push({ id: sample.id, pace });
  }
  step(`${patient.name}: ${complaint.toLowerCase()} -> ${tests.join(' + ') || 'no tests'}${order ? ` (${to})` : ''}`);
  return { consultation, order, samples, invoice };
}

// ------------------------------------------------------------------ the story

const ORAL = (medicine, dosage, frequency, duration, instructions) => ({ medicine, dosage, frequency, duration, instructions });

async function main() {
  console.log(`Demo walkthrough against ${API}`);
  for (const actor of Object.values(who)) await actor.login();
  const doctors = await who.reception.get('/doctors');
  ctx.doctors['Dr. Kabir'] = doctors.find((d) => d.fullName.includes('Kabir'));
  ctx.doctors['Dr. Sana'] = doctors.find((d) => d.fullName.includes('Sana'));
  for (const code of ['CBC', 'ESR', 'FBS', 'PPBS', 'HBA1C', 'LIPID', 'LFT', 'KFT']) {
    const found = await who.lab.get(`/lab-tests?q=${code}`);
    const test = found.find((t) => t.code === code);
    if (!test) throw new Error(`Test ${code} is missing from the catalog`);
    ctx.tests[code] = test.id;
  }

  if (state.done.includes('t-pooja')) {
    console.log('\nThe demo patients already exist. Nothing to do (use --force to start over and add another round).');
    return;
  }

  const kabirActor = who.kabir;
  const sanaActor = who.sana;

  // ---- people
  heading('Front desk registers patients');
  const asha = await registerPatient('asha', { fullName: 'Asha Rao', dob: '1994-05-12', gender: 'FEMALE', phone: '+91 98765 43210', address: '12 MG Road, Pune' });
  const kavita = await registerPatient('kavita', { fullName: 'Kavita Joshi', dob: '1988-11-03', gender: 'FEMALE', phone: '+91 98220 11458', address: '4 Karve Road, Pune', bloodGroup: 'B+', emergency: 'Sameer Joshi' });
  const aarav = await registerPatient('aarav', { fullName: 'Aarav Singh', dob: '2017-02-21', gender: 'MALE', phone: '+91 99221 30765', address: '78 Baner Road, Pune', bloodGroup: 'O+', emergency: 'Priti Singh' });
  const meena = await registerPatient('meena', { fullName: 'Meena Kulkarni', dob: '1971-08-30', gender: 'FEMALE', phone: '+91 98900 77412', address: '22 Shivaji Nagar, Pune', bloodGroup: 'A+', history: 'Type 2 diabetes since 2019; hypertension', emergency: 'Anand Kulkarni' });
  const rahul = await registerPatient('rahul', { fullName: 'Rahul Verma', dob: '1982-01-17', gender: 'MALE', phone: '+91 97300 55214', address: '9 Kothrud, Pune', bloodGroup: 'O-', emergency: 'Neelam Verma' });
  const farah = await registerPatient('farah', { fullName: 'Farah Sheikh', dob: '1999-07-08', gender: 'FEMALE', phone: '+91 98500 21876', address: '31 Viman Nagar, Pune', allergies: 'Sulfa drugs' });
  const sunil = await registerPatient('sunil', { fullName: 'Sunil Patil', dob: '1965-04-25', gender: 'MALE', phone: '+91 98811 66230', address: '5 Aundh, Pune', bloodGroup: 'AB+', history: 'Pre-diabetes' });
  const rohit = await registerPatient('rohit', { fullName: 'Rohit Nair', dob: '1990-12-02', gender: 'MALE', phone: '+91 97650 90412', address: '17 Wakad, Pune' });
  const neha = await registerPatient('neha', { fullName: 'Neha Bhatt', dob: '2001-03-14', gender: 'FEMALE', phone: '+91 98230 40871', address: '63 Hadapsar, Pune' });
  const pooja = await registerPatient('pooja', { fullName: 'Pooja Menon', dob: '1985-09-19', gender: 'FEMALE', phone: '+91 99700 18256', address: '2 Kalyani Nagar, Pune', bloodGroup: 'A-' });

  // ---- inventory
  heading('Lab stock');
  await once('stock', stockShelves);

  // ---- history, oldest first; each round is moved back in time before the next is made
  heading('Round 1 (about 60 days ago)');
  await visit({
    key: 'r1-asha',
    patient: asha, doctor: kabirActor, complaint: 'Fatigue and breathlessness on stairs', notes: 'Pale conjunctiva. No fever. Diet review done.',
    diagnosis: 'Iron-deficiency anaemia (suspected)', advice: 'Iron-rich diet. Repeat blood count in a month.',
    medicines: [ORAL('Ferrous ascorbate', '100 mg', 'Once daily', '30 days', 'After food'), ORAL('Vitamin C', '500 mg', 'Once daily', '30 days')],
    tests: ['CBC', 'FBS'], values: { Haemoglobin: 10.2, 'Total WBC count': 7.8, 'Fasting Blood Sugar': 118, 'Fasting blood sugar': 118, 'Fasting glucose': 118, Glucose: 118 },
    pace: [22, 30, 55, 20, 12],
  });
  await visit({
    key: 'r1-kavita',
    patient: kavita, doctor: kabirActor, complaint: 'Severe weakness and dizziness', notes: 'Marked pallor, tachycardia. Sent for urgent counts.',
    diagnosis: 'Severe anaemia', advice: 'Admit for transfusion assessment if Hb stays this low.', tests: ['CBC'], priority: 'URGENT',
    values: { Haemoglobin: 6.5, 'Platelet count': 190 }, pace: [12, 10, 40, 14, 6], pay: 1, method: 'CARD',
  });
  await once('r1-kavita-ack', async () => {
    const alerts = await kabirActor.get('/reports/critical');
    for (const alert of alerts.items.filter((a) => a.patientName === kavita.name)) {
      await kabirActor.post(`/reports/${alert.sampleId}/acknowledge-critical`, { note: 'Phoned the patient; advised same-day admission.' });
      step(`Dr. Kabir acknowledged the critical result for ${kavita.name}`);
    }
  });
  await closeRound('shift-1', 30);

  heading('Round 2 (about 30 days ago)');
  await visit({
    key: 'r2-asha',
    patient: asha, doctor: kabirActor, complaint: 'Follow-up: anaemia', notes: 'Feeling better. Energy improving.', diagnosis: 'Iron-deficiency anaemia, improving',
    advice: 'Continue iron for another month.', medicines: [ORAL('Ferrous ascorbate', '100 mg', 'Once daily', '30 days', 'After food')],
    tests: ['CBC', 'FBS', 'LIPID'], values: { Haemoglobin: 11.4, 'Total WBC count': 7.2, 'Fasting Blood Sugar': 109, 'Fasting blood sugar': 109, 'Fasting glucose': 109, Glucose: 109 },
    pace: [20, 35, 60, 25, 15], pay: 0.5, method: 'CASH',
  });
  await visit({
    key: 'r2-aarav',
    patient: aarav, doctor: sanaActor, complaint: 'Recurrent fever and fatigue', notes: 'Mild pallor. Chest clear.', diagnosis: 'Viral illness with mild anaemia',
    advice: 'Fluids and rest. Review in a week.', medicines: [ORAL('Paracetamol syrup', '250 mg/5 mL', 'Every 6 hours if fever', '3 days', '5 mL')],
    tests: ['CBC', 'ESR'], values: { Haemoglobin: 11.8 }, pace: [18, 25, 45, 18, 10], method: 'UPI',
  });
  await closeRound('shift-2', 20);

  heading('Round 3 (about 10 days ago)');
  await visit({
    key: 'r3-asha',
    patient: asha, doctor: kabirActor, complaint: 'Routine review', notes: 'Asymptomatic.', diagnosis: 'Anaemia resolving', advice: 'Stop iron after two more weeks.',
    tests: ['CBC', 'FBS'], values: { Haemoglobin: 12.8, 'Total WBC count': 6.9, 'Fasting Blood Sugar': 102, 'Fasting blood sugar': 102, 'Fasting glucose': 102, Glucose: 102 },
    pace: [19, 28, 52, 22, 14], method: 'UPI',
  });
  await visit({
    key: 'r3-meena',
    patient: meena, doctor: kabirActor, complaint: 'Diabetes and blood pressure review', notes: 'BP 138/86. Compliant with medicines.',
    diagnosis: 'Type 2 diabetes; hypertension — controlled', advice: 'Low-salt diet. Walk 30 minutes daily.',
    medicines: [ORAL('Metformin', '500 mg', 'Twice daily', '90 days', 'With meals'), ORAL('Amlodipine', '5 mg', 'Once daily', '90 days')],
    tests: ['LFT', 'HBA1C'], values: { 'HbA1c': 7.4 }, retest: true, retestValues: { 'HbA1c': 7.2 }, pace: [24, 32, 75, 30, 18], pay: 1, method: 'CARD',
  });
  await closeRound('shift-3', 10);

  // ---- today: the bench at every stage
  heading('Today (the lab at every stage)');
  await visit({
    key: 't-rahul',
    patient: rahul, doctor: kabirActor, complaint: 'Black stools and weakness', notes: 'Suspected GI bleed. Urgent blood count.',
    diagnosis: 'Suspected upper GI bleed', advice: 'Nil by mouth. Await counts.', tests: ['CBC'], priority: 'URGENT',
    values: { Haemoglobin: 6.8 }, to: 'verified', pay: 1, method: 'CASH',
  });
  await visit({
    key: 't-rohit',
    patient: rohit, doctor: kabirActor, complaint: 'Yellowing of eyes', notes: 'Mild jaundice. Liver function ordered.', diagnosis: 'Jaundice — ? hepatitis',
    advice: 'Avoid alcohol and fatty food.', tests: ['LFT'], to: 'verified', pay: 1, method: 'UPI',
  });
  await visit({
    key: 't-farah',
    patient: farah, doctor: sanaActor, complaint: 'Frequent headaches', notes: 'Normal neurology.', diagnosis: 'Tension headache; screen for anaemia',
    tests: ['CBC'], to: 'entered', pay: 1, method: 'UPI',
  });
  await visit({
    key: 't-sunil',
    patient: sunil, doctor: kabirActor, complaint: 'Excess thirst and tiredness', notes: 'Pre-diabetic on last check.', diagnosis: 'Query diabetes',
    advice: 'Fast overnight for the sugar tests.', tests: ['FBS', 'PPBS'], to: 'testing', pay: 0.5, method: 'CASH',
  });
  await visit({
    key: 't-aarav',
    patient: aarav, doctor: sanaActor, complaint: 'Follow-up fever', notes: 'Better today.', diagnosis: 'Recovering viral illness', tests: ['CBC'], to: 'collected', pay: 1, method: 'UPI',
  });
  await visit({
    key: 't-meena',
    patient: meena, doctor: kabirActor, complaint: 'Kidney and lipid check', notes: 'Annual review.', diagnosis: 'Annual diabetic review', tests: ['LIPID', 'KFT'], to: 'ordered', pay: 0,
  });
  await visit({
    key: 't-neha',
    patient: neha, doctor: sanaActor, complaint: 'Tiredness and hair loss', notes: 'Thyroid and blood count planned.', diagnosis: 'Query anaemia', tests: ['CBC'],
    to: 'received', reject: true, pay: 1, method: 'UPI',
  });
  await visit({
    key: 't-pooja',
    patient: pooja, doctor: kabirActor, complaint: 'Palpitations', notes: 'ECG normal.', diagnosis: 'Anxiety; rule out anaemia', tests: ['CBC'],
    to: 'entered', values: { Haemoglobin: 21 }, retest: true, pay: 1, method: 'CARD',
  });
  heading('Done');
  console.log(`  ${Object.keys(ctx.patients).length} patients, three rounds of history, and today's bench at every stage.`);
  console.log('  Sign in with any @demo.cdlms.dev account (password Demo@12345): patient, doctor, doctor2, pathologist, reception, lab, admin.');
  console.log('  Try: doctor@ (critical result waiting), lab@ (bench + running-low), admin@ (Insights), patient@ (reports + Trends).');
}

/** Spaces out the round's samples, then moves the whole timeline back so the round becomes history. */
async function closeRound(key, days) {
  await once(key, async () => {
    for (const { id, pace } of state.paced) paceSample(id, pace);
    state.paced = [];
    shiftTimeline(days);
  });
}

/** The shelves: items, then a few weeks of use so levels look lived-in, ending with some low and one out. */
async function stockShelves() {
  const items = [
    ['EDTA tubes (purple cap)', 'TUBE', 'tubes', 120, 40],
    ['SST tubes (gold cap)', 'TUBE', 'tubes', 90, 40],
    ['Fluoride tubes (grey cap)', 'TUBE', 'tubes', 60, 30],
    ['Citrate tubes (blue cap)', 'TUBE', 'tubes', 30, 20],
    ['Urine cups', 'TUBE', 'cups', 80, 30],
    ['CBC reagent pack', 'REAGENT', 'packs', 8, 4],
    ['Glucose reagent kit', 'REAGENT', 'kits', 5, 3],
    ['Lipid panel reagent', 'REAGENT', 'kits', 4, 3],
    ['Disposable gloves (M)', 'CONSUMABLE', 'boxes', 14, 6],
    ['Alcohol swabs', 'CONSUMABLE', 'packs', 25, 10],
    ['Vacutainer needles', 'CONSUMABLE', 'boxes', 9, 5],
  ];
  for (const [name, category, unit, openingStock, lowStockThreshold] of items) {
    try {
      await who.admin.post('/inventory', { name, category, unit, openingStock, lowStockThreshold });
    } catch (error) {
      if (!String(error.message).includes('NAME_TAKEN')) throw error;
    }
  }
  const current = new Map((await who.lab.get('/inventory')).map((i) => [i.name, i]));
  const use = async (name, delta, reason, note, actor = who.lab) => {
    const item = current.get(name);
    if (!item) return;
    try {
      await actor.patch(`/inventory/${item.id}/stock`, { delta, reason, note });
    } catch (error) {
      if (!String(error.message).includes('INSUFFICIENT_STOCK')) throw error;
    }
  };
  await use('EDTA tubes (purple cap)', -35, 'USED', 'Week of collections');
  await use('SST tubes (gold cap)', -28, 'USED', 'Week of collections');
  await use('Fluoride tubes (grey cap)', -18, 'USED', 'Sugar tests');
  await use('Citrate tubes (blue cap)', -12, 'USED', 'Coagulation runs');
  await use('Citrate tubes (blue cap)', -2, 'WASTAGE', 'Two dropped and broken');
  await use('Glucose reagent kit', -5, 'USED', 'Run out after Friday batch');
  await use('CBC reagent pack', -3, 'USED', 'Analyzer calibration and runs');
  await use('Lipid panel reagent', -2, 'USED', 'Lipid runs');
  await use('Vacutainer needles', 20, 'RESTOCK', 'Delivery received', who.admin);
  step('items on the shelves with a week or two of use recorded');
}

main().catch((error) => {
  console.error(`\nStopped: ${error.message}`);
  process.exit(1);
});
