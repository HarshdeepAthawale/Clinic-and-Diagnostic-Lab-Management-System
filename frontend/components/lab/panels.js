/**
 * Common test panels a doctor orders together, by catalog code. A panel is only offered when every
 * one of its tests is in the (active) catalog, so retiring a test quietly hides panels that need it.
 */
export const PANELS = [
  { key: 'fever', label: 'Fever workup', codes: ['CBC', 'NS1', 'WIDAL', 'CRP', 'URINE'] },
  { key: 'diabetes', label: 'Diabetes', codes: ['FBS', 'PPBS', 'HBA1C'] },
  { key: 'checkup', label: 'Annual check-up', codes: ['CBC', 'LIPID', 'LFT', 'KFT', 'FBS', 'TSH'] },
  { key: 'anaemia', label: 'Fatigue / anaemia', codes: ['CBC', 'VITB12', 'VITD', 'TSH'] },
];

/** Panels available in this catalog, each with its tests resolved: [{ ...panel, tests }]. */
export function availablePanels(catalog) {
  const byCode = new Map(catalog.map((t) => [t.code, t]));
  return PANELS.filter((p) => p.codes.every((c) => byCode.has(c))).map((p) => ({
    ...p,
    tests: p.codes.map((c) => byCode.get(c)),
  }));
}

/** Toggling a panel: adds all its tests, or removes them all when every one is already selected. */
export function togglePanel(selectedIds, panel, lockedIds = new Set()) {
  const ids = panel.tests.map((t) => t.id).filter((id) => !lockedIds.has(id));
  const next = new Set(selectedIds);
  const allOn = ids.every((id) => next.has(id));
  ids.forEach((id) => (allOn ? next.delete(id) : next.add(id)));
  return next;
}
