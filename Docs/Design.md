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

Light mode only (ADR-017). A warm neutral canvas, white surfaces, and **one** accent, adapted from the Thapar Nexus reference. All values are CSS variables in `frontend/styles/tokens.css`.

| Token | Value | Use |
|---|---|---|
| `--bg` | `#f5f4f1` | App background (warm off-white) |
| `--surface` | `#ffffff` | Cards, panels, tables, navbar |
| `--surface-alt` | `#faf9f7` | Inset areas, input backgrounds |
| `--surface-2` | `#efede8` | Icon tiles, neutral chips, hover fills |
| `--border` / `--border-strong` | `#e4e2dd` / `#d2d0cb` | Hairline borders (preferred over shadows) |
| `--text` / `--ink` | `#1c1b19` | Primary text; ink avatars and chart series |
| `--text-muted` | `#6a6762` | Secondary text, labels |
| `--text-subtle` | `#9a968f` | Tertiary text, disabled |
| `--accent` | `#b42318` (hover `#9c1d13`, press `#84180f`) | Primary buttons, active nav, links, focus ring — the one highlight per view |
| `--accent-soft` | `rgb(180 35 24 / 0.08)` | Selected/active backgrounds |

**Status colors — reserved for status, never decoration, always with an icon and a word:**

| Token | Meaning | Examples |
|---|---|---|
| `--critical` (`#b42318`) | Clinical danger / blocked | Critical value, allergy, rejected sample |
| `--warning` (`#b7791f`) | Needs attention | Out-of-range value, long wait, retest, tube mismatch, low stock |
| `--success` (`#0f766e`) | Done / safe | Verified, within range, paid, session verified |
| `--info` (`#1d4ed8`) | Neutral progress | Booked, in testing, prep instructions |

**Accent vs. critical.** The accent and "critical" share a red, so critical states are always distinguished by *form*: an octagon alert icon, an explicit word ("Critical", "Rejected", "Allergy"), and a tinted banner or badge. A plain accent-red element (a button, the active nav link) never looks like an alert.

**No role colors.** Every role uses the same accent; roles are identified by name (role badge, "<Role> workspace" under the logo).

**Tube-cap colors** (lab screens only) match real vacutainer caps: Lavender (EDTA), Red (plain), Gold (SST), Light blue (citrate), Grey (fluoride), Green (heparin). They appear only on the small cap icon inside a neutral chip, always with the tube name.

### 2.2 Typography

| Role | Font | Notes |
|---|---|---|
| Everything (UI + headings) | **Outfit** (via `next/font`) | Headings semibold (600) with tight tracking (-0.02 to -0.03em); body 14–16px |
| Numbers, IDs, codes | **IBM Plex Mono** | Sample codes, tokens, lab values, times; `tabular-nums` |

Scale (px): 12 / 13 / 14 / 16 / 18 / 22 / 30 / 40 / 60 (login headline). Line length for reading text max ~70ch.

### 2.3 Space, shape, depth

- **4px grid.** Common steps: 4, 8, 12, 16, 24, 32, 48.
- **Radius:** 10px buttons and inputs, 14px inner boxes, 18px cards and panels, full for pills and avatars.
- **Depth:** borders first; `--shadow-sm` on resting cards, `--shadow-md` on hover and popovers, `--shadow-lg` for modals.
- **Density:** *Comfortable* (patient, 44px touch targets), *Compact* (staff tables), *Touch* (lab bench, 56px targets).

### 2.4 Iconography & imagery

- **Tabler Icons**, 1.5px stroke, 16/20/24px. One icon style throughout.
- Empty states use a single line-art icon composition in the brand tint + one sentence + one action — no stock photos, no cartoon mascots.
- The only "art" is the login/brand panel: an animated, slow-moving gradient mesh with a faint ECG/pulse line — calm, not busy.

### 2.5 Motion & effects

Motion explains change and adds polish; it never blocks work. Built with `motion` (Framer Motion) and CSS. Effect ideas are adapted from ObsidianUI (text reel, cursor light, scroll reveal) and rebuilt in our stack.

