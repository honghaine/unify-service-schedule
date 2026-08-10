package com.keyloop.scheduler.web;

import com.jayway.jsonpath.JsonPath;
import com.keyloop.scheduler.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST layer tests against the full application context (real
 * MySQL via Testcontainers, seeded by Flyway) covering the create/read
 * endpoints, validation, not-found, and the 409 conflict path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AppointmentControllerTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAppointment_returns201AndPersistsAppointment() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(createRequestJson(1L, "OIL_CHANGE", 1L, start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.technicianId").value(1))
                .andExpect(jsonPath("$.serviceType").value("OIL_CHANGE"));
    }

    @Test
    void createAppointment_conflictingSlotReturns409() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        String requestJson = createRequestJson(1L, "OIL_CHANGE", 1L, start, end);

        mockMvc.perform(post("/appointments").contentType("application/json").content(requestJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/appointments").contentType("application/json").content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createAppointment_missingRequiredFieldReturns400() throws Exception {
        String invalidJson = """
                {"serviceType":"OIL_CHANGE","dealershipId":1,"desiredStart":"2027-01-01T09:00:00","desiredEnd":"2027-01-01T10:00:00"}
                """;

        mockMvc.perform(post("/appointments").contentType("application/json").content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createAppointment_endBeforeStartReturns400() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(4).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.minusHours(1);

        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(createRequestJson(1L, "OIL_CHANGE", 1L, start, end)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAppointment_unknownVehicleReturns404() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(5).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(createRequestJson(9999L, "OIL_CHANGE", 1L, start, end)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAppointment_unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/appointments/999999")).andExpect(status().isNotFound());
    }

    @Test
    void getAppointment_afterCreateReturns200() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(6).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        String response = mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(createRequestJson(2L, "BRAKES", 1L, start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/appointments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.technicianName").value("Jordan Lee"));
    }

    private String createRequestJson(Long vehicleId, String serviceType, Long dealershipId, LocalDateTime start, LocalDateTime end) {
        return """
                {"vehicleId":%d,"serviceType":"%s","dealershipId":%d,"desiredStart":"%s","desiredEnd":"%s"}
                """.formatted(vehicleId, serviceType, dealershipId, ISO.format(start), ISO.format(end));
    }
}
