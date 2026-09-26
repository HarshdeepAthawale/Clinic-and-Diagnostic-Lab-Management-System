# Non-Goals

Explicitly out of scope for this project. Listed so scope creep has something concrete to push back against — if a request lands here, it needs a deliberate decision to move it into scope, not a quiet yes.

- **Native mobile apps** (iOS/Android) — web application only, a responsive Next.js web UI at most.
- **Insurance/claims processing integration** — billing is direct patient payment/invoicing only, no insurer-facing workflow.
- **Multi-branch / multi-tenant clinic support** — the system models a single clinic + lab instance, not a chain with shared/isolated tenant data.
- **Real barcode/QR scanner hardware integration** — sample QR codes are generated and can be "printed" on a label for demo purposes; actual scanning hardware integration is not built.
- **Payment gateway integration** — invoices track paid/unpaid status; actual online payment processing (Stripe/Razorpay/etc.) is not implemented unless explicitly promoted from a stretch decision (see [[OpenQuestions]]).
- **Telemedicine / video consultations** — appointments are in-person/queue-based; no video call feature.
- **Stretch features not selected** — of the items listed in [[PRD]] section 7, only 2–3 will be built per [[ImplementationPlan]]; the rest remain explicitly out of scope for this iteration, not silently dropped (tracked, not forgotten — that's the point of separating this from a plain backlog).
- **Full regulatory certification** (e.g., actual NABL accreditation) — the system is designed with accreditation-style audit-trail requirements in mind for realism/domain credibility, but does not claim or pursue real certification.

If something here turns out to be needed, promote it explicitly: add it to [[PRD]] and [[ImplementationPlan]], and remove it from this list with a note in [[Decisions]].
