# Design

The design spec for the Next.js frontend (see [[TechSpecifications]]). It sets the visual language, the interaction patterns, and the "signature" experiences that should make this product feel remarkable rather than like a template admin panel. Every screen is built against this doc; a PR that drifts from it either updates this doc or is a design bug.

---

## 1. Design Concept — "Calm Precision"

A clinic and a lab are high-stakes, high-interruption places. The UI's job is to make people **faster and safer**, and to make patients **feel looked after**. The ambition is "clean and striking", but never at the cost of clinical clarity.

Think **Linear's speed and keyboard flow** for staff, **Apple Health's warmth and clarity** for patients, and **package-tracking delight** for the sample journey.

Five principles, in priority order:

1. **Safety first, always visible.** Allergies, critical values, rejections and "not verified yet" states are impossible to miss and never silently dismissible. Color is never the only signal — every status has an icon and a word.
2. **One role, one purpose-built workspace.** Each of the six roles gets a shell designed around *their* job — not one generic UI with hidden buttons. A UI control for something the backend would reject is a design bug (ties to [[Rules]], [[Security]]).
3. **Speed is a feature.** Keyboard shortcuts and a command palette for staff, instant-feeling interactions (skeletons, optimistic updates, prefetch), and the fewest possible clicks for the ten things each role does all day.
4. **Show the journey, not the database.** Statuses become timelines, numbers become ranges and trends, lists become queues with clear "what's next".
5. **Quiet by default, loud when it matters.** A calm, mostly-neutral canvas so that the rare red banner actually means something.

---

## 2. Visual Language

### 2.1 Color

A neutral, slightly cool canvas with one brand color and strictly reserved semantic colors. All colors are CSS variables (Mantine theme + custom tokens) with light and dark values.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg` | `#F7F8FA` | `#0B0F14` | App background |
| `--surface` | `#FFFFFF` | `#11161D` | Cards, panels, tables |
| `--surface-raised` | `#FFFFFF` + shadow | `#161C24` | Popovers, modals, command palette |
| `--border` | `#E4E7EC` | `#232B36` | Hairline 1px borders (preferred over shadows) |
| `--text` | `#0E1726` | `#E6EAF0` | Primary text |
| `--text-muted` | `#5B6576` | `#98A2B3` | Secondary text, labels |
| `--brand` | `#0E9384` (teal) | `#2ED3B7` | Primary actions, focus, links, brand moments |
| `--brand-soft` | `#E6F6F4` | `#0F2E2B` | Selected rows, active nav, soft badges |

**Semantic colors — reserved, never decorative:**

| Token | Meaning | Examples |
|---|---|---|
| `--critical` (red `#D92D20`) | Clinical danger / blocked | Critical lab value, allergy, sample rejected, destructive confirm |
| `--warning` (amber `#DC6803`) | Needs attention | Out-of-range (non-critical) value, low stock, returned for retest, tube-type mismatch |
| `--success` (green `#079455`) | Done / safe | Verified, within range, paid, report dispatched |
| `--info` (blue `#1570EF`) | Neutral progress | In testing, scheduled, informational notices |

**Role accents.** Each role's shell has a thin accent (top bar stripe, avatar ring, active-nav tint) so a person on a shared computer always knows which workspace they're in:

| Role | Accent |
|---|---|
| Patient | Teal (brand) |
| Doctor | Indigo `#444CE7` |
| Pathologist | Violet `#7A5AF8` |
| Receptionist | Sky `#0086C9` |
| Lab Technician | Cyan `#0E7090` |
| Admin | Slate `#475467` |

Accents are only used for wayfinding, never for status.

**Tube-cap colors** (lab screens) match real vacutainer caps so technicians recognise them instantly: Lavender (EDTA), Red (plain/serum), Gold (SST), Light blue (citrate), Grey (fluoride), Green (heparin). Always shown as a cap chip **plus** the tube name.

### 2.2 Typography

