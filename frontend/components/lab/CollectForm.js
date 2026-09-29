'use client';

import { Alert, Button, Checkbox, Group, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconCheck, IconDroplet } from '@tabler/icons-react';
import { useState } from 'react';
import { BODY_SITES, collectionProblems, isBloodTube, TUBE_CHOICES, useCollectSample } from '@/lib/samples';
import { friendlyMessage } from '@/lib/errors';
import { Panel } from '@/components/ui/Panel';
import { TUBES, TubeChip } from '@/components/ui/TubeChip';
import classes from './CollectForm.module.css';

/**
 * Collection (Design.md §5.3): which tube was used, where the blood came from. The tube the tests
 * need is pre-selected; picking another shows an amber warning and needs an explicit confirmation —
 * a mismatch is never accepted silently (Rules.md §2).
 */
export function CollectForm({ sample }) {
  const collect = useCollectSample(sample.id);
  const [tube, setTube] = useState(sample.requiredTubeType);
  const [site, setSite] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [tried, setTried] = useState(false);

  const blood = isBloodTube(tube);
  const problems = collectionProblems({ tube, site, required: sample.requiredTubeType, confirmed });
  const mismatch = tube !== sample.requiredTubeType;

  const submit = () => {
    setTried(true);
    if (problems.site || problems.mismatch) return;
    collect.mutate(
      { tubeTypeUsed: tube, bodySite: blood ? site.trim() : site.trim() || null, confirmMismatch: mismatch && confirmed },
      {
        onSuccess: (done) =>
          notifications.show({
            title: `${done.sampleCode} collected`,
            message: done.tubeMismatch ? 'Recorded with a wrong-tube flag.' : 'Now send it to the receipt check.',
            color: done.tubeMismatch ? 'yellow' : 'teal',
            radius: 'lg',
            icon: done.tubeMismatch ? <IconAlertTriangle size={18} /> : <IconCheck size={18} />,
          }),
      },
    );
  };

  return (
    <Panel title="Collect the sample" subtitle={`These tests need the ${TUBES[sample.requiredTubeType]?.label ?? sample.requiredTubeType} tube`}>
      <Stack gap="lg">
        <div>
          <Text size="sm" fw={500} mb={8}>Tube or container used</Text>
          <div className={classes.tubes} role="radiogroup" aria-label="Tube or container used">
            {TUBE_CHOICES.map((t) => (
              <UnstyledButton
                key={t}
                role="radio"
                aria-checked={tube === t}
                className={classes.tube}
                data-on={tube === t || undefined}
                data-required={t === sample.requiredTubeType || undefined}
                onClick={() => { setTube(t); setConfirmed(false); }}
              >
                <TubeChip tube={t} />
                {t === sample.requiredTubeType && <span className={classes.needed}>needed</span>}
              </UnstyledButton>
            ))}
          </div>
        </div>

        {mismatch && (
          <Alert color="yellow" variant="light" radius="md" icon={<IconAlertTriangle size={18} />} title="This isn’t the right tube">
            <Text size="sm" mb="sm">
              These tests need the {TUBES[sample.requiredTubeType]?.label} tube. Using {TUBES[tube]?.label} may give unreliable results —
              switch tubes if you can.
            </Text>
            <Checkbox
              label="Use it anyway and flag the mismatch on the record"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.currentTarget.checked)}
              error={tried && problems.mismatch ? 'Confirm to continue' : null}
            />
          </Alert>
        )}

        {blood ? (
          <div>
            <Text size="sm" fw={500} mb={8}>Drawn from</Text>
            <Group gap={8} mb={8}>
              {BODY_SITES.map((s) => (
                <UnstyledButton key={s} className={classes.site} data-on={site === s || undefined} onClick={() => setSite(s)}>
                  {s}
                </UnstyledButton>
              ))}
            </Group>
            <TextInput
              placeholder="Or type another site"
              maxLength={60}
              value={site}
              onChange={(e) => setSite(e.currentTarget.value)}
              error={tried ? problems.site : null}
              aria-label="Body site"
            />
          </div>
        ) : (
          <Text size="sm" c="var(--text-muted)">A cup or swab — no body site needed.</Text>
        )}

        {collect.error && (
          <Alert color="red" variant="light" radius="md" icon={<IconAlertTriangle size={16} />}>{friendlyMessage(collect.error)}</Alert>
        )}
        <Button size="lg" onClick={submit} loading={collect.isPending} leftSection={<IconDroplet size={18} />}>
          {mismatch ? 'Record collection with wrong tube' : 'Mark as collected'}
        </Button>
      </Stack>
    </Panel>
  );
}
