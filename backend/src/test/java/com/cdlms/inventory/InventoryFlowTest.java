package com.cdlms.inventory;

import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 09: inventory levels, movements, low-stock alerts and access (Rules.md §4, ADR-026). */
class InventoryFlowTest extends IntegrationTest {

    private Cookie lab;
    private Cookie admin;
    private Cookie doctor;
    private Cookie reception;
    private Cookie patient;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE inventory_movements, inventory_items CASCADE");
        lab = testUsers.loginCookie(testUsers.create(Role.LAB_TECHNICIAN));
        admin = testUsers.loginCookie(testUsers.create(Role.ADMIN));
        doctor = testUsers.loginCookie(testUsers.create(Role.DOCTOR));
        reception = testUsers.loginCookie(testUsers.create(Role.RECEPTIONIST));
        patient = testUsers.loginCookie(testUsers.create(Role.PATIENT));
    }

    // ---------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(CsrfHeaderFilter.HEADER, "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }

    /** Adds an item as the admin; returns its id. */
    private String item(String name, String category, int opening, int threshold) throws Exception {
        return read(mvc.perform(json(post("/api/inventory"), "{\"name\":\"" + name + "\",\"category\":\"" + category
                        + "\",\"unit\":\"tubes\",\"openingStock\":" + opening + ",\"lowStockThreshold\":" + threshold + "}").cookie(admin))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    private ResultActions adjust(Cookie who, String id, int delta, String reason) throws Exception {
        return mvc.perform(json(patch("/api/inventory/" + id + "/stock"),
                "{\"delta\":" + delta + ",\"reason\":\"" + reason + "\",\"note\":\"test\"}").cookie(who));
    }

    private int stock(String id) {
        return jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE id = ?", Integer.class, UUID.fromString(id));
    }

    // ---------------------------------------------------------------- items

    @Test
    void anAdminAddsAnItemWithAnOpeningCountThatIsRecorded() throws Exception {
        String id = item("EDTA tube", "TUBE", 40, 10);

        mvc.perform(get("/api/inventory").cookie(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("EDTA tube"))
                .andExpect(jsonPath("$[0].currentStock").value(40))
                .andExpect(jsonPath("$[0].lowStock").value(false));
        mvc.perform(get("/api/inventory/" + id + "/movements").cookie(lab))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reason").value("OPENING"))
                .andExpect(jsonPath("$[0].delta").value(40))
                .andExpect(jsonPath("$[0].stockAfter").value(40))
                .andExpect(jsonPath("$[0].actorName").value("Test ADMIN"));
    }

    @Test
    void namesAreUniqueIgnoringCase() throws Exception {
        item("Gloves (M)", "CONSUMABLE", 0, 5);

        mvc.perform(json(post("/api/inventory"), "{\"name\":\"gloves (m)\",\"category\":\"CONSUMABLE\",\"unit\":\"pairs\",\"lowStockThreshold\":1}")
                        .cookie(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NAME_TAKEN"));
    }

    @Test
    void onlyAdminsAddOrEditItems() throws Exception {
        String id = item("SST tube", "TUBE", 10, 5);
        String create = "{\"name\":\"Other\",\"category\":\"OTHER\",\"unit\":\"box\",\"lowStockThreshold\":1}";
        String edit = "{\"name\":\"SST tube\",\"category\":\"TUBE\",\"unit\":\"tubes\",\"lowStockThreshold\":8,\"active\":true}";

        for (Cookie who : new Cookie[] {lab, doctor, reception, patient}) {
            mvc.perform(json(post("/api/inventory"), create).cookie(who)).andExpect(status().isForbidden());
            mvc.perform(json(put("/api/inventory/" + id), edit).cookie(who)).andExpect(status().isForbidden());
        }
        mvc.perform(json(put("/api/inventory/" + id), edit).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lowStockThreshold").value(8));
        // Editing details never touches the level.
        assertThat(stock(id)).isEqualTo(10);
    }

    @Test
    void onlyTheLabAndAdminsSeeInventory() throws Exception {
        item("Swab tube", "TUBE", 5, 2);

        for (Cookie who : new Cookie[] {doctor, reception, patient}) {
            mvc.perform(get("/api/inventory").cookie(who)).andExpect(status().isForbidden());
            mvc.perform(get("/api/inventory/alerts").cookie(who)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/inventory").cookie(admin)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- stock changes

    @Test
    void restockingAndUsingChangeTheLevelAndAreRecorded() throws Exception {
        String id = item("Urine cup", "TUBE", 20, 5);

        adjust(lab, id, 30, "RESTOCK").andExpect(status().isOk()).andExpect(jsonPath("$.currentStock").value(50));
        adjust(lab, id, -12, "USED").andExpect(jsonPath("$.currentStock").value(38));
        adjust(admin, id, -3, "WASTAGE").andExpect(jsonPath("$.currentStock").value(35));
        adjust(lab, id, 2, "CORRECTION").andExpect(jsonPath("$.currentStock").value(37));
        adjust(lab, id, -7, "CORRECTION").andExpect(jsonPath("$.currentStock").value(30));

        mvc.perform(get("/api/inventory/" + id + "/movements").cookie(lab))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].reason").value("CORRECTION"))
                .andExpect(jsonPath("$[0].stockAfter").value(30))
                .andExpect(jsonPath("$[0].actorName").value("Test LAB_TECHNICIAN"))
                .andExpect(jsonPath("$[5].reason").value("OPENING"));
    }

    @Test
    void theLevelCannotGoBelowZero() throws Exception {
        String id = item("Reagent A", "REAGENT", 4, 2);

        adjust(lab, id, -5, "USED").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(stock(id)).isEqualTo(4);
        adjust(lab, id, -4, "USED").andExpect(status().isOk()).andExpect(jsonPath("$.currentStock").value(0));
        adjust(lab, id, -1, "USED").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements", Long.class)).isEqualTo(2); // opening + the one that worked
    }

    @Test
    void theDirectionMustMatchTheReason() throws Exception {
        String id = item("Alcohol swabs", "CONSUMABLE", 10, 2);

        adjust(lab, id, -1, "RESTOCK").andExpect(status().isBadRequest());
        adjust(lab, id, 1, "USED").andExpect(status().isBadRequest());
        adjust(lab, id, 1, "WASTAGE").andExpect(status().isBadRequest());
        adjust(lab, id, 5, "OPENING").andExpect(status().isBadRequest());
        adjust(lab, id, 0, "CORRECTION").andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/inventory/" + id + "/stock"), "{\"delta\":1,\"reason\":\"SOMETHING\"}").cookie(lab))
                .andExpect(status().isBadRequest());
        assertThat(stock(id)).isEqualTo(10);
    }

    @Test
    void peopleAdjustingAtTheSameTimeCannotOverdrawTheStock() throws Exception {
        String id = item("Citrate tube", "TUBE", 5, 2);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            calls.add(pool.submit(() -> {
                go.await();
                return adjust(lab, id, -1, "USED").andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int ok = 0;
        int refused = 0;
        for (Future<Integer> call : calls) {
            int code = call.get();
            if (code == 200) {
                ok++;
            } else if (code == 409) {
                refused++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(5);
        assertThat(refused).isEqualTo(5);
        assertThat(stock(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE reason = 'USED'", Long.class)).isEqualTo(5);
    }

    @Test
    void aRetiredItemCannotBeAdjustedAndIsHiddenByDefault() throws Exception {
        String id = item("Old reagent", "REAGENT", 3, 1);
        mvc.perform(json(put("/api/inventory/" + id),
                        "{\"name\":\"Old reagent\",\"category\":\"REAGENT\",\"unit\":\"tubes\",\"lowStockThreshold\":1,\"active\":false}").cookie(admin))
                .andExpect(status().isOk());

        adjust(lab, id, 1, "RESTOCK").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_RETIRED"));
        mvc.perform(get("/api/inventory").cookie(lab)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/inventory").param("includeInactive", "true").cookie(admin)).andExpect(jsonPath("$", hasSize(1)));
        adjust(lab, UUID.randomUUID().toString(), 1, "RESTOCK").andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- low stock

    @Test
    void anItemIsLowOnceItDropsBelowItsThreshold() throws Exception {
        String id = item("Heparin tube", "TUBE", 12, 10);

        mvc.perform(get("/api/inventory/alerts").cookie(lab)).andExpect(jsonPath("$.lowCount").value(0));
        adjust(lab, id, -2, "USED").andExpect(jsonPath("$.currentStock").value(10)).andExpect(jsonPath("$.lowStock").value(false));
        adjust(lab, id, -1, "USED").andExpect(jsonPath("$.currentStock").value(9)).andExpect(jsonPath("$.lowStock").value(true));

        mvc.perform(get("/api/inventory/alerts").cookie(lab))
                .andExpect(jsonPath("$.lowCount").value(1))
                .andExpect(jsonPath("$.outCount").value(0))
                .andExpect(jsonPath("$.items[0].name").value("Heparin tube"));
        adjust(lab, id, 20, "RESTOCK").andExpect(jsonPath("$.lowStock").value(false));
        mvc.perform(get("/api/inventory/alerts").cookie(lab)).andExpect(jsonPath("$.lowCount").value(0));
    }

    @Test
    void emptyItemsComeFirstAndAThresholdOfZeroIsNeverWatched() throws Exception {
        item("Plain tube", "TUBE", 3, 10);
        String empty = item("Fluoride tube", "TUBE", 5, 10);
        item("Tissue paper", "OTHER", 0, 0);
        item("Stool cup", "TUBE", 50, 10);
        adjust(lab, empty, -5, "USED").andExpect(status().isOk());

        mvc.perform(get("/api/inventory").cookie(lab))
                .andExpect(jsonPath("$[0].name").value("Fluoride tube"))
                .andExpect(jsonPath("$[0].outOfStock").value(true))
                .andExpect(jsonPath("$[1].name").value("Plain tube"))
                .andExpect(jsonPath("$[1].lowStock").value(true))
                .andExpect(jsonPath("$[1].outOfStock").value(false));
        mvc.perform(get("/api/inventory").param("lowOnly", "true").cookie(lab)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/inventory").param("q", "tissue").cookie(lab))
                .andExpect(jsonPath("$[0].lowStock").value(false)).andExpect(jsonPath("$[0].outOfStock").value(false));
        mvc.perform(get("/api/inventory").param("category", "OTHER").cookie(lab)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/inventory/alerts").cookie(lab))
                .andExpect(jsonPath("$.lowCount").value(2))
                .andExpect(jsonPath("$.outCount").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Fluoride tube"));
    }

    // ---------------------------------------------------------------- the database backs it up

    @Test
    void movementsAndItemsCannotBeRewrittenOrDeleted() throws Exception {
        String id = item("SST tube", "TUBE", 10, 4);
        adjust(lab, id, -1, "USED").andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_movements SET delta = 99")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_movements")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_items")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_items SET current_stock = -1")).isInstanceOf(DataAccessException.class);
        // A movement whose direction contradicts its reason is refused.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO inventory_movements (item_id, delta, stock_after, reason, actor_user_id) "
                + "SELECT ?, 5, 5, 'USED', id FROM users LIMIT 1", UUID.fromString(id))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void theDashboardsOfTheLabAndAdminShowTheAlerts() throws Exception {
        String id = item("EDTA tube", "TUBE", 6, 10);

        for (Cookie who : new Cookie[] {lab, admin}) {
            String path = who == lab ? "/api/dashboard/lab-technician" : "/api/dashboard/admin";
            mvc.perform(get(path).cookie(who))
                    .andExpect(jsonPath("$.widgets[?(@.type == 'lowStock')].data.lowCount").value(1))
                    .andExpect(jsonPath("$.widgets[?(@.type == 'lowStock')].data.items[0].id").value(id));
        }
    }
}
