package com.cdlms.auth;

import com.cdlms.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The endpoint list is not published unless someone turns the docs on (Docs/Security.md §4). */
class ApiDocsTest extends IntegrationTest {

    @Test
    void theApiDocsAreOffByDefault() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
