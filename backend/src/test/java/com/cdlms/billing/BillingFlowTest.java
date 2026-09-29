package com.cdlms.billing;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 06: invoices from visits and lab orders, payments, discounts and access (Rules.md §3, ADR-023). */
class BillingFlowTest extends IntegrationTest {

    private Cookie reception;
    private Cookie doctor;
    private Cookie patient;
    private Cookie otherPatient;
    private Cookie lab;
    private Cookie admin;
    private UUID doctorId;
    private UUID patientId;

    @BeforeEach
    void setUp() {
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        User doctorUser = testUsers.create(Role.DOCTOR);
        doctor = testUsers.loginCookie(doctorUser);
        User patientUser = testUsers.create(Role.PATIENT);
        patient = testUsers.loginCookie(patientUser);
        otherPatient = testUsers.loginCookie(testUsers.create(Role.PATIENT, "other.patient@test.local"));
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));

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

    private String ids(String... codes) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < codes.length; i++) {
            UUID id = jdbc.queryForObject("SELECT id FROM lab_tests WHERE code = ?", UUID.class, codes[i]);
            out.append(i == 0 ? "" : ",").append('"').append(id).append('"');
        }
        return out.append(']').toString();
    }

    private String consultation() throws Exception {
        String appointmentId = read(mvc.perform(json(post("/api/queue/tokens"),
                        "{\"patientId\":\"" + patientId + "\",\"doctorId\":\"" + doctorId + "\"}").cookie(reception))
                .andExpect(status().isCreated()).andReturn(), "$.id");
        return read(mvc.perform(json(post("/api/consultations"), "{\"appointmentId\":\"" + appointmentId + "\"}")
                .cookie(doctor)).andExpect(status().isOk()).andReturn(), "$.id");
    }

    private void finish(String consultationId) throws Exception {
        mvc.perform(json(post("/api/consultations/" + consultationId + "/complete"), "{\"diagnosis\":\"Fatigue\"}")
                .cookie(doctor)).andExpect(status().isOk());
    }

    /** A finished visit with CBC (₹350) and Lipid Profile (₹600) ordered: ₹500 fee + ₹950 = ₹1,450. */
    private String visitInvoice() throws Exception {
        String consultationId = consultation();
        mvc.perform(json(post("/api/lab-orders"), "{\"consultationId\":\"" + consultationId + "\",\"testIds\":"
                + ids("CBC", "LIPID") + "}").cookie(doctor)).andExpect(status().isCreated());
        finish(consultationId);
        return jdbc.queryForObject("SELECT id FROM invoices WHERE consultation_id = ?", UUID.class,
                UUID.fromString(consultationId)).toString();
    }

    private MvcResult directOrder(String... codes) throws Exception {
        return mvc.perform(json(post("/api/lab-orders"), "{\"patientId\":\"" + patientId + "\",\"testIds\":"
                + ids(codes) + "}").cookie(doctor)).andExpect(status().isCreated()).andReturn();
    }

    private String invoiceForOrder(String orderId) {
        return jdbc.queryForObject("SELECT id FROM invoices WHERE lab_order_id = ?", UUID.class,
                UUID.fromString(orderId)).toString();
    }

    private ResultActions pay(Cookie who, String invoiceId, String amount, String method) throws Exception {
        return mvc.perform(json(post("/api/invoices/" + invoiceId + "/payments"),
                "{\"amount\":" + amount + ",\"method\":\"" + method + "\",\"reference\":\"TXN-1\"}").cookie(who));
    }

    private ResultActions discount(Cookie who, String invoiceId, String amount, String reason) throws Exception {
        String body = reason == null ? "{\"amount\":" + amount + "}"
                : "{\"amount\":" + amount + ",\"reason\":\"" + reason + "\"}";
        return mvc.perform(json(post("/api/invoices/" + invoiceId + "/discount"), body).cookie(who));
    }

    // ---------------------------------------------------------------- invoices are created by the system

    @Test
    void finishingAVisitBillsTheFeeAndTheTestsOnOneInvoice() throws Exception {
        String id = visitInvoice();

        mvc.perform(get("/api/invoices/" + id).cookie(reception))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceCode").value(org.hamcrest.Matchers.matchesPattern("INV-\\d{6}")))
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.doctorName").value("Test DOCTOR"))
                .andExpect(jsonPath("$.lines", hasSize(3)))
                .andExpect(jsonPath("$.lines[0].kind").value("CONSULTATION"))
                .andExpect(jsonPath("$.consultationFee").value(500.0))
                .andExpect(jsonPath("$.testChargesTotal").value(950.0))
                .andExpect(jsonPath("$.net").value(1450.0))
                .andExpect(jsonPath("$.balance").value(1450.0))
                .andExpect(jsonPath("$.discount").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices", Long.class)).isEqualTo(1);
    }

    @Test
    void aVisitWithoutTestsIsBilledForTheConsultationOnly() throws Exception {
        String consultationId = consultation();
        finish(consultationId);

        assertThat(jdbc.queryForObject("SELECT consultation_fee + test_charges_total FROM invoices WHERE consultation_id = ?",
                java.math.BigDecimal.class, UUID.fromString(consultationId))).isEqualByComparingTo("500");
    }

    @Test
    void aDirectLabOrderIsBilledStraightAway() throws Exception {
        consultation(); // care relationship
        String orderId = read(directOrder("ESR"), "$.id");

        mvc.perform(get("/api/invoices/" + invoiceForOrder(orderId)).cookie(reception))
                .andExpect(jsonPath("$.labOrderCode").exists())
                .andExpect(jsonPath("$.consultationFee").value(0.0))
                .andExpect(jsonPath("$.net").value(150.0));
    }

    // ---------------------------------------------------------------- payments

    @Test
    void partPaymentsAddUpUntilTheInvoiceIsPaid() throws Exception {
        String id = visitInvoice();

        pay(reception, id, "1000", "CASH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balance").value(450.0));
        pay(reception, id, "500", "CARD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_TOO_HIGH"));
        pay(reception, id, "450", "UPI")
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.payments", hasSize(2)))
                .andExpect(jsonPath("$.payments[1].method").value("UPI"))
                .andExpect(jsonPath("$.payments[1].receivedBy").value("Test RECEPTIONIST"));
        pay(reception, id, "1", "CASH")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVOICE_CLOSED"));

        assertThatThrownBy(() -> jdbc.update("UPDATE payments SET amount = 1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoice_events")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void invalidPaymentsAreRejected() throws Exception {
        String id = visitInvoice();
        pay(reception, id, "0", "CASH").andExpect(status().isBadRequest());
        pay(reception, id, "10.555", "CASH").andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/invoices/" + id + "/payments"), "{\"amount\":10,\"method\":\"CHEQUE\"}").cookie(reception))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- discounts

    @Test
    void aDiscountNeedsAReasonAndRecordsWhoGaveIt() throws Exception {
        String id = visitInvoice();

        discount(reception, id, "100", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        discount(reception, id, "100", "Senior citizen")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.net").value(1350.0))
                .andExpect(jsonPath("$.discount.amount").value(100.0))
                .andExpect(jsonPath("$.discount.reason").value("Senior citizen"))
                .andExpect(jsonPath("$.discount.appliedBy").value("Test RECEPTIONIST"))
                .andExpect(jsonPath("$.discount.appliedByRole").value("RECEPTIONIST"));

        // The database itself refuses a discount without who applied it.
        assertThatThrownBy(() -> jdbc.update("UPDATE invoices SET discount_by_user_id = NULL"))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice_events WHERE type = 'DISCOUNT_APPLIED'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void theFrontDeskIsCappedAndAnAdminCanGoFurther() throws Exception {
        String id = visitInvoice(); // ₹1,450; the front-desk cap is 20% = ₹290

        discount(reception, id, "300", "Staff family")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DISCOUNT_OVER_LIMIT"));
        discount(admin, id, "300", "Staff family").andExpect(status().isOk());
        discount(admin, id, "2000", "Too much")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DISCOUNT_TOO_HIGH"));

        pay(reception, id, "1100", "CASH").andExpect(status().isOk());
        discount(admin, id, "400", "More")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DISCOUNT_TOO_HIGH"));
    }

    // ---------------------------------------------------------------- changes to the order

    @Test
    void removingABilledTestTakesItOffTheInvoiceUnlessPaid() throws Exception {
        consultation();
        MvcResult order = directOrder("CBC", "ESR");
        String orderId = read(order, "$.id");
        String invoiceId = invoiceForOrder(orderId);

        mvc.perform(delete("/api/lab-orders/" + orderId + "/items/" + read(order, "$.items[1].id"))
                .header(CsrfHeaderFilter.HEADER, "1").cookie(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/" + invoiceId).cookie(reception))
                .andExpect(jsonPath("$.net").value(350.0))
                .andExpect(jsonPath("$.lines[1].voidedAt").exists());

        pay(reception, invoiceId, "350", "CASH").andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(json(post("/api/lab-orders/" + orderId + "/cancel"), "{}").cookie(doctor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PAID"));
    }

    @Test
    void cancellingAnUnpaidDirectOrderVoidsItsInvoice() throws Exception {
        consultation();
        String orderId = read(directOrder("TSH"), "$.id");
        String invoiceId = invoiceForOrder(orderId);

        mvc.perform(json(post("/api/lab-orders/" + orderId + "/cancel"), "{}").cookie(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/" + invoiceId).cookie(reception)).andExpect(jsonPath("$.status").value("VOID"));
        mvc.perform(get("/api/invoices/" + invoiceId).cookie(patient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices").param("status", "OUTSTANDING").cookie(reception))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------------------------------------------------------------- who sees what

    @Test
    void patientsSeeTheirOwnBillsOnly() throws Exception {
        String id = visitInvoice();

        mvc.perform(get("/api/invoices/mine").cookie(patient))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].balance").value(1450.0));
        mvc.perform(get("/api/invoices/" + id).cookie(patient)).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/" + id).cookie(otherPatient)).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices/" + id).cookie(doctor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/invoices/" + id).cookie(lab)).andExpect(status().isForbidden());
        pay(patient, id, "10", "UPI").andExpect(status().isForbidden());
        pay(admin, id, "10", "UPI").andExpect(status().isForbidden());
        discount(patient, id, "10", "Please").andExpect(status().isForbidden());
    }

    @Test
    void theCounterListsOutstandingBillsAndFindsThemBySearch() throws Exception {
        String id = visitInvoice();

        mvc.perform(get("/api/invoices").cookie(reception))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].patientName").value("Test PATIENT"))
                .andExpect(jsonPath("$.content[0].lineCount").value(3));
        mvc.perform(get("/api/invoices").param("q", "nobody").cookie(reception))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/patients/" + patientId + "/invoices").cookie(reception))
                .andExpect(jsonPath("$", hasSize(1)));

        pay(reception, id, "1450", "CASH");
        mvc.perform(get("/api/invoices").cookie(reception)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/invoices").param("status", "PAID").cookie(reception))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/invoices").param("status", "WHATEVER").cookie(reception))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theInvoicePdfIsGeneratedForThePatient() throws Exception {
        String id = visitInvoice();
        discount(reception, id, "50", "Loyalty");
        pay(reception, id, "500", "CASH");

        MvcResult pdf = mvc.perform(get("/api/invoices/" + id + "/pdf").param("download", "true").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("INV-");
    }

    @Test
    void invoiceAmountsPrintWithIndianGrouping() {
        assertThat(InvoicePdf.rupees(new java.math.BigDecimal("125000"))).isEqualTo("Rs. 1,25,000.00");
        assertThat(InvoicePdf.rupees(new java.math.BigDecimal("1450.5"))).isEqualTo("Rs. 1,450.50");
        assertThat(InvoicePdf.rupees(new java.math.BigDecimal("99"))).isEqualTo("Rs. 99.00");
        assertThat(InvoicePdf.rupees(new java.math.BigDecimal("12345678"))).isEqualTo("Rs. 1,23,45,678.00");
    }

    @Test
    void dashboardsShowBillsAndTakings() throws Exception {
        String id = visitInvoice();
        pay(reception, id, "450", "UPI");

        mvc.perform(get("/api/dashboard/receptionist").cookie(reception))
                .andExpect(jsonPath("$.widgets[?(@.type == 'outstandingBills')].data.count").value(1))
                .andExpect(jsonPath("$.widgets[?(@.type == 'outstandingBills')].data.amount").value(1000.0))
                .andExpect(jsonPath("$.widgets[?(@.type == 'collections')].data.upi").value(450.0));
        mvc.perform(get("/api/dashboard/patient").cookie(patient))
                .andExpect(jsonPath("$.widgets[?(@.type == 'myBills')].data[0].balance").value(1000.0));
    }
}