| Effect | Where | Spec |
|---|---|---|
| **Scroll reveal** | Dashboard sections | Fade + 18px rise the first time a block scrolls into view, 500ms, staggered 50ms |
| **Cursor light** (`GlowCard`) | Cards and panels | A faint accent radial glow follows the pointer inside the card |
| **Hover lift** | Clickable cards, buttons | `translateY(-1..-2px)` + `--shadow-md`; buttons press to `scale(0.98)` |
| **Sliding nav pill** | Command bar | Active-link ink pill animates between links (shared layout) |
| **Text reel** | Login headline | Words roll up with a slight blur ("precisely. / clearly. / safely. / together.") |
| **Count-up** | KPI tiles | Numbers count up on first load only |
| **Live dot** | "Session verified", "Tracking live", live queue | A dot with a soft expanding ring |
| **Journey fill** | Sample journey | Rail fills to the current stop; current stop breathes |
| **Shimmer** | Loading placeholders | Warm neutral shimmer |

`prefers-reduced-motion` turns all of these into instant state changes.

### 2.6 Light mode only

The platform runs in light mode only (ADR-017): Mantine is forced to light, `color-scheme: light` is set, and there is no theme toggle.

## 3. Layout & Navigation

### 3.1 Clinical command bar (all roles)

```
      ┌────────────────────────────────────────────────────────────────────────────┐
      │ CDLMS · Reception     Today   Patients   Register        ⌕ Ctrl K  10:42  [+ Register patient] (KM) │
      └────────────────────────────────────────────────────────────────────────────┘
                  ▔▔▔▔▔ ink pill slides under the active link
                     page content, max 1200px, centered
```

- A white **floating** bar (rounded, hairline border, soft shadow) sitting 8px below the top edge, sticky; 64px tall, compacting to 58px once the page scrolls. Three-column layout so the links stay truly centred.
- Left: wordmark + the role's workspace name. Middle: the role's links, labelled (no icon-only links) — only links to screens that exist. Right: patient search (opens the Ctrl+K palette; staff only), a live clock in IBM Plex Mono, notifications, **one** red primary action for the role (e.g. "Register patient"), and the account menu.
- The active link sits on a dark **ink pill** that slides between links (shared-layout animation; instant under reduced motion).
- **Below 992px:** staff get a menu button that opens a drawer with the same links; search becomes an icon; the clock hides.

### 3.2 Patient shell

Same command bar (without search). On phones, a **bottom tab bar** (Home, Appointments, Reports, Bills, Profile) replaces the middle links. Warmer copy and larger type.

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
| **Skeletons** | Shaped like the real content (rows, cards, tracker), with the warm shimmer. Never a full-page spinner. |
| **Glow card / panel** | Every card and panel uses `GlowCard` (cursor light); `Panel` adds a title, subtitle and a right-side slot. |
| **KPI tile** | Label, big mono number (count-up), change vs. last period (green when good, amber when bad — "good" can mean *down*, e.g. turnaround), optional sparkline. Icons sit in a neutral tile. |
| **Widget registry** | Dashboards are server-driven (ADR-019): `GET /dashboard/{role}` returns widgets `{ type, title, span, data }` and `components/dashboard/widgets/` maps each `type` to a component. Unknown types are skipped. Real data only — no mock or demo data anywhere. |
| **Upcoming module** | A module that isn't built yet appears as an `upcoming` widget (name, phase, one-line description) — never a fake button or fake numbers. |
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

