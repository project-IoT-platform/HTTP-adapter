package main.java.org.example.api.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import main.java.org.example.api.model.TelemetryRecord;
import main.java.org.example.api.model.Device;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Демо-данные без базы: запуск с профилем memory. Нужен, пока Postgres не подключён. */
@Repository
@Profile("memory")
public class InMemoryStore implements DeviceRepository, TelemetryRepository {

    private final Map<String, Device> devices = new TreeMap<>();
    private final Map<String, List<TelemetryRecord>> telemetry = new TreeMap<>();

    public InMemoryStore(ObjectMapper mapper) throws Exception {
        Instant now = Instant.now();
        for (String id : List.of("sensor-1", "sensor-2")) {
            List<TelemetryRecord> records = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                records.add(new TelemetryRecord(i + 1, now.minus(Duration.ofMinutes(5L * (5 - i))),
                        mapper.readTree("{\"temp\":" + (20 + i + 0.5) + "}")));
            }
            telemetry.put(id, records);
            devices.put(id, new Device(id, now.minus(Duration.ofDays(1)), records.get(4).receivedAt()));
        }
    }

    @Override
    public List<Device> findAll(int limit, int offset) {
        return devices.values().stream().skip(offset).limit(limit).toList();
    }

    @Override
    public Optional<Device> findById(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    @Override
    public List<TelemetryRecord> find(String deviceId, Instant from, Instant to, int limit) {
        return telemetry.getOrDefault(deviceId, List.of()).stream()
                .filter(r -> from == null || !r.receivedAt().isBefore(from))
                .filter(r -> to == null || !r.receivedAt().isAfter(to))
                .sorted(Comparator.comparing(TelemetryRecord::receivedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<TelemetryRecord> findLatest(String deviceId) {
        return find(deviceId, null, null, 1).stream().findFirst();
    }
}
