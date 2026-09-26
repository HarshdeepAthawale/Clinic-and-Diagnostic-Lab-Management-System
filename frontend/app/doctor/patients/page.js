import { Stack } from '@mantine/core';
import { PatientSearch } from '@/components/patients/PatientSearch';
import { PageTitle } from '@/components/ui/PageTitle';

export const metadata = { title: 'Patients' };

export default function DoctorPatientsPage() {
  return (
    <Stack gap="xl">
      <PageTitle title="Patients" subtitle="Find any patient. Full records open for patients who have an appointment with you." />
      <PatientSearch basePath="/doctor/patients" showCare />
    </Stack>
  );
}
