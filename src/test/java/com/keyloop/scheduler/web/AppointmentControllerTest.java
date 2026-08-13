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

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void createAppointment_withExplicitTechnicianId_usesThatTechnician() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(20).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content("""
                                {"vehicleId":1,"serviceType":"OIL_CHANGE","dealershipId":1,"technicianId":1,"desiredStart":"%s","desiredEnd":"%s"}
                                """.formatted(ISO.format(start), ISO.format(end))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicianId").value(1));
    }

    @Test
    void createAppointment_withTechnicianAtWrongDealership_returns404() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(21).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        // Technician 4 (Morgan Blake) is seeded at dealership 2, not 1.
        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content("""
                                {"vehicleId":1,"serviceType":"OIL_CHANGE","dealershipId":1,"technicianId":4,"desiredStart":"%s","desiredEnd":"%s"}
                                """.formatted(ISO.format(start), ISO.format(end))))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAppointment_withBusyExplicitTechnician_returns409() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(22).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        String body = """
                {"vehicleId":1,"serviceType":"OIL_CHANGE","dealershipId":1,"technicianId":1,"desiredStart":"%s","desiredEnd":"%s"}
                """.formatted(ISO.format(start), ISO.format(end));

        mockMvc.perform(post("/appointments").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/appointments").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createAppointment_guestBooking_createsCustomerAndVehicleAndReusesOnSecondBooking() throws Exception {
        LocalDateTime start1 = LocalDateTime.now().plusDays(23).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end1 = start1.plusHours(1);
        String email = "guest-" + System.nanoTime() + "@example.com";
        String vin = "V" + Long.toHexString(System.nanoTime());

        String firstResponse = mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(guestRequestJson(email, vin, start1, end1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleVin").value(vin))
                .andReturn().getResponse().getContentAsString();
        Long firstVehicleId = ((Number) JsonPath.read(firstResponse, "$.vehicleId")).longValue();

        LocalDateTime start2 = start1.plusHours(2);
        LocalDateTime end2 = start2.plusHours(1);
        String secondResponse = mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(guestRequestJson(email, vin, start2, end2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long secondVehicleId = ((Number) JsonPath.read(secondResponse, "$.vehicleId")).longValue();

        assertThat(secondVehicleId).isEqualTo(firstVehicleId);
    }

    private String createRequestJson(Long vehicleId, String serviceType, Long dealershipId, LocalDateTime start, LocalDateTime end) {
        return """
                {"vehicleId":%d,"serviceType":"%s","dealershipId":%d,"desiredStart":"%s","desiredEnd":"%s"}
                """.formatted(vehicleId, serviceType, dealershipId, ISO.format(start), ISO.format(end));
    }

    private String guestRequestJson(String email, String vin, LocalDateTime start, LocalDateTime end) {
        return """
                {"serviceType":"OIL_CHANGE","dealershipId":1,"desiredStart":"%s","desiredEnd":"%s",
                 "customerName":"Guest Test","customerEmail":"%s","customerPhone":"555-0199",
                 "vehicleVin":"%s","vehicleMake":"Toyota","vehicleModel":"Corolla"}
                """.formatted(ISO.format(start), ISO.format(end), email, vin);
    }
}
