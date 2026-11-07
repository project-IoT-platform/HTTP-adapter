package org.example.model;

public record Envelope(Long messageId, String timestamp, String payload, String checksum) {
}