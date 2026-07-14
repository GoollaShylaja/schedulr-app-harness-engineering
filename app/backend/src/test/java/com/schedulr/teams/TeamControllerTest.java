package com.schedulr.teams;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schedulr.auth.entity.Team;
import com.schedulr.auth.entity.User;
import com.schedulr.auth.repository.TeamRepository;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.common.security.JwtService;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TeamControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtService jwtService;

  private Team team;
  private User admin;
  private User member;
  private String adminToken;
  private String memberToken;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    teamRepository.deleteAll();

    team = new Team();
    team.setId(IdGenerator.newId());
    team.setName("Acme Sales");
    team.setSlug("acme-sales-" + UUID.randomUUID());
    team.setCreatedAt(OffsetDateTime.now());
    teamRepository.save(team);

    admin = createUser("dana@acme.test", "admin");
    member = createUser("lukas@acme.test", "member");
    adminToken = jwtService.issue(admin.getId());
    memberToken = jwtService.issue(member.getId());
  }

  private User createUser(String email, String role) {
    User u = new User();
    u.setId(IdGenerator.newId());
    u.setEmail(email);
    u.setHashedPassword("irrelevant-for-jwt-tests");
    u.setFullName(email);
    u.setTimezone("UTC");
    u.setRole(role);
    u.setTeamId(team.getId());
    u.setCreatedAt(OffsetDateTime.now());
    return userRepository.save(u);
  }

  @Test
  void myTeamReturns200WithMembers() throws Exception {
    mockMvc
        .perform(get("/api/v1/teams/me").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Acme Sales"))
        .andExpect(jsonPath("$.data.members.length()").value(2));
  }

  @Test
  void inviteMemberByAdminReturns201() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/teams/me/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"mei@acme.test\",\"fullName\":\"Mei Tan\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value("mei@acme.test"));
  }

  @Test
  void inviteMemberByNonAdminReturns403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/teams/me/members")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"mei@acme.test\",\"fullName\":\"Mei Tan\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void inviteDuplicateEmailReturns409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/teams/me/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"lukas@acme.test\",\"fullName\":\"Dup\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateMemberRoleByAdminReturns200() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/teams/me/members/" + member.getId() + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"admin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("admin"));
  }

  @Test
  void removeSelfReturns400() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/teams/me/members/" + admin.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeMemberByAdminReturns204() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/teams/me/members/" + member.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNoContent());
  }
}
