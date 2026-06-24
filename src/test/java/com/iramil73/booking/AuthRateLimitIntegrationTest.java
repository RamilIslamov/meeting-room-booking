package com.iramil73.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.capacity=3",
        "app.rate-limit.window-seconds=60"
})
class AuthRateLimitIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authEndpointIsThrottledAfterCapacity() throws Exception {
        String body = "{\"email\":\"nobody@example.com\",\"password\":\"whatever\"}";

        // Capacity is 3: the first three attempts hit the controller (401 for bad creds)...
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        // ...the fourth is rejected by the rate limiter.
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
