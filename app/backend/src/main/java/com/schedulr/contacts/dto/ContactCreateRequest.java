package com.schedulr.contacts.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactCreateRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    String company,
    String phone,
    String title,
    String notes,
    String stage) {

  public ContactCreateRequest {
    if (stage == null || stage.isBlank()) {
      stage = "lead";
    }
  }
}
