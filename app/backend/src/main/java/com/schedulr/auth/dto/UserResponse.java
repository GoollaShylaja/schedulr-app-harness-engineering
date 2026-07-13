package com.schedulr.auth.dto;

import java.util.UUID;

public record UserResponse(
    UUID id, String email, String fullName, String timezone, String role, UUID teamId) {}