| Role | Font | Notes |
|---|---|---|
| UI text | **Inter** (variable, via `next/font`) | 14px base for staff, 16px base for patient screens |
| Numbers, IDs, codes | **JetBrains Mono** | Sample codes (`LAB-20260927-0042`), lab values, tokens; `tabular-nums` everywhere numbers line up |
| Patient-facing headlines | **Instrument Serif** | A touch of editorial warmth on the patient portal and login only ("Good morning, Asha") |

Scale (rem): 0.75 / 0.8125 / 0.875 / 1 / 1.125 / 1.375 / 1.75 / 2.25 / 3. Headings are semibold (600), never bold 700 walls. Line length for reading text max ~70ch.

### 2.3 Space, shape, depth

- **4px grid.** Common steps: 4, 8, 12, 16, 24, 32, 48.
- **Radius:** 8px inputs/buttons, 12px cards, 16px modals/sheets, full for pills and avatars.
- **Depth:** borders first, shadows only for things that float (menus, modals, toasts, command palette). One soft shadow token.
- **Density:** *Comfortable* (patient, 44px min touch targets) and *Compact* (staff tables, 36px rows). Lab bench screens use *Touch* (56px targets — gloved hands on a tablet).

### 2.4 Iconography & imagery

- **Tabler Icons**, 1.5px stroke, 16/20/24px. One icon style throughout.
- Empty states use a single line-art icon composition in the brand tint + one sentence + one action — no stock photos, no cartoon mascots.
- The only "art" is the login/brand panel: an animated, slow-moving gradient mesh with a faint ECG/pulse line — calm, not busy.

### 2.5 Motion

Motion explains change; it never decorates. Built with the `motion` library (Framer Motion).

| Pattern | Spec |
|---|---|
| Micro (hover, press, toggles) | 120–150ms, ease-out |
| Panels, drawers, modals | 200–250ms, spring (stiff, no bounce) |
| Sample-journey progress | Line "fills" to the current stage, current dot breathes (2s loop) |
| List changes (queues) | Items animate in/out and reorder with layout animation |
| Number changes (KPI tiles) | Count-up on first load only |
| Page transitions | Fade + 4px rise, 180ms |

`prefers-reduced-motion` disables all non-essential motion (breathing, count-up, mesh), leaving instant state changes.

### 2.6 Dark mode

Full dark theme from day one (labs often run in dim light; staff work night shifts). Follows the OS by default, with a toggle in the user menu, remembered per browser. Charts, tube chips and status colors all have tuned dark variants — not just inverted.

---

## 3. Layout & Navigation

### 3.1 Staff shell (Doctor, Pathologist, Receptionist, Lab Technician, Admin)

```
┌────────────┬─────────────────────────────────────────────────────┐
│  ◉ CDLMS   │  Breadcrumbs            [⌘K Search…]   🔔  ◐  (Avatar)│  ← role-accent stripe on top edge
│            ├─────────────────────────────────────────────────────┤
│  Today     │                                                     │
│  Queue     │                 Page content                        │
│  Patients  │                                                     │
│  …         │                                                     │
│            │                                                     │
│  ──────    │                                                     │
│  ? Help    │                                                     │
└────────────┴─────────────────────────────────────────────────────┘
```

- **Left sidebar**, collapsible to icons (remembered). Max ~6 items per role; the first item is always that role's "Today"/home view.
- **Top bar:** breadcrumbs, command palette trigger, notifications, theme toggle, user menu (name, role badge, logout).
- **Content width:** fluid for tables/boards, max 1200px for forms and reading views.

### 3.2 Patient shell

Mobile-first. On phones: a **bottom tab bar** (Home, Appointments, Reports, Bills, Profile). On desktop: a simple top nav with the same five items and a centered 960px column. Larger type, more whitespace, warmer copy.

### 3.3 Command palette (staff) — `Ctrl/⌘ + K`

One box to go anywhere and do anything:

- **Search patients** by name, phone or patient ID (summary results only, per ADR-015).
- **Find a sample** by code (`LAB-…`) or by scanning its QR.
- **Jump** to any screen of your role.
- **Actions:** "New walk-in token", "Register patient", "Collect sample", "Open verification queue"…
- Recent items first; fuzzy matching; every result shows its keyboard shortcut if it has one.

