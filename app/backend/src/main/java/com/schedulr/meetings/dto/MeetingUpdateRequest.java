package com.schedulr.meetings.dto;

import java.time.OffsetDateTime;

public record MeetingUpdateRequest(
    String title,
    String notes,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    String meetingTimezone,
    String status) {}
