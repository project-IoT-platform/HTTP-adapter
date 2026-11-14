package main.java.org.example.api.repository;

import main.java.org.example.api.model.Device;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!memory")
public class JdbcDeviceRepository implements DeviceRepository {

    private final JdbcTemplate jdbc;

    public JdbcDeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Device> findAll(int limit, int offset) {
        return jdbc.query(
                "SELECT device_id, created_at, last_seen_at FROM devices ORDER BY device_id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Timestamp lastSeen = rs.getTimestamp("last_seen_at");
                    return new Device(rs.getString("device_id"),
                            rs.getTimestamp("created_at").toInstant(),
                            lastSeen == null ? null : lastSeen.toInstant());
                },
                limit, offset);
    }

    @Override
    public Optional<Device> findById(String deviceId) {
        return jdbc.query(
                "SELECT device_id, created_at, last_seen_at FROM devices WHERE device_id = ?",
                (rs, i) -> {
                    Timestamp lastSeen = rs.getTimestamp("last_seen_at");
                    return new Device(rs.getString("device_id"),
                            rs.getTimestamp("created_at").toInstant(),
                            lastSeen == null ? null : lastSeen.toInstant());
                },
                deviceId).stream().findFirst();
    }
}
