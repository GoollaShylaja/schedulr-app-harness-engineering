package com.schedulr.availability.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AvailabilityBulkSetRequest(
    @NotNull List<@Valid AvailabilitySlotCreateRequest> slots) {}
