import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PATIENT_STAGES, SampleJourney } from './SampleJourney';

function renderJourney(props) {
  return render(
    <MantineProvider>
      <SampleJourney {...props} />
    </MantineProvider>,
  );
}

describe('SampleJourney', () => {
  it('marks earlier stages done, the current stage as the current step, and the rest pending', () => {
    renderJourney({ current: 2, times: ['9:02 AM', '9:40 AM'], currentNote: 'Now' });

    const steps = screen.getAllByRole('listitem');
    expect(steps).toHaveLength(PATIENT_STAGES.length);
    expect(steps[0]).toHaveTextContent('Ordered');
    expect(steps[0]).toHaveTextContent('9:02 AM');
    expect(steps[0]).toHaveTextContent('completed');
    expect(steps[2]).toHaveAttribute('aria-current', 'step');
    expect(steps[2]).toHaveTextContent('Now');
    expect(steps[5]).toHaveTextContent('not started');
  });

  it('exposes an accessible label for the whole journey', () => {
    renderJourney({ current: 0, label: 'Sample LAB-20260927-0042' });

    expect(screen.getByRole('list', { name: 'Sample LAB-20260927-0042' })).toBeInTheDocument();
  });
});
