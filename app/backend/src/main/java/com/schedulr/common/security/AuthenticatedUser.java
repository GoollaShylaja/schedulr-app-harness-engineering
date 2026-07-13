package com.schedulr.common.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, UUID teamId, String role, String timezone, String email) {}
