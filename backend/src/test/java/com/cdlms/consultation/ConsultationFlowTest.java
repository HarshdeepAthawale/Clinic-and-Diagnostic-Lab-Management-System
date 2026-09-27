package com.cdlms.consultation;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 04: consultation lifecycle, prescriptions, revisions, PDF and who may read them (Rules.md §1b, ADR-021). */
class ConsultationFlowTest extends IntegrationTest {

    private static final String RX = """
            {"advice":"Rest and fluids.","items":[
              {"medicine":"Paracetamol 500 mg","dose":"1 tablet","frequency":"1-1-1","durationDays":5,"instructions":"After food"},
              {"medicine":"Cetirizine 10 mg","frequency":"0-0-1","durationDays":3}]}""";

    private Cookie reception;
    private Cookie doctor;
    private Cookie otherDoctor;
    private Cookie patient;
    private Cookie otherPatient;
    private UUID doctorId;
    private UUID patientId;
    private UUID otherPatientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        otherDoctor = testUsers.loginCookie(testUsers.create(Role.DOCTOR, "other.doctor@test.local"));
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        User otherPatientUser = testUsers.create(Role.PATIENT, "other.patient@test.local");
        otherPatient = testUsers.loginCookie(otherPatientUser);
        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
        patientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, patientUser.getId());
        otherPatientId = jdbc.queryForObject("SELECT id FROM patients WHERE user_id = ?", UUID.class, otherPatientUser.getId());
        jdbc.update("UPDATE doctors SET qualification = 'MBBS, MD', registration_number = 'MMC-1234' WHERE id = ?", doctorId);
    }

    // ---------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String appointment(UUID forPatient, String status) {
        return jdbc.queryForObject("INSERT INTO appointments (patient_id, doctor_id, scheduled_at, status, reason) "
                        + "VALUES (?, ?, ?, ?, 'Fever for 3 days') RETURNING id", UUID.class, forPatient, doctorId,
                Timestamp.from(Instant.now()), status).toString();
    }

    private ResultActions open(String appointmentId) throws Exception {
        return mvc.perform(post("/api/appointments/" + appointmentId + "/consultation")
                .header(CsrfHeaderFilter.HEADER, "1").cookie(doctor));
    }

    private String openConsultation() throws Exception {
        return read(open(appointment(patientId, "IN_CONSULTATION")).andExpect(status().isOk()).andReturn(), "$.id");
    }

    private ResultActions update(String consultationId, String body) throws Exception {
        return mvc.perform(json(patch("/api/consultations/" + consultationId), body).cookie(doctor));
    }

    private ResultActions prescribe(String consultationId, String body) throws Exception {
        return mvc.perform(json(post("/api/consultations/" + consultationId + "/prescriptions"), body).cookie(doctor));
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    // ---------------------------------------------------------------- consultation

    @Test
    void consultationOpensOnlyOnceThePatientIsCalledIn() throws Exception {
        open(appointment(patientId, "CHECKED_IN")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IN_CONSULTATION"));
    }

    @Test
    void openingIsIdempotentAndStartsFromTheBookingReason() throws Exception {
        String appointmentId = appointment(patientId, "IN_CONSULTATION");
        String first = read(open(appointmentId).andReturn(), "$.id");
        open(appointmentId).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.chiefComplaint").value("Fever for 3 days"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void onlyTheAppointmentsDoctorCanOpenIt() throws Exception {
        String appointmentId = appointment(patientId, "IN_CONSULTATION");
        mvc.perform(post("/api/appointments/" + appointmentId + "/consultation").header(CsrfHeaderFilter.HEADER, "1")
                .cookie(otherDoctor)).andExpect(status().isNotFound());
    }

    @Test
    void doctorWritesNotesAndVitals() throws Exception {
        String id = openConsultation();
        update(id, """
                {"chiefComplaint":"Fever","notes":"Throat congested.","diagnosis":"Viral pharyngitis",
                 "vitals":{"bpSystolic":120,"bpDiastolic":80,"pulse":88,"temperatureC":38.4,"spo2":98}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Viral pharyngitis"))
                .andExpect(jsonPath("$.vitals.temperatureC").value(38.4));
        update(id, "{\"vitals\":{\"pulse\":900}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['vitals.pulse']").exists());
    }

    @Test
    void finishingNeedsADiagnosisThenLocksAndCompletesTheVisit() throws Exception {
        String id = openConsultation();
        mvc.perform(post("/api/consultations/" + id + "/complete").header(CsrfHeaderFilter.HEADER, "1").cookie(doctor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DIAGNOSIS_REQUIRED"));

        update(id, "{\"diagnosis\":\"Viral fever\"}");
        mvc.perform(post("/api/consultations/" + id + "/complete").header(CsrfHeaderFilter.HEADER, "1").cookie(doctor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));

        assertThat(jdbc.queryForObject("SELECT a.status FROM appointments a JOIN consultations c ON c.appointment_id = a.id "
                + "WHERE c.id = ?", String.class, UUID.fromString(id))).isEqualTo("COMPLETED");
        update(id, "{\"diagnosis\":\"Changed\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSULTATION_COMPLETED"));
    }

    // ---------------------------------------------------------------- prescriptions

    @Test
    void doctorIssuesOnePrescriptionPerVisit() throws Exception {
        String id = openConsultation();
        prescribe(id, RX).andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("RX-\\d{6}")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].medicine").value("Paracetamol 500 mg"))
                .andExpect(jsonPath("$.items[1].position").value(2))
                .andExpect(jsonPath("$.doctor.registrationNumber").value("MMC-1234"));
        prescribe(id, RX).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_PRESCRIBED"));
    }

    @Test
    void emptyPrescriptionIsRejected() throws Exception {
        prescribe(openConsultation(), "{\"items\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void revisionReplacesButNeverEditsTheOriginal() throws Exception {
        String consultation = openConsultation();
        String original = read(prescribe(consultation, RX).andReturn(), "$.id");

        String revised = read(mvc.perform(json(post("/api/prescriptions/" + original + "/revise"), """
                        {"reason":"Wrong strength","items":[{"medicine":"Paracetamol 650 mg","frequency":"1-0-1","durationDays":3}]}""")
                        .cookie(doctor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replaces.id").value(original))
                .andExpect(jsonPath("$.revisionReason").value("Wrong strength"))
                .andReturn(), "$.id");

        mvc.perform(get("/api/prescriptions/" + original).cookie(doctor))
                .andExpect(jsonPath("$.supersededBy.id").value(revised))
                .andExpect(jsonPath("$.items[0].medicine").value("Paracetamol 500 mg"));
        mvc.perform(json(post("/api/prescriptions/" + original + "/revise"), "{\"reason\":\"x\",\"items\":[{\"medicine\":\"A\",\"frequency\":\"1-0-0\"}]}")
                .cookie(doctor)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REVISED"));

        assertThatThrownBy(() -> jdbc.update("UPDATE prescription_items SET medicine = 'x'")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM prescriptions")).isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void patientSeesOwnPrescriptionAndVisitButNotTheDoctorsNotes() throws Exception {
        String consultation = openConsultation();
        update(consultation, "{\"notes\":\"Private clinical note\",\"diagnosis\":\"Viral fever\"}");
        String rx = read(prescribe(consultation, RX).andReturn(), "$.id");

        mvc.perform(get("/api/prescriptions/mine").cookie(patient)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/prescriptions/" + rx).cookie(patient)).andExpect(status().isOk());
        mvc.perform(get("/api/consultations/" + consultation).cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Viral fever"))
                .andExpect(jsonPath("$.notes").doesNotExist());
        mvc.perform(get("/api/consultations/mine").cookie(patient)).andExpect(jsonPath("$.content", hasSize(1)));

        mvc.perform(get("/api/prescriptions/" + rx).cookie(otherPatient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/consultations/" + consultation).cookie(otherPatient)).andExpect(status().isNotFound());
    }

    @Test
    void doctorsNeedACareRelationshipAndEveryReadIsLogged() throws Exception {
        String rx = read(prescribe(openConsultation(), RX).andReturn(), "$.id");

        mvc.perform(get("/api/prescriptions/" + rx).cookie(otherDoctor)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
        mvc.perform(get("/api/patients/" + patientId + "/consultations").cookie(otherDoctor)).andExpect(status().isForbidden());

        mvc.perform(get("/api/prescriptions/" + rx).cookie(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/patients/" + patientId + "/consultations").cookie(doctor)).andExpect(jsonPath("$", hasSize(1)));
        assertThat(jdbc.queryForList("SELECT resource FROM patient_access_log WHERE patient_id = ? ORDER BY accessed_at",
                String.class, patientId)).containsExactly("PRESCRIPTION", "CONSULTATIONS");
    }

    @Test
    void frontDeskNeverSeesPrescriptions() throws Exception {
        String rx = read(prescribe(openConsultation(), RX).andReturn(), "$.id");
        mvc.perform(get("/api/prescriptions/" + rx).cookie(reception)).andExpect(status().isForbidden());
        mvc.perform(get("/api/prescriptions/" + rx + "/pdf").cookie(reception)).andExpect(status().isForbidden());
    }

    @Test
    void pastConsultationKeepsTheCareRelationshipAfterTheAppointmentIsCancelled() throws Exception {
        String consultation = openConsultation();
        jdbc.update("UPDATE appointments SET status = 'CANCELLED' WHERE id = "
                + "(SELECT appointment_id FROM consultations WHERE id = ?)", UUID.fromString(consultation));
        mvc.perform(get("/api/patients/" + patientId + "/history").cookie(doctor)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- PDF & suggestions

    @Test
    void pdfCarriesThePrescription() throws Exception {
        String consultation = openConsultation();
        update(consultation, "{\"diagnosis\":\"Viral fever\"}");
        String rx = read(prescribe(consultation, RX).andReturn(), "$.id");
        String code = read(mvc.perform(get("/api/prescriptions/" + rx).cookie(patient)).andReturn(), "$.code");

        byte[] pdf = mvc.perform(get("/api/prescriptions/" + rx + "/pdf").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains(code, "Test PATIENT", "Paracetamol 500 mg", "1-1-1", "5 days", "Viral fever",
                    "Reg. no. MMC-1234", "Rest and fluids.");
        }
    }

    @Test
    void pdfEscapesWhatDoctorsType() throws Exception {
        String rx = read(prescribe(openConsultation(), """
                {"items":[{"medicine":"<b>Syrup</b> & co","frequency":"SOS"}]}""").andReturn(), "$.id");
        byte[] pdf = mvc.perform(get("/api/prescriptions/" + rx + "/pdf").cookie(doctor))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("<b>Syrup</b> & co");
        }
    }

    @Test
    void medicineSuggestionsComeFromRealPrescriptions() throws Exception {
        prescribe(openConsultation(), RX);
        mvc.perform(get("/api/medicines?q=para").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].medicine").value("Paracetamol 500 mg"))
                .andExpect(jsonPath("$[0].frequency").value("1-1-1"))
                .andExpect(jsonPath("$[0].timesPrescribed").value(1));
        mvc.perform(get("/api/medicines?q=p").cookie(doctor)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void doctorListsTheirOwnConsultations() throws Exception {
        openConsultation();
        mvc.perform(get("/api/consultations/mine").cookie(doctor))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].patient.id").value(patientId.toString()));
        mvc.perform(get("/api/consultations/mine").cookie(otherDoctor)).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(otherPatientId).isNotNull();
    }
}
