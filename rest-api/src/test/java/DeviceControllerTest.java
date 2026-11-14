import com.fasterxml.jackson.databind.ObjectMapper;
import main.java.org.example.api.repository.*;
import main.java.org.example.api.model.*;
import main.java.org.example.api.controller.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

    private static final Instant T = Instant.parse("2026-11-14T12:00:00Z");
    private static final Device DEVICE = new Device("sensor-1", T, T);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean DeviceRepository devices;
    @MockBean TelemetryRepository telemetry;

    private TelemetryRecord record() throws Exception {
        return new TelemetryRecord(7, T, mapper.readTree("{\"temp\":21.5}"));
    }

    @Test
    void listReturnsDevices() throws Exception {
        when(devices.findAll(50, 0)).thenReturn(List.of(DEVICE));
        mvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value("sensor-1"));
    }

    @Test
    void listRejectsLimitOutOfRange() throws Exception {
        mvc.perform(get("/api/devices?limit=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/devices?limit=201")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/devices?offset=-1")).andExpect(status().isBadRequest());
    }

    @Test
    void getDeviceReturnsIt() throws Exception {
        when(devices.findById("sensor-1")).thenReturn(Optional.of(DEVICE));
        mvc.perform(get("/api/devices/sensor-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("sensor-1"));
    }

    @Test
    void unknownDeviceIs404WithErrorBody() throws Exception {
        when(devices.findById("nope")).thenReturn(Optional.empty());
        mvc.perform(get("/api/devices/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void invalidDeviceIdIs400() throws Exception {
        mvc.perform(get("/api/devices/bad$id")).andExpect(status().isBadRequest());
    }

    @Test
    void historyReturnsPayloadAsJson() throws Exception {
        when(devices.findById("sensor-1")).thenReturn(Optional.of(DEVICE));
        when(telemetry.find(eq("sensor-1"), isNull(), isNull(), eq(100))).thenReturn(List.of(record()));
        mvc.perform(get("/api/devices/sensor-1/telemetry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value(7))
                .andExpect(jsonPath("$[0].payload.temp").value(21.5));
    }

    @Test
    void historyPassesTimeRangeToRepository() throws Exception {
        when(devices.findById("sensor-1")).thenReturn(Optional.of(DEVICE));
        mvc.perform(get("/api/devices/sensor-1/telemetry?from=2026-11-14T10:00:00Z&to=2026-11-14T11:00:00Z&limit=10"))
                .andExpect(status().isOk());
        verify(telemetry).find("sensor-1", Instant.parse("2026-11-14T10:00:00Z"),
                Instant.parse("2026-11-14T11:00:00Z"), 10);
    }

    @Test
    void historyRejectsFromAfterTo() throws Exception {
        mvc.perform(get("/api/devices/sensor-1/telemetry?from=2026-11-14T11:00:00Z&to=2026-11-14T10:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyRejectsBadDateAndBadLimit() throws Exception {
        mvc.perform(get("/api/devices/sensor-1/telemetry?from=вчера")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/devices/sensor-1/telemetry?limit=1001")).andExpect(status().isBadRequest());
    }

    @Test
    void historyOfUnknownDeviceIs404() throws Exception {
        when(devices.findById("nope")).thenReturn(Optional.empty());
        mvc.perform(get("/api/devices/nope/telemetry")).andExpect(status().isNotFound());
    }

    @Test
    void latestReturnsNewestRecord() throws Exception {
        when(devices.findById("sensor-1")).thenReturn(Optional.of(DEVICE));
        when(telemetry.findLatest("sensor-1")).thenReturn(Optional.of(record()));
        mvc.perform(get("/api/devices/sensor-1/telemetry/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.temp").value(21.5));
    }

    @Test
    void latestIs404WhenDeviceHasNoTelemetry() throws Exception {
        when(devices.findById("sensor-1")).thenReturn(Optional.of(DEVICE));
        when(telemetry.findLatest(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/devices/sensor-1/telemetry/latest"))
                .andExpect(status().isNotFound());
    }
}
