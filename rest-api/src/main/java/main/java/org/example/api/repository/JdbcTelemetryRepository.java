package main.java.org.example.api.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import main.java.org.example.api.model.TelemetryRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!memory")
public class JdbcTelemetryRepository implements TelemetryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcTelemetryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<TelemetryRecord> find(String deviceId, Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT message_id, received_at, payload::text AS payload FROM telemetry WHERE device_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(deviceId);
        if (from != null) {
            sql.append(" AND received_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND received_at <= ?");
            args.add(Timestamp.from(to));
        }
        sql.append(" ORDER BY received_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), rowMapper(), args.toArray());
    }

    @Override
    public Optional<TelemetryRecord> findLatest(String deviceId) {
        return find(deviceId, null, null, 1).stream().findFirst();
    }

    private RowMapper<TelemetryRecord> rowMapper() {
        return (rs, i) -> {
            try {
                return new TelemetryRecord(rs.getLong("message_id"),
                        rs.getTimestamp("received_at").toInstant(),
                        mapper.readTree(rs.getString("payload")));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("telemetry.payload is not valid JSON", e);
            }
        };
    }
}
