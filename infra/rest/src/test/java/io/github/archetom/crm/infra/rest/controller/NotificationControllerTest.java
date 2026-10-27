package io.github.archetom.crm.infra.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    @Test
    void preferenceUpdateRejectsUnknownTransportFieldsBeforeFacadeAccess() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(null, null)).build();

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true,\"endpoint\":\"attacker@example.test\"}"))
                .andExpect(status().isBadRequest());
    }
}
