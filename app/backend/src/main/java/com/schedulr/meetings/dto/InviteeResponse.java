package com.schedulr.meetings.dto;

import java.util.UUID;

public record InviteeResponse(
    UUID id, UUID contactId, String contactName, String contactEmail, String response) {}
