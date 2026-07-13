package com.schedulr.auth.controller;

import com.schedulr.auth.dto.AuthResponse;
import com.schedulr.auth.dto.LoginRequest;
import com.schedulr.auth.dto.PasswordChangeRequest;
import com.schedulr.auth.dto.ProfileUpdateRequest;
import com.schedulr.auth.dto.UserResponse;
import com.schedulr.auth.service.AuthService;
import com.schedulr.common.api.ApiResponse;
import com.schedulr.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> me(
      @AuthenticationPrincipal AuthenticatedUser current) {
    return ResponseEntity.ok(ApiResponse.ok(authService.currentUser(current)));
  }

  @PatchMapping("/me/profile")
  public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
      @AuthenticationPrincipal AuthenticatedUser current,
      @Valid @RequestBody ProfileUpdateRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(authService.updateProfile(current, request)));
  }

  @PostMapping("/me/change-password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthenticatedUser current,
      @Valid @RequestBody PasswordChangeRequest request) {
    authService.changePassword(current, request);
    return ResponseEntity.noContent().build();
  }
}
