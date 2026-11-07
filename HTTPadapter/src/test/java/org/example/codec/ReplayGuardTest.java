package org.example.codec;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ReplayGuardTest {

    @Test
    void firstMessageAcceptedDuplicateRejected() {
        ReplayGuard guard = new ReplayGuard(Duration.ofMinutes(10));
        assertTrue(guard.register("dev-1", 1));
        assertFalse(guard.register("dev-1", 1));
    }

    @Test
    void sameIdFromAnotherDeviceIsNotDuplicate() {
        ReplayGuard guard = new ReplayGuard(Duration.ofMinutes(10));
        assertTrue(guard.register("dev-1", 1));
        assertTrue(guard.register("dev-2", 1));
    }

    @Test
    void differentIdsAreIndependent() {
        ReplayGuard guard = new ReplayGuard(Duration.ofMinutes(10));
        assertTrue(guard.register("dev-1", 1));
        assertTrue(guard.register("dev-1", 2));
    }

    @Test
    void expiredEntryIsAcceptedAgain() throws InterruptedException {
        ReplayGuard guard = new ReplayGuard(Duration.ofMillis(30));
        assertTrue(guard.register("dev-1", 1));
        Thread.sleep(80);
        assertTrue(guard.register("dev-1", 1));
    }
}
