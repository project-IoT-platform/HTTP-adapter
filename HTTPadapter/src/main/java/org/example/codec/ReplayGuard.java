package org.example.codec;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class ReplayGuard {

    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private volatile long lastPurge = System.currentTimeMillis();

    public ReplayGuard(Duration ttl) {
        this.ttlMillis = ttl.toMillis();
    }
    public boolean register(String deviceId, long messageId) {
        long now = System.currentTimeMillis();
        purgeIfDue(now);

        String key = deviceId + ":" + messageId;
        long expiresAt = now + ttlMillis;

        Long previous = seen.putIfAbsent(key, expiresAt);
        if (previous == null) {
            return true;
        }
        if (previous < now) {          // запись уже просрочена - считаем сообщение новым
            seen.put(key, expiresAt);
            return true;
        }
        return false;
    }

    private void purgeIfDue(long now) {
        if (now - lastPurge < 60_000) {
            return;
        }
        lastPurge = now;
        seen.values().removeIf(expiresAt -> expiresAt < now);
    }
}
