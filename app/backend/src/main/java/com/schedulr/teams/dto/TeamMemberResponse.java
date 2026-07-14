package com.schedulr.teams.dto;

import java.util.UUID;

public record TeamMemberResponse(
    UUID id, String name, String email, String timezone, String role) {}
