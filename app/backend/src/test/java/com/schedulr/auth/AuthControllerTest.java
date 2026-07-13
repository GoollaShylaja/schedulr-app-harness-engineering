package com.schedulr.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.schedulr.auth.entity.Team;
import com.schedulr.auth.entity.User;
import com.schedulr.auth.repository.TeamRepository;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.common.util.IdGenerator;
import com.schedulr.support.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    teamRepository.deleteAll();

    Team team = new Team();
    team.setId(IdGenerator.newId());
    team.setName("Acme Sales");
    team.setSlug("acme-sales-" + UUID.randomUUID());
    team.setCreatedAt(OffsetDateTime.now());
    teamRepository.save(team);

    User user = new User();
    user.setId(IdGenerator.newId());
    user.setEmail("dana@acme.test");
    user.setHashedPassword(passwordEncoder.encode("password123"));
    user.setFullName("Dana Ortiz");
    user.setTimezone("America/Chicago");
    user.setRole("admin");
    user.setTeamId(team.getId());
    user.setCreatedAt(OffsetDateTime.now());
    userRepository.save(user);
  }

  @Test
  void loginReturns200WithTokenAndUser() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@acme.test\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.token").isNotEmpty())
        .andExpect(jsonPath("$.data.user.email").value("dana@acme.test"))
        .andExpect(jsonPath("$.data.user.hashedPassword").doesNotExist());
  }

  @Test
  void loginWithBadPasswordReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dana@acme.test\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void loginWithUnknownEmailReturns401NotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@acme.test\",\"password\":\"password123\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginWithMalformedEmailReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"password123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void meWithoutTokenReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meWithValidTokenReturns200WithoutPasswordHash() throws Exception {
    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"dana@acme.test\",\"password\":\"password123\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = JsonPath.read(loginBody, "$.data.token");

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("dana@acme.test"))
        .andExpect(jsonPath("$.data.fullName").value("Dana Ortiz"))
        .andExpect(jsonPath("$.data.hashedPassword").doesNotExist())
        .andExpect(jsonPath("$.data.password").doesNotExist());
  }
}
