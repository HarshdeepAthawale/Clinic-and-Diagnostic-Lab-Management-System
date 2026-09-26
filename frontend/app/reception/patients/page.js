import { Stack } from '@mantine/core';
import { PatientSearch } from '@/components/patients/PatientSearch';
import { PageTitle } from '@/components/ui/PageTitle';

export const metadata = { title: 'Patients' };

export default function ReceptionPatientsPage() {
  return (
    <Stack gap="xl">
      <PageTitle title="Patients" subtitle="Search before registering, so each person has exactly one record." />
      <PatientSearch basePath="/reception/patients" />
    </Stack>
  );
}
