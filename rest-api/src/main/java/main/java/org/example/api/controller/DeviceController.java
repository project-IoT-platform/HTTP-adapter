package main.java.org.example.api.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import main.java.org.example.api.model.Device;
import main.java.org.example.api.model.TelemetryRecord;
import main.java.org.example.api.repository.DeviceRepository;
import main.java.org.example.api.repository.TelemetryRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Только чтение: список устройств, карточка устройства, история и последнее значение телеметрии. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final String DEVICE_ID = "[A-Za-z0-9_-]{1,64}";

    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;

    public DeviceController(DeviceRepository devices, TelemetryRepository telemetry) {
        this.devices = devices;
        this.telemetry = telemetry;
    }

    @GetMapping
    public List<Device> list(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                             @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return devices.findAll(limit, offset);
    }

    @GetMapping("/{deviceId}")
    public Device get(@PathVariable @Pattern(regexp = DEVICE_ID) String deviceId) {
        return requireDevice(deviceId);
    }

    @GetMapping("/{deviceId}/telemetry")
    public List<TelemetryRecord> history(
            @PathVariable @Pattern(regexp = DEVICE_ID) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        requireDevice(deviceId);
        return telemetry.find(deviceId, from, to, limit);
    }

    @GetMapping("/{deviceId}/telemetry/latest")
    public TelemetryRecord latest(@PathVariable @Pattern(regexp = DEVICE_ID) String deviceId) {
        requireDevice(deviceId);
        return telemetry.findLatest(deviceId)
                .orElseThrow(() -> new NotFoundException("no telemetry for device " + deviceId));
    }

    private Device requireDevice(String deviceId) {
        return devices.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("device " + deviceId + " not found"));
    }
}
