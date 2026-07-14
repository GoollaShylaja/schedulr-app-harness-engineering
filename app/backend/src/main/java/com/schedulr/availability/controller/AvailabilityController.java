package com.schedulr.availability.controller;

import com.schedulr.availability.dto.AvailabilityBulkSetRequest;
import com.schedulr.availability.dto.AvailabilitySlotCreateRequest;
import com.schedulr.availability.dto.AvailabilitySlotResponse;
import com.schedulr.availability.service.AvailabilityService;
import com.schedulr.common.api.ApiResponse;
import com.schedulr.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilityController {

  private final AvailabilityService availabilityService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AvailabilitySlotResponse>>> getMyAvailability(
      @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(ApiResponse.ok(availabilityService.listOwn(current.id())));
  }

  @GetMapping("/user/{userId}")
  public ResponseEntity<ApiResponse<List<AvailabilitySlotResponse>>> getUserAvailability(
      @PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(
        ApiResponse.ok(availabilityService.listForUser(userId, current.teamId())));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AvailabilitySlotResponse>> addSlot(
      @Valid @RequestBody AvailabilitySlotCreateRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    AvailabilitySlotResponse response = availabilityService.add(current.id(), request);
    return ResponseEntity.status(201).body(ApiResponse.ok("Slot added", response));
  }

  @PutMapping
  public ResponseEntity<ApiResponse<List<AvailabilitySlotResponse>>> setAvailability(
      @Valid @RequestBody AvailabilityBulkSetRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(ApiResponse.ok(availabilityService.bulkSet(current.id(), request)));
  }

  @DeleteMapping("/{slotId}")
  public ResponseEntity<Void> deleteSlot(
      @PathVariable UUID slotId, @AuthenticationPrincipal AuthenticatedUser current) {
    availabilityService.delete(current.id(), slotId);
    return ResponseEntity.noContent().build();
  }
}
