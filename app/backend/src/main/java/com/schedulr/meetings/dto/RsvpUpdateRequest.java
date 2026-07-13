package com.schedulr.meetings.dto;

import jakarta.validation.constraints.NotBlank;

public record RsvpUpdateRequest(@NotBlank String response) {}
