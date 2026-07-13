package com.schedulr.auth.service;

import com.schedulr.auth.dto.AuthResponse;
import com.schedulr.auth.dto.LoginRequest;
import com.schedulr.auth.dto.PasswordChangeRequest;
import com.schedulr.auth.dto.ProfileUpdateRequest;
import com.schedulr.auth.dto.UserResponse;
import com.schedulr.auth.entity.User;
import com.schedulr.auth.exception.InvalidCredentialsException;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.common.error.exception.NotFoundException;
import com.schedulr.common.security.AuthenticatedUser;
import com.schedulr.common.security.JwtService;
import com.schedulr.common.timezone.TimezoneConverter;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final TimezoneConverter timezoneConverter;

  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new InvalidCredentialsException("Incorrect email or password"));
    if (!passwordEncoder.matches(request.password(), user.getHashedPassword())) {
      throw new InvalidCredentialsException("Incorrect email or password");
    }
    String token = jwtService.issue(user.getId());
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(jwtService.expirationMinutes());
    return new AuthResponse(token, expiresAt, toResponse(user));
  }

  public UserResponse currentUser(AuthenticatedUser principal) {
    return toResponse(loadUser(principal.id()));
  }

  public UserResponse updateProfile(AuthenticatedUser principal, ProfileUpdateRequest request) {
    User user = loadUser(principal.id());
    if (request.fullName() != null) {
      user.setFullName(request.fullName());
    }
    if (request.timezone() != null) {
      timezoneConverter.validateZone(request.timezone());
      user.setTimezone(request.timezone());
    }
    return toResponse(userRepository.save(user));
  }

  public void changePassword(AuthenticatedUser principal, PasswordChangeRequest request) {
    User user = loadUser(principal.id());
    if (!passwordEncoder.matches(request.currentPassword(), user.getHashedPassword())) {
      throw new InvalidCredentialsException("Current password is incorrect");
    }
    user.setHashedPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
  }

  private User loadUser(UUID id) {
    return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
  }

  private UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getTimezone(),
        user.getRole(),
        user.getTeamId());
  }
}
