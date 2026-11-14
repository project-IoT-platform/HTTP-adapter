package main.java.org.example.api.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Одно сообщение телеметрии; payload отдаётся как есть (JSON, который прислало устройство). */
public record TelemetryRecord(long messageId, Instant receivedAt, JsonNode payload) {
}