### 3.4 Keyboard

`?` opens a shortcut cheatsheet on every staff screen. Global: `Ctrl/⌘+K` palette, `G then T` go to Today, `/` focus search. Screen-specific shortcuts are listed in §5. Shortcuts never fire while typing in an input.

---

## 4. Core Components & Patterns

| Pattern | Rules |
|---|---|
| **Status badge** | Icon + label + semantic color. Same component for appointment, sample, invoice, stock statuses. Never color alone. |
| **Data table** | Sticky header, compact rows, zebra off, row hover, keyboard row navigation (`J/K`, `Enter` opens), column sort, filter chips above, empty/loading/error states built in. Bulk actions only where safe. |
| **Forms** | Labels above fields, inline validation on blur, server errors mapped to fields (API `fields` object), primary action bottom-right and `Ctrl+Enter` to submit. Autosave drafts for long clinical notes. |
| **Range bar** | For every lab value: the reference range drawn as a band, the value as a marker, out-of-range sides tinted amber, critical zone red. Used in results, reports and the pathologist view. |
| **Trend sparkline** | Patient's previous values for the same test, next to the current value. |
| **Tube chip** | Real cap color + tube name; turns into a warning chip on mismatch. |
| **Safety banner** | Allergies (doctor/pharmacy contexts) and critical values: pinned at the top of the relevant view, red, cannot be collapsed. |
| **Toasts** | Bottom-right, auto-dismiss 5s, "Undo" only for reversible actions. Clinical sign-offs (verify, reject, return for retest) never get undo — they get a confirm step instead. |
| **Confirm dialogs** | Only for irreversible/clinical actions. Title states the consequence ("Reject sample LAB-…? The patient will need a redraw."), the confirm button repeats the verb, reasons required inline. |
| **Skeletons** | Shaped like the real content (rows, cards, tracker). Never a full-page spinner. |
| **Empty states** | Icon + one sentence explaining why it's empty + one action. E.g. "No samples waiting. New orders appear here automatically." |
| **Errors** | Plain language + what to do next + a "Try again" button. API error codes map to friendly copy (e.g. `NO_CARE_RELATIONSHIP` → "You don't have an appointment with this patient. Ask the front desk to book them in."). |
| **Notifications** | Bell with unread count; items are actionable (click → the sample/patient). Rejections and critical values also raise a toast. |
| **Live updates** | Queues and boards refresh in the background (polling via TanStack Query to start); changes animate in, and new urgent items get a subtle highlight. |

---

## 5. Signature Experiences

These are the moments that should make an evaluator (and a real user) stop and say "oh, nice".

### 5.1 The Sample Journey (patient + staff)

The hero interaction ([[PRD]] §6). A vertical "subway line" on mobile, horizontal on desktop:

```
 ●━━━━━━━●━━━━━━━●━━━━━━━◉ ─ ─ ─ ─ ○ ─ ─ ─ ─ ○
Ordered  Collected  At lab   Testing    Verified   Report ready
9:02     9:40       10:15    (now)      usually by 5 PM
```

- Completed stops are filled with their timestamp; the current stop **breathes**; future stops are hollow and muted.
- **Patient view:** plain-language stage names and an **"usually ready by …" estimate** from that test's average TAT (Phase 09 data; hidden until available). A retest stays at "Testing" with no alarm. A rejection shows a clear, kind message: "We need a new sample — the front desk will contact you" and the journey restarts on a new line.
- **Staff view:** the same line plus who did each step, analyzer, and the full event log on expand. A **return for retest** is drawn as a loop-back arc from "Result entered" to "Testing" labelled with the reason; a **rejection** forks to a new line for the redraw sample.

### 5.2 Pathologist Focus Mode

Verification is deep, careful work — so it gets a distraction-free mode:

- One result fills the screen: big value in mono, the **range bar**, the **trend sparkline**, sample details (collected/received times, tube), earlier attempts if it's a retest.
- Critical values show a red banner at the top.
- Keyboard-driven: `V` verify (with confirm), `R` return for retest (reason picker opens), `J/K` next/previous, `Esc` back to queue.
- A small progress indicator: "7 of 23 in queue".

