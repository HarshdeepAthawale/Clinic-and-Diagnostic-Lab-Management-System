'use client';

import { Alert, Button, Group, Modal, NumberInput, Select, Stack, Switch, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconAlertCircle, IconCheck } from '@tabler/icons-react';
import { friendlyMessage } from '@/lib/errors';
import { CATEGORIES, useSaveItem } from '@/lib/inventory';

function Form({ item, onDone }) {
  const save = useSaveItem(item?.id);
  const form = useForm({
    initialValues: {
      name: item?.name ?? '',
      category: item?.category ?? 'TUBE',
      unit: item?.unit ?? '',
      openingStock: 0,
      lowStockThreshold: item?.lowStockThreshold ?? 10,
      active: item?.active ?? true,
    },
    validate: {
      name: (v) => (v.trim() ? null : 'Required'),
      unit: (v) => (v.trim() ? null : 'Required, e.g. tubes or boxes'),
      lowStockThreshold: (v) => (v === '' ? 'Required — use 0 to not watch this item' : null),
    },
  });

  const submit = form.onSubmit((v) => {
    const body = { name: v.name.trim(), category: v.category, unit: v.unit.trim(), lowStockThreshold: Number(v.lowStockThreshold) };
    if (item) body.active = v.active;
    else body.openingStock = Number(v.openingStock || 0);
    save.mutate(body, {
      onSuccess: (saved) => {
        notifications.show({ title: item ? 'Item updated' : 'Item added', message: saved.name, color: 'teal', radius: 'lg', icon: <IconCheck size={18} /> });
        onDone();
      },
      onError: (error) => form.setErrors(error.fields ?? {}),
    });
  });

  return (
    <form onSubmit={submit}>
      <Stack gap="sm">
        <TextInput label="Name" placeholder="EDTA tubes (purple cap)" maxLength={120} data-autofocus {...form.getInputProps('name')} />
        <Group grow align="flex-start">
          <Select label="Category" data={CATEGORIES} allowDeselect={false} {...form.getInputProps('category')} />
          <TextInput label="Unit" placeholder="tubes" maxLength={30} {...form.getInputProps('unit')} />
        </Group>
        <Group grow align="flex-start">
          {!item && <NumberInput label="Opening stock" min={0} max={1_000_000} allowDecimal={false} hideControls {...form.getInputProps('openingStock')} />}
          <NumberInput
            label="Low-stock level"
            description="Flag when stock falls below this. 0 = don't watch."
            min={0}
            max={1_000_000}
            allowDecimal={false}
            hideControls
            {...form.getInputProps('lowStockThreshold')}
          />
        </Group>
        {item && (
          <Switch
            label="In use"
            description="Turn off to retire an item. Its history is kept, but its level can't change and it never raises an alert."
            checked={form.values.active}
            onChange={(e) => form.setFieldValue('active', e.currentTarget.checked)}
          />
        )}
        {!item && <Text size="xs" c="var(--text-muted)">After this, the level only changes by recording a restock, use, wastage or correction.</Text>}
        {save.isError && !save.error.fields && (
          <Alert color="red" variant="light" icon={<IconAlertCircle size={16} />}>{friendlyMessage(save.error)}</Alert>
        )}
        <Group justify="flex-end" mt="xs">
          <Button variant="default" onClick={onDone}>Cancel</Button>
          <Button type="submit" loading={save.isPending}>{item ? 'Save changes' : 'Add item'}</Button>
        </Group>
      </Stack>
    </form>
  );
}

/** Admin: add an item, or change an item's details (not its level — that goes through a recorded change). */
export function ItemEditor({ opened, item, onClose }) {
  return (
    <Modal opened={opened} onClose={onClose} title={item ? `Edit ${item.name}` : 'Add an item'} radius="lg" centered>
      {opened && <Form key={item?.id ?? 'new'} item={item} onDone={onClose} />}
    </Modal>
  );
}
