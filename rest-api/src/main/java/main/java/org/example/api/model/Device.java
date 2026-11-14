package main.java.org.example.api.model;

import java.time.Instant;

/** Устройство; lastSeenAt пустое, если от него ещё не было телеметрии. */
public record Device(String deviceId, Instant createdAt, Instant lastSeenAt) {
}
