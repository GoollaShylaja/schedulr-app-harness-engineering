package com.schedulr.contacts;

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
import com.schedulr.contacts.repository.ContactRepository;
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
class ContactControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ContactRepository contactRepository;
  @Autowired private JwtService jwtService;

  private Team team;
  private String token;
  private String otherTeamToken;

  @BeforeEach
  void setUp() {
    contactRepository.deleteAll();
    userRepository.deleteAll();
    teamRepository.deleteAll();

    team = createTeam("Acme Sales");
    User user = createUser(team.getId(), "dana@acme.test");
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
    u.setRole("admin");
    u.setTeamId(teamId);
    u.setCreatedAt(OffsetDateTime.now());
    return userRepository.save(u);
  }

  @Test
  void createContactReturns201() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Priya Shah\",\"email\":\"priya@globex.test\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("Priya Shah"))
        .andExpect(jsonPath("$.data.stage").value("lead"));
  }

  @Test
  void createContactWithoutTokenReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Priya Shah\",\"email\":\"priya@globex.test\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createContactWithBlankNameReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"priya@globex.test\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getContactFromOtherTeamReturns404() throws Exception {
    UUID id = createContactViaApi();

    mockMvc
        .perform(get("/api/v1/contacts/" + id).header("Authorization", "Bearer " + otherTeamToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateContactReturns200() throws Exception {
    UUID id = createContactViaApi();

    mockMvc
        .perform(
            patch("/api/v1/contacts/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"opportunity\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stage").value("opportunity"));
  }

  @Test
  void deleteContactReturns204() throws Exception {
    UUID id = createContactViaApi();

    mockMvc
        .perform(delete("/api/v1/contacts/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void listContactsIsPaginated() throws Exception {
    createContactViaApi();
    createContactViaApi();

    mockMvc
        .perform(get("/api/v1/contacts?page=0&size=1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  private UUID createContactViaApi() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/contacts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Contact "
                            + UUID.randomUUID()
                            + "\",\"email\":\"c@test.com\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
    return UUID.fromString(id);
  }
}
