package io.github.archetom.crm;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Confirms the B15 endpoints remain behind the production authentication chain. */
@RequiresDatabase
class B15RouteIntegrationTest extends BaseIntegrationTest {

    @Test
    void b15RoutesRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/activities/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/activities/options"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/activities/options")
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/return-visits/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/return-visits/options"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/worklist"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/worklist/acknowledgements")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
