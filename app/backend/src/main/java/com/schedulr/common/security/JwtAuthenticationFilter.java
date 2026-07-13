package com.schedulr.common.security;

import com.schedulr.auth.entity.User;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.common.error.exception.UnauthenticatedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        UUID userId = jwtService.parseUserId(header.substring(7));
        userRepository.findById(userId).ifPresent(this::authenticate);
      } catch (UnauthenticatedException ignored) {
        // Leave the security context unauthenticated; the entry point handles the 401.
      }
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(User user) {
    AuthenticatedUser principal =
        new AuthenticatedUser(
            user.getId(), user.getTeamId(), user.getRole(), user.getTimezone(), user.getEmail());
    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()));
    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
