package com.schedulr.availability.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AvailabilitySlotCreateRequest(
    @NotNull @Min(0) @Max(6) Integer weekday,
    @NotNull @Pattern(regexp = "\\d{2}:\\d{2}") String start,
    @NotNull @Pattern(regexp = "\\d{2}:\\d{2}") String end) {}
