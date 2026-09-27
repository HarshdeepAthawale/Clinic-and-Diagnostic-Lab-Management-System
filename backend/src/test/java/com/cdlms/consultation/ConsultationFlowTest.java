package com.cdlms.consultation;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 04: consultation workflow, prescriptions, PDF, access rules (Rules.md §1b, ADR-021). */
class ConsultationFlowTest extends IntegrationTest {

    private static final String FULL = """
            {"chiefComplaint":"Fever and sore throat, 3 days","notes":"Tonsils enlarged, no exudate",
             "diagnosis":"Acute pharyngitis","advice":"Warm saline gargles. Plenty of fluids.",
             "followUpDate":"%s",
             "vitals":{"bpSystolic":124,"bpDiastolic":82,"pulseBpm":88,"temperatureC":38.4,"spo2Percent":98,"weightKg":64.5},
             "medicines":[
               {"medicine":"Paracetamol","dosage":"500 mg","frequency":"1-1-1","duration":"3 days","instructions":"After food"},
               {"medicine":"Azithromycin","dosage":"500 mg","frequency":"1-0-0","duration":"3 days"}]}"""
            .formatted(java.time.LocalDate.now().plusDays(7));

    private Cookie reception;
    private Cookie doctor;
    private Cookie otherDoctor;
    private Cookie patient;
    private UUID doctorId;
    private UUID otherDoctorId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        User otherDoctorUser = testUsers.create(Role.DOCTOR, "other.doctor@test.local");
        otherDoctor = testUsers.loginCookie(otherDoctorUser);
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);

        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        otherDoctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, otherDoctorUser.getId());
        patientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
    }

    // ---------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }

    /** A walk-in token: the patient is checked in with the doctor, ready to be seen. */
    private String checkedIn(UUID withDoctor) throws Exception {
        return read(mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + withDoctor + "\"}").cookie(reception))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private ResultActions start(Cookie who, String appointmentId) throws Exception {
        return mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}").cookie(who));
    }

    private String startedConsultation() throws Exception {
        return read(start(doctor, checkedIn(doctorId)).andExpect(status().isOk()).andReturn(), "$.id");
    }

    private MvcResult completed(String body) throws Exception {
        String id = startedConsultation();
        return mvc.perform(json(post("/api/consultations/" + id + "/complete"), body).cookie(doctor))
                .andExpect(status().isOk()).andReturn();
    }

    private String appointmentStatus(String appointmentId) {
        return jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, UUID.fromString(appointmentId));
    }

    // ---------------------------------------------------------------- workflow

    @Test
    void startingCallsThePatientInAndOpensADraft() throws Exception {
        String appointmentId = checkedIn(doctorId);

        start(doctor, appointmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.patient.fullName").value("Test PATIENT"))
                .andExpect(jsonPath("$.doctor.fullName").value("Test DOCTOR"));
        assertThat(appointmentStatus(appointmentId)).isEqualTo("IN_CONSULTATION");
    }

    @Test
    void startingAgainReopensTheSameConsultation() throws Exception {
        String appointmentId = checkedIn(doctorId);
        String first = read(start(doctor, appointmentId).andReturn(), "$.id");

        start(doctor, appointmentId).andExpect(jsonPath("$.id").value(first));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultations", Long.class)).isEqualTo(1);
    }

    @Test
    void patientMustBeCheckedInFirst() throws Exception {
        String booked = jdbc.queryForObject("INSERT INTO appointments (patient_id, doctor_id, scheduled_at, status) "
                + "VALUES (?, ?, ?, 'BOOKED') RETURNING id", UUID.class, patientId, doctorId,
                Timestamp.from(Instant.now())).toString();

        start(doctor, booked).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_READY"));
    }

    @Test
    void onlyTheAppointmentsDoctorCanStartIt() throws Exception {
        start(otherDoctor, checkedIn(doctorId)).andExpect(status().isNotFound());
        start(patient, checkedIn(otherDoctorId)).andExpect(status().isForbidden());
    }

    @Test
    void savingADraftReplacesTheMedicineList() throws Exception {
        String id = startedConsultation();
        mvc.perform(json(put("/api/consultations/" + id), FULL).cookie(doctor))
                .andExpect(jsonPath("$.prescription.medicines", hasSize(2)))
                .andExpect(jsonPath("$.prescription.prescriptionCode").doesNotExist());

        mvc.perform(json(put("/api/consultations/" + id),
                        "{\"diagnosis\":\"Viral fever\",\"medicines\":[{\"medicine\":\"Paracetamol\",\"frequency\":\"SOS\",\"duration\":\"2 days\"}]}")
                        .cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Viral fever"))
                .andExpect(jsonPath("$.prescription.medicines", hasSize(1)))
                .andExpect(jsonPath("$.prescription.medicines[0].frequency").value("SOS"));
    }

    @Test
    void finishingNeedsADiagnosis() throws Exception {
        String appointmentId = checkedIn(doctorId);
        String id = read(start(doctor, appointmentId).andReturn(), "$.id");

        mvc.perform(json(post("/api/consultations/" + id + "/complete"), "{\"notes\":\"Seen\"}").cookie(doctor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DIAGNOSIS_REQUIRED"));
        assertThat(appointmentStatus(appointmentId)).isEqualTo("IN_CONSULTATION");
    }

    @Test
    void finishingIssuesThePrescriptionCompletesTheVisitAndLocksTheRecord() throws Exception {
        MvcResult done = completed(FULL);
        String id = read(done, "$.id");

        assertThat(read(done, "$.status")).isEqualTo("COMPLETED");
        assertThat(read(done, "$.prescription.prescriptionCode")).matches("RX-\\d{6}");
        assertThat(read(done, "$.vitals.temperatureC")).isEqualTo("38.4");
        assertThat(appointmentStatus(read(done, "$.appointmentId"))).isEqualTo("COMPLETED");

        mvc.perform(json(put("/api/consultations/" + id), FULL).cookie(doctor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSULTATION_LOCKED"));
        assertThatThrownBy(() -> jdbc.update("UPDATE consultations SET diagnosis = 'changed'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE prescription_items SET dosage = '5 g'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM prescriptions")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void finishingWithoutMedicinesIssuesNoPrescription() throws Exception {
        MvcResult done = completed("{\"diagnosis\":\"Tension headache\",\"advice\":\"Rest\"}");

        assertThat(done.getResponse().getContentAsString()).doesNotContain("\"prescription\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prescriptions", Long.class)).isZero();
    }

    @Test
    void outOfRangeVitalsAreRejected() throws Exception {
        String id = startedConsultation();
        mvc.perform(json(put("/api/consultations/" + id), "{\"vitals\":{\"spo2Percent\":140}}").cookie(doctor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---------------------------------------------------------------- patient access & PDF

    @Test
    void patientSeesTheirConsultationWithoutTheDoctorsNotesAndDownloadsThePdf() throws Exception {
        MvcResult done = completed(FULL);
        String rxId = read(done, "$.prescription.id");

        mvc.perform(get("/api/prescriptions/mine").cookie(patient))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].prescriptionCode").value(read(done, "$.prescription.prescriptionCode")))
                .andExpect(jsonPath("$[0].medicineCount").value(2));
        mvc.perform(get("/api/prescriptions/" + rxId).cookie(patient))
                .andExpect(jsonPath("$.diagnosis").value("Acute pharyngitis"))
                .andExpect(jsonPath("$.notes").doesNotExist());

        MvcResult pdf = mvc.perform(get("/api/prescriptions/" + rxId + "/pdf").param("download", "true").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        assertThat(bytes.length).isGreaterThan(1000);
    }

    @Test
    void otherPatientsAndTheFrontDeskCannotSeeAPrescription() throws Exception {
        String rxId = read(completed(FULL), "$.prescription.id");
        Cookie stranger = testUsers.loginCookie(testUsers.create(Role.PATIENT, "stranger@test.local"));

        mvc.perform(get("/api/prescriptions/" + rxId).cookie(stranger)).andExpect(status().isNotFound());
        mvc.perform(get("/api/prescriptions/" + rxId + "/pdf").cookie(stranger)).andExpect(status().isNotFound());
        mvc.perform(get("/api/prescriptions/" + rxId).cookie(reception)).andExpect(status().isForbidden());
    }

    @Test
    void draftsAreInvisibleToThePatient() throws Exception {
        String id = startedConsultation();
        mvc.perform(get("/api/consultations/" + id).cookie(patient)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- other doctors & history

    @Test
    void anotherDoctorReadsACompletedVisitOnlyWithACareRelationshipAndIsLogged() throws Exception {
        String id = read(completed(FULL), "$.id");
        mvc.perform(get("/api/consultations/" + id).cookie(otherDoctor)).andExpect(status().isNotFound());

        checkedIn(otherDoctorId); // now the other doctor is treating this patient too
        mvc.perform(get("/api/consultations/" + id).cookie(otherDoctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Tonsils enlarged, no exudate"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM patient_access_log WHERE resource = 'CONSULTATIONS'", Long.class)).isEqualTo(1);
    }

    @Test
    void visitHistoryListsCompletedConsultations() throws Exception {
        String rxCode = read(completed(FULL), "$.prescription.prescriptionCode");

        mvc.perform(get("/api/patients/" + patientId + "/consultations").cookie(doctor))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].diagnosis").value("Acute pharyngitis"))
                .andExpect(jsonPath("$[0].prescriptionCode").value(rxCode));
        mvc.perform(get("/api/patients/" + patientId + "/consultations").cookie(patient))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/patients/" + patientId + "/consultations").cookie(otherDoctor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
    }

    @Test
    void doctorListsTheirOwnConsultationsDraftsFirst() throws Exception {
        completed(FULL);
        startedConsultation();

        mvc.perform(get("/api/consultations").cookie(doctor))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
        mvc.perform(get("/api/consultations").cookie(otherDoctor)).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------------------------------------------------------------- dashboards

    @Test
    void dashboardsShowTheOpenConsultationAndRecentPrescriptions() throws Exception {
        String id = startedConsultation();
        mvc.perform(get("/api/dashboard/doctor").cookie(doctor))
                .andExpect(jsonPath("$.widgets[0].type").value("openConsultation"))
                .andExpect(jsonPath("$.widgets[0].data.consultationId").value(id));

        mvc.perform(json(post("/api/consultations/" + id + "/complete"), FULL).cookie(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/doctor").cookie(doctor))
                .andExpect(jsonPath("$.widgets[?(@.type == 'openConsultation')]").isEmpty());
        mvc.perform(get("/api/dashboard/patient").cookie(patient))
                .andExpect(jsonPath("$.widgets[?(@.type == 'recentPrescriptions')].data[0].diagnosis").value("Acute pharyngitis"));
    }

    // ---------------------------------------------------------------- formulary

    @Test
    void formularySuggestsMatchingMedicinesPrefixFirst() throws Exception {
        mvc.perform(get("/api/formulary").param("q", "para").cookie(doctor))
                .andExpect(jsonPath("$[0].name").value("Paracetamol"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/formulary").param("q", "para").cookie(patient)).andExpect(status().isForbidden());
    }
}
