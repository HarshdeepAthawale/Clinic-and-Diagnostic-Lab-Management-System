import { Anchor, Group, Text, Title } from '@mantine/core';
import { IconArrowLeft } from '@tabler/icons-react';
import Link from 'next/link';
import { Reveal } from './Reveal';

/** Title row for inner pages: optional back link, title, subtitle, right-side actions. */
export function PageTitle({ title, subtitle, back, actions }) {
  return (
    <Reveal y={10}>
      {back && (
        <Anchor component={Link} href={back.href} size="sm" c="var(--text-muted)" fw={500} mb={8} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <IconArrowLeft size={15} />
          {back.label}
        </Anchor>
      )}
      <Group justify="space-between" align="flex-end" wrap="wrap" gap="md">
        <div>
          <Title order={1} fz={{ base: 24, sm: 30 }} fw={600} style={{ letterSpacing: '-0.02em' }}>
            {title}
          </Title>
          {subtitle && (
            <Text c="var(--text-muted)" mt={4}>
              {subtitle}
            </Text>
          )}
        </div>
        {actions && <Group gap="sm">{actions}</Group>}
      </Group>
    </Reveal>
  );
}