### 5.3 Lab Bench Mode (Lab Technician)

Designed for a tablet on the bench, gloves on:

- **Scan-first:** a big, always-focused "Scan or type sample code" field. A QR scan (camera, if the QR stretch feature is built) or typed code jumps straight to that sample's next action.
- 56px touch targets, large tube chips, one primary action per screen ("Mark received", "Enter result").
- Tube-type mismatch appears as an amber warning before the sample can be marked received.
- Returned-for-retest samples are pinned at the top of the queue with the pathologist's reason.

### 5.4 Reception Live Queue

A live board for the front desk:

- Columns **Waiting → With doctor → Done** per doctor, token cards with patient name, time waited (turns amber after a threshold), and appointment type.
- Drag a card (or use keyboard) to move it; issuing a walk-in token is one palette action (`N`).
- **"TV mode"** (stretch): a full-screen, anonymised token display for the waiting room ("Token A-17 → Room 2").
- A sample-rejected inbox with a "Call patient" action and one-click rebooking.

### 5.5 Doctor Consult Workspace

A split view that keeps the patient in front of the doctor:

- **Left rail:** patient summary — name, age, **allergies pinned in a red safety banner**, active medications, recent visits, recent lab results with range indicators.
- **Main:** notes editor (autosave), diagnosis, then a **prescription builder** (add drug → dose → frequency → duration as fast keyboard rows) and an **"Order tests" drawer** (search catalog, test chips with price and prep instructions, which the patient will see).
- Finishing the consult is one action that saves everything and returns to Today's schedule with the next patient highlighted.

### 5.6 Patient Home

Card-first, answers "what do I need to know right now?":

- Warm greeting (serif headline), then cards in priority order: **results ready**, **active sample journeys**, **next appointment** (with prep instructions and a calendar add), **unpaid bills**.
- Reports open in a clean reader view with range bars and plain-language notes, plus "Download PDF".
- Everything reachable in two taps on a phone.

### 5.7 Admin Insight Dashboard

