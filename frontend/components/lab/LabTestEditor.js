'use client';

import { ActionIcon, Alert, Autocomplete, Button, Drawer, Grid, Group, NumberInput, Select, Skeleton, Stack, Switch, Text, TextInput, Textarea } from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCheck, IconPlus, IconTrash } from '@tabler/icons-react';
import { useLabTest, useSaveLabTest } from '@/lib/lab';
import { friendlyMessage } from '@/lib/errors';
import { TUBES } from '@/components/ui/TubeChip';

const SAMPLE_TYPES = [
  { value: 'BLOOD', label: 'Blood' },
  { value: 'URINE', label: 'Urine' },
  { value: 'STOOL', label: 'Stool' },
  { value: 'SWAB', label: 'Swab' },
];
const TUBE_OPTIONS = Object.entries(TUBES).map(([value, t]) => ({ value, label: t.label }));
const blankParameter = { name: '', unit: '', valueType: 'NUMERIC', refLow: '', refHigh: '', criticalLow: '', criticalHigh: '' };
const VALUE_TYPES = [{ value: 'NUMERIC', label: 'Number' }, { value: 'TEXT', label: 'Text' }];

const num = (v) => (v === '' || v === null || v === undefined ? null : Number(v));
const str = (v) => (v?.trim() ? v.trim() : null);

function initialValues(test) {
  return {
    code: test?.code ?? '',
    name: test?.name ?? '',
    category: test?.category ?? '',
    sampleType: test?.sampleType ?? 'BLOOD',
    requiredTubeType: test?.requiredTubeType ?? 'EDTA',
    price: test?.price ?? '',
    turnaroundHours: test?.turnaroundHours ?? 24,
    prepInstructions: test?.prepInstructions ?? '',
    active: test?.active ?? true,
    parameters: (test?.parameters ?? []).map((p) => ({
      name: p.name,
      unit: p.unit ?? '',
      valueType: p.valueType ?? 'TEXT',
      refLow: p.refLow ?? '',
      refHigh: p.refHigh ?? '',
      criticalLow: p.criticalLow ?? '',
      criticalHigh: p.criticalHigh ?? '',
    })),
  };
}

function toRequest(v) {
  return {
    code: str(v.code),
    name: v.name.trim(),
    category: v.category.trim(),
    sampleType: v.sampleType,
    requiredTubeType: v.requiredTubeType,
    price: num(v.price),
    turnaroundHours: num(v.turnaroundHours),
    prepInstructions: str(v.prepInstructions),
    active: v.active,
    parameters: v.parameters
      .filter((p) => p.name.trim())
      .map((p) => ({
        name: p.name.trim(),
        unit: str(p.unit),
        valueType: p.valueType,
        // A text result has no range to check against.
        refLow: p.valueType === 'TEXT' ? null : num(p.refLow),
        refHigh: p.valueType === 'TEXT' ? null : num(p.refHigh),
        criticalLow: p.valueType === 'TEXT' ? null : num(p.criticalLow),
        criticalHigh: p.valueType === 'TEXT' ? null : num(p.criticalHigh),
      })),
  };
}

