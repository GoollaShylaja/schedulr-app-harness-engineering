package com.schedulr.meetings.dto;

import java.util.List;
import java.util.UUID;

public record MeetingResponse(
    UUID id,
    String title,
    String host,
    UUID hostId,
    String start,
    String end,
    String timezone,
    String status,
    String notes,
    int inviteeCount,
    List<InviteeResponse> invitees) {}
