'use client';

import { ActionIcon, ScrollArea, Table, Tooltip } from '@mantine/core';
import { IconChartBar, IconTable } from '@tabler/icons-react';
import { useState } from 'react';
import { Panel } from '@/components/ui/Panel';

/** The same numbers as a table, for reading exact values and for anyone who can't read the chart. */
export function DataTable({ columns, rows, maxHeight = 320 }) {
  return (
    <ScrollArea.Autosize mah={maxHeight} type="auto" offsetScrollbars>
      <Table verticalSpacing={6} horizontalSpacing="sm" stickyHeader>
        <Table.Thead>
          <Table.Tr>
            {columns.map((c) => (
              <Table.Th key={c.key} ta={c.align}>{c.label}</Table.Th>
            ))}
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {rows.map((row, i) => (
            <Table.Tr key={i}>
              {columns.map((c) => (
                <Table.Td key={c.key} ta={c.align} className={c.align === 'right' ? 'mono' : undefined} style={{ fontSize: 13 }}>
                  {c.render ? c.render(row) : row[c.key]}
                </Table.Td>
              ))}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </ScrollArea.Autosize>
  );
}

/** A titled chart that can flip to its data table (Design.md §5.7: every chart has a table view). */
export function ChartCard({ title, subtitle, columns, rows, children, ...props }) {
  const [asTable, setAsTable] = useState(false);
  const label = asTable ? 'Show chart' : 'Show data table';
  return (
    <Panel
      title={title}
      subtitle={subtitle}
      right={
        <Tooltip label={label}>
          <ActionIcon variant="subtle" color="gray" aria-label={`${label}: ${title}`} aria-pressed={asTable} onClick={() => setAsTable((v) => !v)}>
            {asTable ? <IconChartBar size={17} /> : <IconTable size={17} />}
          </ActionIcon>
        </Tooltip>
      }
      {...props}
    >
      {asTable ? <DataTable columns={columns} rows={rows} /> : children}
    </Panel>
  );
}