- One result fills the screen: big value in mono, the **range bar**, the **trend sparkline** (the patient's earlier verified values for that parameter, oldest to newest), sample details (collected/received times, tube, who entered it and on which analyzer), earlier attempts if it's a retest.
- Critical values show a red banner at the top.
- Keyboard-driven: `V` verify (with confirm), `R` return for retest (reason picker opens), `J/K` next/previous, `Esc` back to queue. After a decision it moves to the next result in the queue.
- A small progress indicator: "7 of 23 in queue".

### 5.3 Lab Bench Mode (Lab Technician)

Designed for a tablet on the bench, gloves on:

- **Scan-first:** a big, always-focused "Scan or type sample code" field. A QR scan (camera, if the QR stretch feature is built) or typed code jumps straight to that sample's next action.
- 56px touch targets, large tube chips, one primary action per screen ("Mark received", "Enter result").
- Tube-type mismatch appears as an amber warning while collecting (it needs an explicit confirmation) and again before the sample can be marked received.
- Returned-for-retest samples are pinned at the top of the queue with the pathologist's reason.
- **Result entry:** one card per test with a large input per parameter, the reference range under each name, the range bar and flag appearing as you type, `Enter` moving to the next field (and submitting on the last), the analyzer remembered from last time, and — on a retest — "last time 38" beside each field. A critical value shows a banner. "Reject" is offered for a sample that is used up or unusable.

### 5.4 Reception Live Queue

A live board, one column per doctor (built in Phase 03):

- **Now with the doctor** — an ink card with the token in large IBM Plex Mono, a pulsing live dot and one slow accent glow; the doctor's **Finish** button sits on it.
- **Room is free** — a dashed bar; for the doctor it carries **Call T-0xx** for the next in line. "Call in" is never offered while someone is already in the room.
- **Waiting** — token chips in check-in order; the next patient is tinted accent; minutes waited turn amber after 30. Rows slide up (layout animation) when someone is called; other moves (no-show, cancel) sit in a "…" menu.
- Refreshes every 10 s; the same board appears as a widget on the reception, doctor and admin dashboards.
- **Waiting-room screen** (TV mode): full screen, large tokens, **no patient names**.
- **Sample alerts (Phase 07):** when the lab rejects a sample, a "Patients to call back" card appears right under the tiles and the bell shows the open count. Each item names the patient, the sample and the reason, with the phone number, **Patient called** and **Open record**. The Samples page lists them all.

### 5.5 Doctor Consult Workspace

A split view that keeps the patient in front of the doctor:

- **Left rail (sticky):** name, patient ID, age · gender, **allergies pinned in a red safety banner** (or a green "no known allergies"), blood group, medical history, and the last five visits (each opens that consultation).
- **Main:** vitals (six optional fields, server limits), chief complaint, clinical notes (marked *clinicians only*), diagnosis, a **prescription builder** — formulary autocomplete that pre-fills the usual strength, quick-pick frequency (`1-0-1`, `SOS` …), duration and instructions, animated rows, unfinished rows flagged amber — a **Lab tests** panel, then advice and follow-up with 3-day / 1-week / 2-week / 1-month shortcuts.
- **Order tests drawer:** search the catalog (name, code or category), filter by category, or tap a **panel** (Fever workup, Diabetes, Annual check-up, Fatigue / anaemia). Each row shows the tube chip, turnaround, price and a prep marker; tests already on the order are ticked and locked. A live footer sums it up — tubes needed (`Fluoride ×2 · EDTA · SST`), "results in about 1 day", **what the patient must prepare**, the total — with Routine / Urgent and a note for the lab. `Ctrl+Enter` orders. The order reaches the lab queue immediately; lines can be removed from the panel, and the finish dialog mentions tests already with the lab.
- **Autosave** 1.2 s after typing stops, with a status line ("Saving…", "Saved 10:44 pm", "Not saved — check the highlighted fields"). `Ctrl+S` saves now, `Ctrl+Enter` finishes.
- **Finish** opens a confirmation that says exactly what will happen (prescription with N medicines, or none; record locked; visit completed) and warns about unfinished rows. Afterwards the page becomes the read-only summary with **View PDF / Download**.
- **Entry points:** "Start consultation" / "Call T-00x" on the queue and day agenda, "Open consultation" for the patient in the room, and a **Resume consultation** card at the top of the doctor's dashboard while one is open.

### 5.6 Patient Home

Card-first, answers "what do I need to know right now?":

- Warm greeting (serif headline), then cards in priority order: **results ready**, **active sample journeys**, **next appointment** (with prep instructions and a calendar add), **unpaid bills**.
- Reports open in a clean reader view with range bars and plain-language notes, plus "Download PDF".
- Everything reachable in two taps on a phone.

### 5.7 Admin Insight Dashboard

- KPI tiles (patients seen, revenue, reports issued, median TAT) with the change against the previous period, sparkline trends and count-up on load.
- **TAT heatmap** (test type × day), revenue area chart, patients bar chart, most-ordered tests bar chart, a turnaround table with a bar for where the time goes, and a staff activity table.
- One global date-range picker (7 / 30 / 90 days) drives every widget. Every chart has an accessible data-table toggle. Charts use the accent colour and ink only; the heatmap is one accent ramp with empty days left blank.
- Built at `/admin/insights`; the admin's home (`/admin`, "Overview") keeps today's live queue, access log and running-low card.

### 5.7a Inventory

- A table of items: name and category, level with a bar (half full at the threshold, amber when low, red when out), threshold and a badge. Out-of-stock and low items sort first; a healthy row stays quiet.
- Clicking an item opens a side panel: the level, a form (reason chips, quantity, direction for corrections, note) and the history of changes with who made them. Admins also get "Add item" and "Edit details" (including retiring an item).
- The lab and admin see a "Running low" card on their dashboard and a count on the bell, only when something is below its level.

### 5.8 Login

Split screen: left, a warm ink panel with one slow accent glow, a rolling text-reel headline, a pulse line and three product points; right, a focused sign-in form that eases in. The panel hides below 992px. After login the user lands directly in their role's workspace — nobody picks a role. Registration is a short two-step form for patients. In local dev only, a row of demo-account chips fills the form for quick testing.

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
- Lab tests: ordered tests with a "Before your test" prep checklist and the order number to show at the lab
- Sample journey ★ (§5.1), per active sample — on the Lab tests page, one line per tube; a rejected sample says "We need a new sample" and the redraw starts a new line
- Reports (reader view + PDF download) — each test's values with a range bar, flag and unit, and the pathologist's verification stamp
- Bills: unpaid amount first, invoices with status and a PDF view/download; the invoice shows the lines, any discount (who gave it and why), payments received and what is still owed

### Doctor (`/doctor`)
- Today: schedule timeline + next patient card
- Patient search (basic details for everyone; badge on patients they can open — ADR-015)
- Patient record (full EMR, only with a care relationship; otherwise a friendly "no appointment with this patient" state, not an error page)
- Consult workspace ★ (§5.5): notes, prescription builder, order tests
- Order tests directly from a patient's record (patients under their care)
- Lab reports: verified reports for tests they ordered, with the same reader view and PDF

### Pathologist (`/pathology`)
- Verification queue (critical first, then out-of-range, then oldest; retest count shown)
- Focus mode ★ (§5.2): verify or return for retest
- My verifications (history)
- *(Profile with a signature image is deferred; reports print the name, qualification and registration number.)*

### Receptionist (`/reception`)
- Live queue ★ (§5.4) with walk-in token issuance
- Patient registration form
- Appointments calendar
- Billing counter ★: "To collect" list (oldest first) searchable by patient or invoice number; the invoice page has a **Take payment** panel (full balance or half in one tap, cash / card / UPI with a reference, never more than is owed) and a **Discount** panel (presets within the front-desk cap, mandatory reason, recorded with the giver's name). Dashboard widgets: bills to collect and today's takings by method
- Admins get the same Billing pages for review and larger discounts
- Samples to redraw (the sample-rejected inbox)

### Lab Technician (`/lab`)
- Bench home ★ (§5.3): scan field + incoming orders queue (retests pinned on top) and "Tubes to set out" by cap colour
- Orders: every open order, urgent first; order detail with tubes to collect, the doctor's note and the prep to check
- Samples ★ (§5.3): the scan field, then "To collect", "To receive" and "To test" lists (urgent, redraws and retests first)
- Reports to send: verified reports waiting for dispatch (critical first); each opens the report with a **Send** panel — email or download link, SMS shown but off — and afterwards shows when the patient opened it
- Sample page: **collection** (tube chips with the needed one pre-selected, body site chips, amber wrong-tube warning that needs an explicit confirmation), **receipt check** ("Mark received" or "Reject sample" with a reason), the printable **label with a QR code**, and the chain of custody
- Result entry per analyzer/test, with range bar preview and a secondary "Reject sample" action (exhausted / degraded / other)
- My processed samples
- Inventory ★ (§5.7a): view, record restock / use / wastage / correction, low-stock warnings

### Admin (`/admin`)
- Insight dashboard ★ (§5.7)
- Test catalog: price, tube, turnaround, patient prep, parameters with normal and critical ranges; retire tests
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

- Contrast ≥ 4.5:1 for text, ≥ 3:1 for UI components and chart marks.
- Visible focus ring (2px brand outline + offset) on every interactive element; logical tab order; skip-to-content link.
- Everything usable by keyboard, including drag-and-drop alternatives on the queue board.
- Status never conveyed by color alone (icon + text).
- Live regions announce queue changes, toasts and form errors to screen readers.
- Touch targets ≥ 44px (patient), ≥ 56px (lab bench).
- Respect `prefers-reduced-motion`; support 200% zoom without horizontal scroll.

---

## 9. Responsive Targets

| Breakpoint | Primary users | Notes |
|---|---|---|
| < 640px (phone) | Patients | Bottom tab bar, single column, sheets instead of modals |
| 640–992px (tablet) | Lab technicians, reception | Bench mode, command-bar links move into a drawer |
| ≥ 992px (desktop) | Doctors, pathologists, admin, reception | Full command bar, split views, dense tables |

---

## 10. Performance Feel

- Skeletons within 100ms of navigation; content usually within 300ms.
- Prefetch likely next views (hovered table rows, next queue item in focus mode).
- Optimistic updates for low-risk actions (moving a queue card, marking read); clinical actions wait for the server and show progress on the button.
- Fonts via `next/font` (no layout shift); icons tree-shaken; charts loaded lazily.

---

## 11. Implementation Notes

- **Stack:** Mantine 9 (theme tokens mapped to the variables in §2.1), `@mantine/spotlight` for the command palette, Mantine Charts (Recharts) for dashboards, `motion` for animation, `@tabler/icons-react` for icons, `next/font` for Outfit / IBM Plex Mono. See [[TechSpecifications]].
- **Structure:** `components/ui/` (primitives: GlowCard, Panel, Reveal, TextReel, StatusBadge, RangeBar, TubeChip, SafetyBanner, Sparkline, KpiTile, EmptyState, SampleJourney), `components/shell/` (WorkspaceShell command bar, command palette, menus), `components/dashboard/` (`RoleDashboard` + `widgets/` registry), `components/patients/` (search, forms, record view, registration slip).
- **Charts:** series use `--ink` and `--accent` only; heatmaps use a single accent intensity scale.
- **Build order:** Phase 01 delivered the theme and tokens, login/register (§5.8), the command-bar shell for all six roles and the Ctrl+K palette. Phase 02 added server-driven widget dashboards (real data only) and the patient screens. Each later phase adds its screens on the same primitives and registers new widget types for its dashboards.

## 12. Design Review Checklist (every UI PR)

- [ ] Uses tokens — no hard-coded colors, spacing or font sizes
- [ ] Uses only the canvas, text, one accent and status colors (no new hues)
- [ ] Loading (skeleton), empty and error states designed
- [ ] Keyboard-accessible, visible focus, no color-only status
- [ ] Responsive at phone / tablet / desktop widths for its users
- [ ] Copy follows §7; API errors mapped to friendly messages
- [ ] Clinical/irreversible actions use confirm + reason, never undo
- [ ] Reduced-motion respected
