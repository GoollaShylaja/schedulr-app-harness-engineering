package com.schedulr.meetings.controller;

import com.schedulr.common.api.ApiResponse;
import com.schedulr.common.api.PageResponse;
import com.schedulr.common.security.AuthenticatedUser;
import com.schedulr.export.service.ExportService;
import com.schedulr.meetings.dto.InviteeResponse;
import com.schedulr.meetings.dto.MeetingCreateRequest;
import com.schedulr.meetings.dto.MeetingResponse;
import com.schedulr.meetings.dto.MeetingUpdateRequest;
import com.schedulr.meetings.dto.RsvpUpdateRequest;
import com.schedulr.meetings.service.MeetingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

  private final MeetingService meetingService;
  private final ExportService exportService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<MeetingResponse>>> listMeetings(
      @RequestParam(required = false) UUID hostId,
      @RequestParam(required = false) OffsetDateTime startAfter,
      @RequestParam(required = false) OffsetDateTime startBefore,
      @RequestParam(required = false) UUID contactId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      @PageableDefault(size = 50) Pageable pageable,
      @AuthenticationPrincipal AuthenticatedUser current) {
    Page<MeetingResponse> page =
        meetingService.list(
            current.teamId(),
            hostId,
            startAfter,
            startBefore,
            contactId,
            status,
            search,
            current.timezone(),
            pageable);
    return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<MeetingResponse>> createMeeting(
      @Valid @RequestBody MeetingCreateRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    MeetingResponse response =
        meetingService.create(current.teamId(), current.id(), request, current.timezone());
    return ResponseEntity.created(URI.create("/api/v1/meetings/" + response.id()))
        .body(ApiResponse.ok("Meeting created", response));
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> exportMeetings(
      @RequestParam(defaultValue = "pdf") String format,
      @AuthenticationPrincipal AuthenticatedUser current) {
    List<MeetingResponse> meetings =
        meetingService
            .list(
                current.teamId(),
                null,
                null,
                null,
                null,
                null,
                null,
                current.timezone(),
                Pageable.unpaged())
            .getContent();
    byte[] body = exportService.render(format, meetings, current.timezone());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(exportService.contentType(format)))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=meetings." + exportService.fileExtension(format))
        .body(body);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<MeetingResponse>> getMeeting(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(
        ApiResponse.ok(meetingService.get(id, current.teamId(), current.timezone())));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<MeetingResponse>> updateMeeting(
      @PathVariable UUID id,
      @Valid @RequestBody MeetingUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    MeetingResponse response =
        meetingService.update(id, current.teamId(), current, request, current.timezone());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancelMeeting(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser current) {
    meetingService.cancel(id, current.teamId(), current);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/invitees/{inviteeId}/rsvp")
  public ResponseEntity<ApiResponse<InviteeResponse>> updateRsvp(
      @PathVariable UUID id,
      @PathVariable UUID inviteeId,
      @Valid @RequestBody RsvpUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    InviteeResponse response = meetingService.updateRsvp(id, inviteeId, current.teamId(), request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
