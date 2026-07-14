package com.schedulr.contacts.controller;

import com.schedulr.common.api.ApiResponse;
import com.schedulr.common.api.PageResponse;
import com.schedulr.common.security.AuthenticatedUser;
import com.schedulr.contacts.dto.ContactCreateRequest;
import com.schedulr.contacts.dto.ContactResponse;
import com.schedulr.contacts.dto.ContactUpdateRequest;
import com.schedulr.contacts.service.ContactService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

  private final ContactService contactService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> listContacts(
      @PageableDefault(size = 50, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedUser current) {
    Page<ContactResponse> page = contactService.list(current.teamId(), pageable);
    return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ContactResponse>> createContact(
      @Valid @RequestBody ContactCreateRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    ContactResponse response = contactService.create(current.teamId(), request);
    return ResponseEntity.created(URI.create("/api/v1/contacts/" + response.id()))
        .body(ApiResponse.ok("Contact created", response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ContactResponse>> getContact(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(ApiResponse.ok(contactService.get(id, current.teamId())));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<ContactResponse>> updateContact(
      @PathVariable UUID id,
      @Valid @RequestBody ContactUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(ApiResponse.ok(contactService.update(id, current.teamId(), request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteContact(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser current) {
    contactService.delete(id, current.teamId());
    return ResponseEntity.noContent().build();
  }
}
