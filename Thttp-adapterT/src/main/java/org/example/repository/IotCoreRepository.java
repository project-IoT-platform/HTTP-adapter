package org.example.repository;

import org.example.сonfig.AdapterConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class IotCoreRepository {

    private static final HttpClient CORE_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public CompletableFuture<HttpResponse<String>> send(String deviceId, byte[] body) {
        HttpRequest coreReq = HttpRequest.newBuilder(URI.create(AdapterConfig.CORE_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("X-Device-Id", deviceId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return CORE_CLIENT.sendAsync(coreReq, HttpResponse.BodyHandlers.ofString());
    }
}