function Form({ test, categories, onDone }) {
  const save = useSaveLabTest(test?.id);
  const form = useForm({
    initialValues: initialValues(test),
    validate: {
      code: (v) => (!test && !v.trim() ? 'Give the test a short code, e.g. CBC' : null),
      name: (v) => (v.trim() ? null : 'Required'),
      category: (v) => (v.trim() ? null : 'Required'),
      price: (v) => (v === '' ? 'Required' : null),
    },
  });

  const submit = form.onSubmit((values) =>
    save.mutate(toRequest(values), {
      onSuccess: (saved) => {
        notifications.show({ title: test ? 'Test updated' : 'Test added', message: `${saved.code} · ${saved.name}`, color: 'teal', radius: 'lg', icon: <IconCheck size={18} /> });
        onDone();
      },
      onError: (error) => form.setErrors(error.fields ?? {}),
    }),
  );

  return (
    <form onSubmit={submit}>
      <Stack gap="md" p="lg">
        <Grid gutter="sm">
          <Grid.Col span={4}>
            <TextInput label="Code" placeholder="CBC" disabled={Boolean(test)} maxLength={20} className="mono" {...form.getInputProps('code')} />
          </Grid.Col>
          <Grid.Col span={8}>
            <TextInput label="Name" placeholder="Complete Blood Count" maxLength={200} {...form.getInputProps('name')} />
          </Grid.Col>
          <Grid.Col span={6}>
            <Autocomplete label="Category" data={categories} placeholder="Haematology" maxLength={40} {...form.getInputProps('category')} />
          </Grid.Col>
          <Grid.Col span={6}>
            <Select label="Sample" data={SAMPLE_TYPES} allowDeselect={false} {...form.getInputProps('sampleType')} />
          </Grid.Col>
          <Grid.Col span={4}>
            <Select label="Tube / container" data={TUBE_OPTIONS} allowDeselect={false} {...form.getInputProps('requiredTubeType')} />
          </Grid.Col>
          <Grid.Col span={4}>
            <NumberInput label="Price (₹)" min={0} max={999999} decimalScale={2} hideControls {...form.getInputProps('price')} />
          </Grid.Col>
          <Grid.Col span={4}>
            <NumberInput label="Turnaround (hours)" min={1} max={720} allowDecimal={false} {...form.getInputProps('turnaroundHours')} />
          </Grid.Col>
        </Grid>
        <Textarea
          label="Patient preparation"
          description="Shown to the patient in their app when this test is ordered. Leave empty if none."
          placeholder="Fast for 10–12 hours before the test. Water is fine."
          autosize
          minRows={2}
          maxLength={500}
          {...form.getInputProps('prepInstructions')}
        />

        <div>
          <Group justify="space-between" mb={6}>
            <div>
              <Text size="sm" fw={600}>Parameters & reference ranges</Text>
              <Text size="xs" c="var(--text-muted)">A number is checked against its range when the result is entered. Choose Text for results like Positive / Negative or a blood group.</Text>
            </div>
            <Button size="xs" variant="default" leftSection={<IconPlus size={14} />} onClick={() => form.insertListItem('parameters', { ...blankParameter })}>
              Add
            </Button>
          </Group>
          <Stack gap={6}>
            {form.values.parameters.map((row, i) => {
              const text = row.valueType === 'TEXT';
              return (
                <Group key={i} gap={6} wrap="nowrap" align="flex-start">
                  <TextInput size="xs" placeholder="Name" style={{ flex: 2 }} {...form.getInputProps(`parameters.${i}.name`)} />
                  <Select size="xs" data={VALUE_TYPES} allowDeselect={false} w={82} aria-label="Result type" {...form.getInputProps(`parameters.${i}.valueType`)} />
                  <TextInput size="xs" placeholder="Unit" style={{ flex: 1 }} {...form.getInputProps(`parameters.${i}.unit`)} />
                  <NumberInput size="xs" placeholder="Low" hideControls disabled={text} style={{ flex: 1 }} {...form.getInputProps(`parameters.${i}.refLow`)} />
                  <NumberInput size="xs" placeholder="High" hideControls disabled={text} style={{ flex: 1 }} {...form.getInputProps(`parameters.${i}.refHigh`)} />
                  <NumberInput size="xs" placeholder="Crit. low" hideControls disabled={text} style={{ flex: 1 }} {...form.getInputProps(`parameters.${i}.criticalLow`)} />
                  <NumberInput size="xs" placeholder="Crit. high" hideControls disabled={text} style={{ flex: 1 }} {...form.getInputProps(`parameters.${i}.criticalHigh`)} />
                  <ActionIcon variant="subtle" color="gray" mt={2} onClick={() => form.removeListItem('parameters', i)} aria-label="Remove parameter">
                    <IconTrash size={15} />
                  </ActionIcon>
                </Group>
              );
            })}
            {form.values.parameters.length === 0 && <Text size="xs" c="var(--text-subtle)">No parameters yet.</Text>}
          </Stack>
        </div>

        <Switch
          label="Offered"
          description="Retired tests can't be ordered. Existing orders keep them."
          checked={form.values.active}
          onChange={(e) => form.setFieldValue('active', e.currentTarget.checked)}
        />

        {save.error && !save.error.fields && (
          <Alert color="red" variant="light" radius="md" icon={<IconAlertCircle size={16} />}>{friendlyMessage(save.error)}</Alert>
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onDone}>Cancel</Button>
          <Button type="submit" loading={save.isPending}>{test ? 'Save changes' : 'Add test'}</Button>
        </Group>
      </Stack>
    </form>
  );
}

/** Admin: add a test, or edit one (`testId`). The form mounts once the test has loaded. */
export function LabTestEditor({ opened, testId, categories, onClose }) {
  const test = useLabTest(opened ? testId : null);
  return (
    <Drawer opened={opened} onClose={onClose} position="right" size={640} padding={0}
      title={<Text fw={600}>{testId ? 'Edit test' : 'Add a test'}</Text>}
      styles={{ header: { padding: '16px 24px', borderBottom: '1px solid var(--border)' } }}
    >
      {testId && test.isPending ? (
        <Stack p="lg">{[0, 1, 2].map((i) => <Skeleton key={i} height={56} radius="md" />)}</Stack>
      ) : testId && test.isError ? (
        <Text p="lg" c="var(--critical)" size="sm">{friendlyMessage(test.error)}</Text>
      ) : (
        <Form key={testId ?? 'new'} test={testId ? test.data : null} categories={categories} onDone={onClose} />
      )}
    </Drawer>
  );
}