- KPI tiles (today's patients, revenue, samples in progress, avg TAT) with sparkline trends and count-up on load.
- **TAT heatmap** (test type × day), revenue area chart, most-ordered tests bar chart, staff throughput table.
- One global date-range picker drives every widget. Every chart has an accessible data-table toggle.

### 5.8 Login

Split screen: left, the animated brand panel (gradient mesh + pulse line + one line of product copy); right, a focused sign-in card. After login the user lands directly in their role's workspace — nobody picks a role. Registration is a short two-step form for patients. In local dev only, a row of demo-account chips fills the form for quick testing.

---

## 6. Screen Inventory (by role)

Route prefixes from [[TechSpecifications]] §3. ★ = signature experience from §5.

### Patient (`/patient`)
- Login / registration ★ (§5.8)
- Home ★ (§5.6)
- Book appointment (pick doctor → date → slot, with availability shown)
- My appointments (upcoming / past)
- Medical history (read-only EMR)
- Prescriptions (list + PDF download)
- Sample journey ★ (§5.1), per active sample
- Reports (reader view + PDF download)
- Invoices / billing

### Doctor (`/doctor`)
- Today: schedule timeline + next patient card
- Patient search (basic details for everyone; badge on patients they can open — ADR-015)
- Patient record (full EMR, only with a care relationship; otherwise a friendly "no appointment with this patient" state, not an error page)
- Consult workspace ★ (§5.5): notes, prescription builder, order tests
- Verified lab reports for their patients

### Pathologist (`/pathology`)
- Verification queue (oldest first, critical and out-of-range flagged, retest count shown)
- Focus mode ★ (§5.2): verify or return for retest
- My verifications (history)
- Profile (qualification, registration number, signature image used on reports)

### Receptionist (`/reception`)
- Live queue ★ (§5.4) with walk-in token issuance
- Patient registration form
- Appointments calendar
- Billing counter (invoice lookup, mark paid, discount with logged staff ID)
- Sample-rejected inbox

### Lab Technician (`/lab`)
- Bench home ★ (§5.3): scan field + incoming orders queue (retests pinned on top)
- Sample collection (tube type chips, body site, timestamp)
- Receipt check (accept / reject with reason)
- Result entry per analyzer/test, with range bar preview and a secondary "Reject sample" action (exhausted / degraded / other)
- My processed samples
- Inventory (view / restock, low-stock warnings)

### Admin (`/admin`)
- Insight dashboard ★ (§5.7)
- Staff accounts (create, deactivate, assign role; pathologist registration details)
- Inventory management
- Record access log (filter by patient, staff member, date)

---

## 7. Content & Microcopy

| Audience | Voice | Example |
|---|---|---|
| Patients | Warm, plain language, no jargon, reassuring | "Your sample reached the lab. We'll let you know as soon as your report is ready." |
| Staff | Terse, precise, domain terms welcome | "Hemolyzed — rejected. Front desk notified." |

- Buttons are verbs ("Verify result", "Issue token"), never "OK"/"Submit".
- Times are relative when recent ("12 min ago"), absolute otherwise; always show the full timestamp on hover.
- Dates in `27 Sep 2026` format; times in the clinic's locale (12h or 24h per setting).
- Never blame the user in error messages.

---

## 8. Accessibility (target: WCAG 2.2 AA)

- Contrast ≥ 4.5:1 for text, ≥ 3:1 for UI components and chart marks, in both themes.
- Visible focus ring (2px brand outline + offset) on every interactive element; logical tab order; skip-to-content link.
- Everything usable by keyboard, including drag-and-drop alternatives on the queue board.
- Status never conveyed by color alone (icon + text).
- Live regions announce queue changes, toasts and form errors to screen readers.
- Touch targets ≥ 44px (patient), ≥ 56px (lab bench).
- Respect `prefers-reduced-motion` and `prefers-color-scheme`; support 200% zoom without horizontal scroll.

---

## 9. Responsive Targets

| Breakpoint | Primary users | Notes |
|---|---|---|
| < 640px (phone) | Patients | Bottom tab bar, single column, sheets instead of modals |
| 640–1024px (tablet) | Lab technicians, reception | Bench mode, collapsible sidebar |
| ≥ 1024px (desktop) | Doctors, pathologists, admin, reception | Full sidebar, split views, dense tables |

---

## 10. Performance Feel

- Skeletons within 100ms of navigation; content usually within 300ms.
- Prefetch likely next views (hovered table rows, next queue item in focus mode).
- Optimistic updates for low-risk actions (moving a queue card, marking read); clinical actions wait for the server and show progress on the button.
- Fonts via `next/font` (no layout shift); icons tree-shaken; charts loaded lazily.

---

## 11. Implementation Notes

- **Stack:** Mantine (theme tokens mapped to the variables in §2.1), `@mantine/spotlight` for the command palette, Mantine Charts for dashboards, `motion` for animation, `@tabler/icons-react` for icons, `next/font` for Inter / JetBrains Mono / Instrument Serif. See [[TechSpecifications]].
- **Structure:** `components/ui/` (design-system primitives: StatusBadge, RangeBar, TubeChip, SafetyBanner, EmptyState, KpiTile…), `components/shell/` (role shells, sidebar, top bar, palette), and feature folders per role.
- **Build order:** Phase 01 delivers the theme and tokens, light/dark mode, login/register (§5.8), all six role shells with navigation and the command palette skeleton, and polished empty dashboards. Each later phase builds its screens on these primitives; the signature experiences land with their phases (sample journey in 07/08, focus mode in 08, admin insights in 09).

## 12. Design Review Checklist (every UI PR)

- [ ] Uses tokens — no hard-coded colors, spacing or font sizes
- [ ] Works in light and dark mode
- [ ] Loading (skeleton), empty and error states designed
- [ ] Keyboard-accessible, visible focus, no color-only status
- [ ] Responsive at phone / tablet / desktop widths for its users
- [ ] Copy follows §7; API errors mapped to friendly messages
- [ ] Clinical/irreversible actions use confirm + reason, never undo
- [ ] Reduced-motion respected
