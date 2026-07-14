package com.schedulr.teams.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteMemberRequest(
    @NotBlank @Email String email, @NotBlank String fullName, String role, String timezone) {

  public InviteMemberRequest {
    if (role == null || role.isBlank()) {
      role = "member";
    }
    if (timezone == null || timezone.isBlank()) {
      timezone = "UTC";
    }
  }
}
