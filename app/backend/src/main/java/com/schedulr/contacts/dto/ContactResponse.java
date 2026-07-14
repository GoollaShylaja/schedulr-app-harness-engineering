package com.schedulr.contacts.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContactResponse(
    UUID id,
    String name,
    String email,
    String company,
    String phone,
    String title,
    String notes,
    String stage,
    OffsetDateTime createdAt) {}
