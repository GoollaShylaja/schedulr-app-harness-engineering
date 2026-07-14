package com.schedulr.contacts.dto;

public record ContactUpdateRequest(
    String name,
    String email,
    String company,
    String phone,
    String title,
    String notes,
    String stage) {}
