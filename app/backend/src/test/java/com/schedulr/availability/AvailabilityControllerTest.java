package com.schedulr.availability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schedulr.auth.entity.Team;
import com.schedulr.auth.entity.User;
import com.schedulr.auth.repository.TeamRepository;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.availability.repository.AvailabilitySlotRepository;
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
class AvailabilityControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AvailabilitySlotRepository slotRepository;
  @Autowired private JwtService jwtService;

  private User user;
  private String token;
  private String otherTeamToken;

  @BeforeEach
  void setUp() {
    slotRepository.deleteAll();
    userRepository.deleteAll();
    teamRepository.deleteAll();

    Team team = createTeam("Acme Sales");
    user = createUser(team.getId(), "dana@acme.test");
    Team otherTeam = createTeam("Other Co");
    User outsider = createUser(otherTeam.getId(), "outsider@other.test");

    token = jwtService.issue(user.getId());
    otherTeamToken = jwtService.issue(outsider.getId());
  }

  private Team createTeam(String name) {
    Team t = new Team();
    t.setId(IdGenerator.newId());
    t.setName(name);
    t.setSlug(name.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID());
    t.setCreatedAt(OffsetDateTime.now());
    return teamRepository.save(t);
  }

  private User createUser(UUID teamId, String email) {
    User u = new User();
    u.setId(IdGenerator.newId());
    u.setEmail(email);
    u.setHashedPassword("irrelevant-for-jwt-tests");
    u.setFullName(email);
    u.setTimezone("UTC");
    u.setRole("member");
    u.setTeamId(teamId);
    u.setCreatedAt(OffsetDateTime.now());
    return userRepository.save(u);
  }

  @Test
  void addSlotReturns201() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weekday\":1,\"start\":\"09:00\",\"end\":\"17:00\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.weekday").value(1));
  }

  @Test
  void addSlotWithInvalidWeekdayReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weekday\":7,\"start\":\"09:00\",\"end\":\"17:00\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bulkSetReplacesSlots() throws Exception {
    mockMvc.perform(
        post("/api/v1/availability")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"weekday\":0,\"start\":\"09:00\",\"end\":\"17:00\"}"));

    mockMvc
        .perform(
            put("/api/v1/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slots\":[{\"weekday\":2,\"start\":\"10:00\",\"end\":\"18:00\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].weekday").value(2));
  }

  @Test
  void getUserAvailabilityFromOtherTeamReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/availability/user/" + user.getId())
                .header("Authorization", "Bearer " + otherTeamToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteSlotReturns204() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/availability")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"weekday\":3,\"start\":\"09:00\",\"end\":\"17:00\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");

    mockMvc
        .perform(delete("/api/v1/availability/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void getAvailabilityWithoutTokenReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/availability")).andExpect(status().isUnauthorized());
  }
}
