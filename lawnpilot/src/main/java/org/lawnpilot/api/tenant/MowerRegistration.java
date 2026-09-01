package org.lawnpilot.api.tenant;

import java.time.Instant;

record MowerRegistration(
        String mowerId,
        String model,
        Instant registeredAt,
        boolean simulated,
        Double startLatitude,
        Double startLongitude) {
}
