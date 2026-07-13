package com.schedulr.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schedulr.common.error.exception.UnauthenticatedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final JwtService jwtService = new JwtService("test-secret-value", 60);

  @Test
  void issueAndParseRoundTrips() {
    UUID userId = UUID.randomUUID();
    String token = jwtService.issue(userId);

    assertThat(jwtService.parseUserId(token)).isEqualTo(userId);
  }

  @Test
  void tamperedTokenIsRejected() {
    UUID userId = UUID.randomUUID();
    String token = jwtService.issue(userId);
    String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

    assertThatThrownBy(() -> jwtService.parseUserId(tampered))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  void expiredTokenIsRejected() {
    JwtService shortLived = new JwtService("test-secret-value", -1);
    UUID userId = UUID.randomUUID();
    String token = shortLived.issue(userId);

    assertThatThrownBy(() -> shortLived.parseUserId(token))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  void garbageTokenIsRejected() {
    assertThatThrownBy(() -> jwtService.parseUserId("not-a-jwt"))
        .isInstanceOf(UnauthenticatedException.class);
  }
}
