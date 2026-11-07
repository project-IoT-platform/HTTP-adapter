package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.codec.Aad;
import org.example.codec.Checksum;
import org.example.crypto.AesGcmCipher;
import org.example.model.Envelope;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
public class DeviceSimulator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        AesGcmCipher cipher = AesGcmCipher.fromEnv();
        String baseUrl = System.getenv().getOrDefault("ADAPTER_URL", "http://localhost:8080");
        String deviceId = "sensor-1";
        byte[] telemetry = "{\"temp\":21.5}".getBytes(StandardCharsets.UTF_8);

        long messageId = System.currentTimeMillis();
        String timestamp = Instant.now().toString();
        String valid = envelope(cipher, deviceId, messageId, timestamp, messageId, telemetry);

        System.out.println("1) корректное сообщение");
        send(cipher, baseUrl, deviceId, valid);

        System.out.println("2) то же сообщение повторно (replay)");
        send(cipher, baseUrl, deviceId, valid);

        System.out.println("3) подмена messageId при том же payload");
        String tampered = envelope(cipher, deviceId, messageId + 1, timestamp, messageId, telemetry);
        send(cipher, baseUrl, deviceId, tampered);

        System.out.println("4) payload не в Base64");
        send(cipher, baseUrl, deviceId,
                MAPPER.writeValueAsString(new Envelope(messageId + 2, Instant.now().toString(), "это не base64!", "00000000")));

        System.out.println("5) битый payload: контрольная сумма не совпадает");
        String ts5 = Instant.now().toString();
        byte[] enc5 = cipher.encrypt(telemetry, Aad.build("req", deviceId, messageId + 3, ts5));
        String goodCrc = Checksum.crc32Hex(enc5);
        enc5[enc5.length - 1] ^= 1;   // портим один бит уже после подсчёта суммы
        send(cipher, baseUrl, deviceId, MAPPER.writeValueAsString(
                new Envelope(messageId + 3, ts5, Base64.getEncoder().encodeToString(enc5), goodCrc)));

        System.out.println("6) обрезанное сообщение (потеряна часть payload)");
        String ts6 = Instant.now().toString();
        byte[] enc6 = cipher.encrypt(telemetry, Aad.build("req", deviceId, messageId + 4, ts6));
        String crc6 = Checksum.crc32Hex(enc6);
        byte[] cut = java.util.Arrays.copyOf(enc6, enc6.length - 5);
        send(cipher, baseUrl, deviceId, MAPPER.writeValueAsString(
                new Envelope(messageId + 4, ts6, Base64.getEncoder().encodeToString(cut), crc6)));

        System.out.println("7) нет поля checksum");
        send(cipher, baseUrl, deviceId,
                "{\"messageId\":" + (messageId + 5) + ",\"timestamp\":\"" + Instant.now() + "\",\"payload\":\"AAAA\"}");
    }


    private static String envelope(AesGcmCipher cipher, String deviceId, long envelopeId,
                                   String timestamp, long signedId, byte[] plain) throws Exception {
        byte[] aad = Aad.build("req", deviceId, signedId, timestamp);
        byte[] encrypted = cipher.encrypt(plain, aad);
        String payload = Base64.getEncoder().encodeToString(encrypted);
        return MAPPER.writeValueAsString(new Envelope(envelopeId, timestamp, payload, Checksum.crc32Hex(encrypted)));
    }

    private static void send(AesGcmCipher cipher, String baseUrl, String deviceId, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/devices/" + deviceId + "/telemetry"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("   status    = " + response.statusCode());
        System.out.println("   body      = " + response.body());
        try {
            Envelope ack = MAPPER.readValue(response.body(), Envelope.class);
            byte[] aad = Aad.build("resp", deviceId, ack.messageId(), ack.timestamp());
            byte[] encryptedAck = Base64.getDecoder().decode(ack.payload());
            if (!Checksum.matches(encryptedAck, ack.checksum())) {
                System.out.println("   !!! контрольная сумма ответа не совпала");
                return;
            }
            byte[] plain = cipher.decrypt(encryptedAck, aad);
            System.out.println("   расшифрованный ответ = " + new String(plain, StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.out.println("   (ответ - обычный текст, не конверт)");
        }
        System.out.println();
    }
}