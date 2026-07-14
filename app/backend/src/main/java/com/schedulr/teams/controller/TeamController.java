package com.schedulr.teams.controller;

import com.schedulr.common.api.ApiResponse;
import com.schedulr.common.security.AuthenticatedUser;
import com.schedulr.teams.dto.InviteMemberRequest;
import com.schedulr.teams.dto.TeamMemberResponse;
import com.schedulr.teams.dto.TeamResponse;
import com.schedulr.teams.dto.UpdateMemberRoleRequest;
import com.schedulr.teams.service.TeamService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

  private final TeamService teamService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<TeamResponse>> myTeam(
      @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(ApiResponse.ok(teamService.currentTeam(current.teamId())));
  }

  @PostMapping("/me/members")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<TeamMemberResponse>> inviteMember(
      @Valid @RequestBody InviteMemberRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    TeamMemberResponse response = teamService.inviteMember(current.teamId(), request);
    return ResponseEntity.status(201).body(ApiResponse.ok("Member invited", response));
  }

  @PatchMapping("/me/members/{userId}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<TeamMemberResponse>> updateMemberRole(
      @PathVariable UUID userId,
      @Valid @RequestBody UpdateMemberRoleRequest request,
      @AuthenticationPrincipal AuthenticatedUser current) {
    TeamMemberResponse response = teamService.updateMemberRole(current.teamId(), userId, request);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/me/members/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> removeMember(
      @PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser current) {
    teamService.removeMember(current.teamId(), current.id(), userId);
    return ResponseEntity.noContent().build();
  }
}
