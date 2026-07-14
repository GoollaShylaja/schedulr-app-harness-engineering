package com.schedulr.availability.dto;

import java.util.UUID;

public record AvailabilitySlotResponse(
    UUID id, UUID userId, int weekday, String start, String end) {}
