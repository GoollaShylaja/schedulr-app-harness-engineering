package com.schedulr.teams.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMemberRoleRequest(@NotBlank String role) {}
