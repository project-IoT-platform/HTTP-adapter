package main.java.org.example.api.repository;

import main.java.org.example.api.model.TelemetryRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TelemetryRepository {

    /** Новые сообщения первыми; from и to необязательны и включаются в диапазон. */
    List<TelemetryRecord> find(String deviceId, Instant from, Instant to, int limit);

    Optional<TelemetryRecord> findLatest(String deviceId);
}
