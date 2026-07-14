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
    // Flip a character in the header segment (index 10), not the very last character of the
    // signature: the trailing base64url group of an HS256 (32-byte) signature has unused low
    // bits, so some single-character edits there decode to the same bytes and don't actually
    // tamper the signature — making the assertion flaky. A header-segment edit always changes
    // the signed content, so the signature always fails to verify.
    char[] chars = token.toCharArray();
    int index = 10;
    chars[index] = chars[index] == 'a' ? 'b' : 'a';
    String tampered = new String(chars);

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
