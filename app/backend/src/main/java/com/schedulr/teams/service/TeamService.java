package com.schedulr.teams.service;

import com.schedulr.auth.entity.Team;
import com.schedulr.auth.entity.User;
import com.schedulr.auth.repository.TeamRepository;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.common.error.exception.ConflictException;
import com.schedulr.common.error.exception.InvalidRequestException;
import com.schedulr.common.error.exception.NotFoundException;
import com.schedulr.common.util.IdGenerator;
import com.schedulr.teams.dto.InviteMemberRequest;
import com.schedulr.teams.dto.TeamMemberResponse;
import com.schedulr.teams.dto.TeamResponse;
import com.schedulr.teams.dto.UpdateMemberRoleRequest;
import com.schedulr.teams.exception.MemberNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TeamService {

  private static final Set<String> VALID_ROLES = Set.of("admin", "member");
  private static final String TEMP_PASSWORD = "changeme123";

  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public TeamResponse currentTeam(UUID teamId) {
    Team team =
        teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("Team not found"));
    List<TeamMemberResponse> members =
        userRepository.findAllByTeamIdOrderByFullName(teamId).stream()
            .map(this::toMemberResponse)
            .toList();
    return new TeamResponse(team.getId(), team.getName(), team.getSlug(), members);
  }

  public TeamMemberResponse inviteMember(UUID teamId, InviteMemberRequest request) {
    if (userRepository.findByEmail(request.email()).isPresent()) {
      throw new ConflictException("Email already in use");
    }
    User user = new User();
    user.setId(IdGenerator.newId());
    user.setEmail(request.email());
    user.setFullName(request.fullName());
    user.setRole(request.role());
    user.setTimezone(request.timezone());
    user.setTeamId(teamId);
    user.setHashedPassword(passwordEncoder.encode(TEMP_PASSWORD));
    user.setCreatedAt(OffsetDateTime.now());
    return toMemberResponse(userRepository.save(user));
  }

  public TeamMemberResponse updateMemberRole(
      UUID teamId, UUID userId, UpdateMemberRoleRequest request) {
    if (!VALID_ROLES.contains(request.role())) {
      throw new InvalidRequestException("Role must be one of " + VALID_ROLES);
    }
    User user = loadMember(teamId, userId);
    user.setRole(request.role());
    return toMemberResponse(userRepository.save(user));
  }

  public void removeMember(UUID teamId, UUID currentUserId, UUID userId) {
    if (userId.equals(currentUserId)) {
      throw new InvalidRequestException("Cannot remove yourself");
    }
    userRepository.delete(loadMember(teamId, userId));
  }

  private User loadMember(UUID teamId, UUID userId) {
    return userRepository
        .findByIdAndTeamId(userId, teamId)
        .orElseThrow(MemberNotFoundException::new);
  }

  private TeamMemberResponse toMemberResponse(User user) {
    return new TeamMemberResponse(
        user.getId(), user.getFullName(), user.getEmail(), user.getTimezone(), user.getRole());
  }
}
