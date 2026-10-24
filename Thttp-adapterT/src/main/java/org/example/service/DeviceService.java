package org.example.service;

import org.example.repository.IotCoreRepository;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class DeviceService {

    private final IotCoreRepository repository;

    public DeviceService(IotCoreRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<HttpResponse<String>> forward(String deviceId, byte[] body) {
        System.out.println("[service] device=" + deviceId + ", bytes=" + body.length);
        return repository.send(deviceId, body);

    }

}
