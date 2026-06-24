package com.iramil73.booking;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BookingFlowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousRequestToProtectedEndpointIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bookings/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeredUserCanBookAndConflictsAreRejected() throws Exception {
        String userToken = register("flow.user@example.com", "password123", "Flow User");
        String adminToken = login("admin@booking.local", "admin12345");

        // A near-future slot inside working hours and the advance horizon.
        String day = LocalDate.now().plusDays(2).toString();
        String start = day + "T10:00:00";
        String end = day + "T11:00:00";

        // Admin creates a room.
        String roomResponse = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearer(adminToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"IT Room\",\"capacity\":10,\"pricePerHour\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long roomId = ((Number) JsonPath.read(roomResponse, "$.id")).longValue();

        // User books a slot.
        String bookingResponse = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(APPLICATION_JSON)
                        .content(bookingJson(roomId, "Team sync", start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long bookingId = ((Number) JsonPath.read(bookingResponse, "$.id")).longValue();

        // Overlapping slot in the same room -> 409.
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(APPLICATION_JSON)
                        .content(bookingJson(roomId, "Clash", day + "T10:30:00", day + "T11:30:00")))
                .andExpect(status().isConflict());

        // The user sees their one booking.
        mockMvc.perform(get("/api/bookings/my").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Cancel it, then the same slot can be booked again.
        mockMvc.perform(delete("/api/bookings/{id}", bookingId).header("Authorization", bearer(userToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(userToken))
                        .contentType(APPLICATION_JSON)
                        .content(bookingJson(roomId, "Rebooked", start, end)))
                .andExpect(status().isCreated());
    }

    @Test
    void nonAdminCannotCreateRoom() throws Exception {
        String userToken = register("plain.user@example.com", "password123", "Plain User");
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearer(userToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Forbidden Room\",\"capacity\":4,\"pricePerHour\":0}"))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String register(String email, String password, String fullName) throws Exception {
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"%s\"}", email, password, fullName);
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String login(String email, String password) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String bookingJson(long roomId, String title, String start, String end) {
        return String.format(
                "{\"roomId\":%d,\"title\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\"}",
                roomId, title, start, end);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
