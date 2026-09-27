import { describe, expect, it } from 'vitest';
import { availablePanels, togglePanel } from './panels';

const catalog = ['FBS', 'PPBS', 'HBA1C', 'CBC'].map((code) => ({ id: code.toLowerCase(), code }));

describe('availablePanels', () => {
  it('offers only panels whose tests are all in the catalog', () => {
    const panels = availablePanels(catalog);
    expect(panels.map((p) => p.key)).toEqual(['diabetes']);
    expect(panels[0].tests.map((t) => t.id)).toEqual(['fbs', 'ppbs', 'hba1c']);
  });
});

describe('togglePanel', () => {
  const [diabetes] = availablePanels(catalog);

  it('selects every test in the panel', () => {
    expect([...togglePanel(new Set(['cbc']), diabetes)].sort()).toEqual(['cbc', 'fbs', 'hba1c', 'ppbs']);
  });

  it('clears the panel when it is fully selected', () => {
    expect([...togglePanel(new Set(['cbc', 'fbs', 'ppbs', 'hba1c']), diabetes)]).toEqual(['cbc']);
  });

  it('ignores tests already on the order', () => {
    const next = togglePanel(new Set(['ppbs', 'hba1c']), diabetes, new Set(['fbs']));
    expect([...next].sort()).toEqual([]);
  });
});
