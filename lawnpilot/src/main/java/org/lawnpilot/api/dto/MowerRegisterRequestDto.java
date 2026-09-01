package org.lawnpilot.api.dto;

public record MowerRegisterRequestDto(
        String mowerId,
        String model,
        Boolean simulated,
        Double startLatitude,
        Double startLongitude) {
}
