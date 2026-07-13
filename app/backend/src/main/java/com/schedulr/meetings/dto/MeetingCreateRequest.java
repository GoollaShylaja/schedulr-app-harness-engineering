package com.schedulr.meetings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MeetingCreateRequest(
    @NotBlank String title,
    @NotNull OffsetDateTime startTime,
    @NotNull OffsetDateTime endTime,
    String meetingTimezone,
    String notes,
    List<UUID> inviteeContactIds) {

  public MeetingCreateRequest {
    if (meetingTimezone == null || meetingTimezone.isBlank()) {
      meetingTimezone = "UTC";
    }
    if (inviteeContactIds == null) {
      inviteeContactIds = List.of();
    }
  }
}
