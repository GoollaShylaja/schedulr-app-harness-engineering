package com.schedulr.auth.dto;

import java.time.OffsetDateTime;

public record AuthResponse(String token, OffsetDateTime expiresAt, UserResponse user) {}
