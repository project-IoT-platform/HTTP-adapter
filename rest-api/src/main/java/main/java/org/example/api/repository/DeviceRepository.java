package main.java.org.example.api.repository;

import main.java.org.example.api.model.*;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository {

    List<Device> findAll(int limit, int offset);

    Optional<Device> findById(String deviceId);
}
