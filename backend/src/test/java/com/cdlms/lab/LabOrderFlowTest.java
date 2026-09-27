package com.cdlms.lab;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 05: the lab test catalog and ordering tests from a consultation or directly (Rules.md §2a, ADR-022). */
class LabOrderFlowTest extends IntegrationTest {

    private Cookie reception;
    private Cookie doctor;
    private Cookie otherDoctor;
    private Cookie patient;
    private Cookie otherPatient;
    private Cookie lab;
    private Cookie admin;
    private UUID doctorId;
    private UUID patientId;
    private UUID doctorUserId;

    @BeforeEach
    void setUp() {
        // Catalog rows are reference data and survive the per-test truncate; drop the ones tests add.
        jdbc.update("DELETE FROM lab_tests WHERE code LIKE 'ZZ%'");
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        otherDoctor = testUsers.loginCookie(testUsers.create(Role.DOCTOR, "other.doctor@test.local"));
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        otherPatient = testUsers.loginCookie(testUsers.create(Role.PATIENT, "other.patient@test.local"));
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));

        doctorUserId = doctorUser.getId();
        doctorId = jdbc.queryForObject("SELECT id FROM doctors WHERE user_id = ?", UUID.class, doctorUser.getId());
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

    private String test(String code) {
        return jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = ?", UUID.class, code).toString();
    }

    private String ids(String... codes) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < codes.length; i++) {
            out.append(i == 0 ? "" : ",").append('"').append(test(codes[i])).append('"');
        }
        return out.append(']').toString();
    }

    /** Checks the patient in with the doctor and starts the consultation; returns its id. */
    private String consultation() throws Exception {
        String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        return read(mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}")
                .cookie(doctor)).andExpect(status().isOk()).andReturn(), "$.id");
    }

    private ResultActions order(Cookie who, String body) throws Exception {
        return mvc.perform(json(post("/api/lab-orders"), body).cookie(who));
    }

    private ResultActions orderInConsultation(String consultationId, String... codes) throws Exception {
        return order(doctor, "{\"consultationId\":\"" + consultationId + "\",\"testIds\":" + ids(codes) + "}");
    }

    private String directOrder(String priority, String... codes) throws Exception {
        return read(order(doctor, "{\"patientId\":\"" + patientId + "\",\"priority\":\"" + priority
                        + "\",\"clinicalNotes\":\"Rule out anaemia\",\"testIds\":" + ids(codes) + "}")
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    // ---------------------------------------------------------------- catalog

    @Test
    void catalogIsSeededAndSearchableByEveryone() throws Exception {
        mvc.perform(get("/api/lab-tests").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(22)));
        mvc.perform(get("/api/lab-tests").param("q", "lipid").cookie(doctor))
                .andExpect(jsonPath("$[0].code").value("LIPID"))
                .andExpect(jsonPath("$[0].requiredTubeType").value("SST"))
                .andExpect(jsonPath("$[0].prepInstructions", startsWith("Fast for 10")));
    }

    @Test
    void aTestListsItsParametersWithReferenceRanges() throws Exception {
        mvc.perform(get("/api/lab-tests/" + test("CBC")).cookie(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parameters", hasSize(5)))
                .andExpect(jsonPath("$.parameters[0].name").value("Haemoglobin"))
                .andExpect(jsonPath("$.parameters[0].unit").value("g/dL"))
                .andExpect(jsonPath("$.parameters[0].refLow").value(12.0))
                .andExpect(jsonPath("$.parameters[0].criticalLow").value(7.0));
    }

    @Test
    void adminsAddEditAndRetireTests() throws Exception {
        String body = """
                {"code":"zz1","name":"Serum Ferritin","category":"Biochemistry","sampleType":"BLOOD",
                 "requiredTubeType":"SST","price":800,"turnaroundHours":24,
                 "parameters":[{"name":"Ferritin","unit":"ng/mL","refLow":30,"refHigh":400}]}""";
        String id = read(mvc.perform(json(post("/api/lab-tests"), body).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ZZ1"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn(), "$.id");

        mvc.perform(json(post("/api/lab-tests"), body).cookie(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_TAKEN"));
        mvc.perform(json(post("/api/lab-tests"), body.replace("zz1", "zz2")).cookie(doctor))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/lab-tests"), body.replace("zz1", "zz3").replace("\"refLow\":30", "\"refLow\":500"))
                        .cookie(admin))
                .andExpect(status().isBadRequest());

        mvc.perform(json(put("/api/lab-tests/" + id), body.replace("800", "850").replace("}]}", "},"
                        + "{\"name\":\"Iron\",\"unit\":\"µg/dL\",\"refLow\":60,\"refHigh\":170}],\"active\":false}"))
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(850))
                .andExpect(jsonPath("$.parameters", hasSize(2)))
                .andExpect(jsonPath("$.active").value(false));

        // Retired: hidden from the picker and from direct lookups, but still in the admin's catalog.
        mvc.perform(get("/api/lab-tests").param("q", "ferritin").cookie(doctor)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/lab-tests/" + id).cookie(doctor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/lab-tests").param("q", "ferritin").param("includeInactive", "true").cookie(admin))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------------------------------------------------------------- ordering from a consultation

    @Test
    void orderingInAConsultationCreatesTheOrderAtOnce() throws Exception {
        String consultationId = consultation();

        MvcResult created = orderInConsultation(consultationId, "CBC", "LIPID")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderCode").value(org.hamcrest.Matchers.matchesPattern("LO-\\d{6}")))
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.priority").value("ROUTINE"))
                .andExpect(jsonPath("$.patient.id").value(patientId.toString()))
                .andExpect(jsonPath("$.doctor.fullName").value("Test DOCTOR"))
                .andExpect(jsonPath("$.consultationId").value(consultationId))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.total").value(950.0))
                .andReturn();

        mvc.perform(get("/api/consultations/" + consultationId + "/lab-order").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(read(created, "$.id")));
    }

    @Test
    void orderingAgainInTheSameConsultationAddsToItsOrder() throws Exception {
        String consultationId = consultation();
        String first = read(orderInConsultation(consultationId, "CBC").andReturn(), "$.id");

        orderInConsultation(consultationId, "FBS", "CBC")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.items", hasSize(2)));
        orderInConsultation(consultationId, "CBC")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ORDERED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lab_orders", Long.class)).isEqualTo(1);
    }

    @Test
    void aConsultationWithoutAnOrderHasNoContent() throws Exception {
        mvc.perform(get("/api/consultations/" + consultation() + "/lab-order").cookie(doctor))
                .andExpect(status().isNoContent());
    }

    @Test
    void aFinishedConsultationTakesNoMoreOrders() throws Exception {
        String consultationId = consultation();
        mvc.perform(json(post("/api/consultations/" + consultationId + "/complete"), "{\"diagnosis\":\"Fatigue\"}")
                .cookie(doctor)).andExpect(status().isOk());

        orderInConsultation(consultationId, "CBC")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSULTATION_LOCKED"));
    }

    @Test
    void onlyTheConsultingDoctorOrdersIntoAConsultation() throws Exception {
        order(otherDoctor, "{\"consultationId\":\"" + consultation() + "\",\"testIds\":" + ids("CBC") + "}")
                .andExpect(status().isNotFound());
        order(patient, "{\"patientId\":\"" + patientId + "\",\"testIds\":" + ids("CBC") + "}")
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- direct orders

    @Test
    void directOrdersNeedACareRelationship() throws Exception {
        consultation(); // gives the doctor an appointment with the patient

        directOrder("ROUTINE", "TSH");
        order(otherDoctor, "{\"patientId\":\"" + patientId + "\",\"testIds\":" + ids("TSH") + "}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
        order(doctor, "{\"testIds\":" + ids("TSH") + "}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrRetiredTestsAreRejected() throws Exception {
        consultation();
        order(doctor, "{\"patientId\":\"" + patientId + "\",\"testIds\":[\"" + UUID.randomUUID() + "\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TEST"));
        order(doctor, "{\"patientId\":\"" + patientId + "\",\"testIds\":[]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void linesKeepThePriceTheyWereOrderedAt() throws Exception {
        consultation();
        String orderId = directOrder("ROUTINE", "ESR");
        jdbc.update("UPDATE lab_tests SET price = 999 WHERE code = 'ESR'");
        try {
            mvc.perform(get("/api/lab-orders/" + orderId).cookie(doctor))
                    .andExpect(jsonPath("$.items[0].price").value(150.0))
                    .andExpect(jsonPath("$.total").value(150.0));
        } finally {
            jdbc.update("UPDATE lab_tests SET price = 150 WHERE code = 'ESR'");
        }
    }

    // ---------------------------------------------------------------- changing an order

    @Test
    void removingTheLastTestCancelsTheOrderAndTheConsultationCanOrderAgain() throws Exception {
        String consultationId = consultation();
        MvcResult created = orderInConsultation(consultationId, "CBC", "ESR").andReturn();
        String orderId = read(created, "$.id");

        mvc.perform(delete("/api/lab-orders/" + orderId + "/items/" + read(created, "$.items[0].id"))
                        .header(CsrfHeaderFilter.HEADER, "1").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(delete("/api/lab-orders/" + orderId + "/items/" + read(created, "$.items[1].id"))
                        .header(CsrfHeaderFilter.HEADER, "1").cookie(doctor))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("All tests removed"));

        mvc.perform(get("/api/consultations/" + consultationId + "/lab-order").cookie(doctor))
                .andExpect(status().isNoContent());
        orderInConsultation(consultationId, "CBC")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(orderId)));
    }

    @Test
    void onlyTheOrderingDoctorCancels() throws Exception {
        consultation();
        String orderId = directOrder("ROUTINE", "CBC");

        mvc.perform(json(post("/api/lab-orders/" + orderId + "/cancel"), "{}").cookie(otherDoctor))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/lab-orders/" + orderId + "/cancel"), "{\"reason\":\"Ordered by mistake\"}")
                        .cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Ordered by mistake"));
        mvc.perform(json(post("/api/lab-orders/" + orderId + "/cancel"), "{}").cookie(doctor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CANCELLED"));
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void patientsSeeTheirOrdersWithPrepButNotTheDoctorsNotes() throws Exception {
        consultation();
        String orderId = directOrder("ROUTINE", "FBS", "CBC");

        mvc.perform(get("/api/lab-orders/mine").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].items[0].name").value("Fasting Blood Sugar"))
                .andExpect(jsonPath("$[0].items[0].prepInstructions", startsWith("Fast for 8")))
                .andExpect(jsonPath("$[0].clinicalNotes").doesNotExist());
        mvc.perform(get("/api/lab-orders/" + orderId).cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicalNotes").doesNotExist());
        mvc.perform(get("/api/lab-orders/" + orderId).cookie(otherPatient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/lab-orders/" + orderId).cookie(reception)).andExpect(status().isForbidden());
    }

    @Test
    void doctorsReadingAnOrderIsLogged() throws Exception {
        consultation();
        String orderId = directOrder("ROUTINE", "CBC");

        mvc.perform(get("/api/lab-orders/" + orderId).cookie(doctor))
                .andExpect(jsonPath("$.clinicalNotes").value("Rule out anaemia"));
        mvc.perform(get("/api/lab-orders/" + orderId).cookie(otherDoctor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/patients/" + patientId + "/lab-orders").cookie(doctor))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].testNames[0]").value("Complete Blood Count"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM patient_access_log WHERE user_id = ? AND resource = 'LAB_HISTORY'",
                Long.class, doctorUserId)).isEqualTo(2);
    }

    @Test
    void theLabQueueShowsUrgentOrdersFirst() throws Exception {
        consultation();
        String routine = directOrder("ROUTINE", "CBC", "LIPID");
        String urgent = directOrder("URGENT", "NS1");

        mvc.perform(get("/api/lab-orders").cookie(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(urgent))
                .andExpect(jsonPath("$.content[1].id").value(routine))
                .andExpect(jsonPath("$.content[1].testCount").value(2))
                .andExpect(jsonPath("$.content[1].tubes", hasSize(2)))
                .andExpect(jsonPath("$.content[1].hasPrep").value(true));
        mvc.perform(get("/api/lab-orders/" + urgent).cookie(lab))
                .andExpect(jsonPath("$.clinicalNotes").value("Rule out anaemia"));
        mvc.perform(get("/api/lab-orders").cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/lab-orders").cookie(doctor)).andExpect(status().isForbidden());
    }

    @Test
    void dashboardsShowIncomingOrdersAndTestsToGetDone() throws Exception {
        consultation();
        directOrder("URGENT", "CBC", "ESR", "FBS");

        mvc.perform(get("/api/dashboard/lab-technician").cookie(lab))
                .andExpect(jsonPath("$.widgets[0].data[0].value").value(1))
                .andExpect(jsonPath("$.widgets[0].data[1].value").value(1))
                .andExpect(jsonPath("$.widgets[1].type").value("incomingOrders"))
                .andExpect(jsonPath("$.widgets[1].data.orders", hasSize(1)))
                .andExpect(jsonPath("$.widgets[2].type").value("tubesNeeded"))
                .andExpect(jsonPath("$.widgets[2].data.EDTA").value(2))
                .andExpect(jsonPath("$.widgets[2].data.FLUORIDE").value(1));

        // The patient is still checked in, so the queue card comes first and the tests right after it.
        mvc.perform(get("/api/dashboard/patient").cookie(patient))
                .andExpect(jsonPath("$.widgets[1].type").value("myLabOrders"))
                .andExpect(jsonPath("$.widgets[1].data[0].items", hasSize(3)));
    }
}
