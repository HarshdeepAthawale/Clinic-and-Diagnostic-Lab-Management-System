'use client';

import { Button, Stack, Text, Title } from '@mantine/core';
import { IconCompass } from '@tabler/icons-react';
import Link from 'next/link';

export default function NotFound() {
  return (
    <Stack component="main" id="main" align="center" justify="center" mih="100dvh" gap="md" p="xl" ta="center">
      <IconCompass size={44} stroke={1.3} color="var(--brand)" />
      <Title order={1} className="serif" fz={40} fw={400}>
        This page doesn&apos;t exist
      </Title>
      <Text c="var(--text-muted)" maw={420}>
        The link may be broken, or the page may belong to a part of the system that isn&apos;t built yet.
      </Text>
      <Button component={Link} href="/" mt="sm">
        Back to my workspace
      </Button>
    </Stack>
  );
}
