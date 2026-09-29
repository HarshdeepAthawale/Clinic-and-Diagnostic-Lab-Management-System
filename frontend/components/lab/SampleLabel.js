'use client';

import { Box, Button, Group, Stack, Text } from '@mantine/core';
import { IconPrinter } from '@tabler/icons-react';
import { useMemo } from 'react';
import { qrRows } from '@/lib/samples';
import { formatDate } from '@/lib/format';
import { TubeChip } from '@/components/ui/TubeChip';
import classes from './SampleLabel.module.css';

/**
 * The QR code for a sample code, drawn as SVG rectangles from the module grid (nothing is injected as
 * HTML). Runs of dark modules in a row become one rectangle. Includes the 4-module quiet zone scanners need.
 */
export function QrCode({ value, size = 120 }) {
  const rows = useMemo(() => qrRows(value), [value]);
  const quiet = 4;
  const span = rows.length + quiet * 2;
  const rects = [];
  rows.forEach((row, y) => {
    let x = 0;
    while (x < row.length) {
      if (!row[x]) {
        x += 1;
        continue;
      }
      const start = x;
      while (x < row.length && row[x]) x += 1;
      rects.push(<rect key={`${y}-${start}`} x={start + quiet} y={y + quiet} width={x - start} height={1} />);
    }
  });
  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${span} ${span}`}
      shapeRendering="crispEdges"
      role="img"
      aria-label={`QR code for ${value}`}
      className={classes.qr}
    >
      <rect width={span} height={span} fill="#fff" />
      <g fill="#000">{rects}</g>
    </svg>
  );
}

/**
 * The label stuck on the tube: QR + code, patient, tube and tests. Printing shows only the label.
 * Scanning isn't built into the bench — a barcode scanner types the code into the scan field, and the
 * code is printed in words under the QR for typing (ADR-024).
 */
export function SampleLabel({ sample }) {
  const tests = sample.tests.map((t) => t.code).join(' · ');
  return (
    <Stack gap="md">
      <Box className={classes.label} id="sample-label">
        <Group gap="md" wrap="nowrap" align="center">
          <QrCode value={sample.sampleCode} />
          <div style={{ minWidth: 0 }}>
            <Text className={`mono ${classes.code}`}>{sample.sampleCode}</Text>
            <Text fw={600} size="sm" truncate>{sample.patient.fullName}</Text>
            <Text size="xs" className="mono" c="var(--text-muted)">{sample.patient.patientCode}</Text>
            <Group gap={6} mt={6} wrap="nowrap">
              <TubeChip tube={sample.requiredTubeType} />
              {sample.priority === 'URGENT' && <span className={classes.urgent}>URGENT</span>}
            </Group>
          </div>
        </Group>
        <Text size="xs" mt="sm" className="mono" c="var(--text-muted)">{tests}</Text>
        <Text size="xs" c="var(--text-subtle)">{sample.orderCode} · {formatDate(sample.createdAt)}</Text>
      </Box>
      <Group className={classes.noPrint}>
        <Button variant="default" leftSection={<IconPrinter size={16} />} onClick={() => window.print()}>
          Print label
        </Button>
      </Group>
    </Stack>
  );
}
