package com.schedulr.meetings;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.schedulr.contacts.entity.Contact;
import com.schedulr.contacts.repository.ContactRepository;
import com.schedulr.meetings.entity.MeetingInvitee;
import com.schedulr.meetings.repository.MeetingInviteeRepository;
import com.schedulr.meetings.repository.MeetingRepository;
import com.schedulr.support.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.util.List;
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
class MeetingControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ContactRepository contactRepository;
  @Autowired private MeetingRepository meetingRepository;
  @Autowired private MeetingInviteeRepository meetingInviteeRepository;
  @Autowired private JwtService jwtService;

  private Team team;
  private User host;
  private User member;
  private Contact contact;
  private String hostToken;
  private String memberToken;
  private String otherTeamToken;

  @BeforeEach
  void setUp() {
    meetingInviteeRepository.deleteAll();
    meetingRepository.deleteAll();
    contactRepository.deleteAll();
    userRepository.deleteAll();
    teamRepository.deleteAll();

    team = createTeam("Acme Sales");
    host = createUser(team.getId(), "dana@acme.test", "admin");
    member = createUser(team.getId(), "lukas@acme.test", "member");
    Team otherTeam = createTeam("Other Co");
    User outsider = createUser(otherTeam.getId(), "outsider@other.test", "admin");

    contact = createContact(team.getId(), "Priya Shah", "priya@globex.test");

    hostToken = jwtService.issue(host.getId());
    memberToken = jwtService.issue(member.getId());
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

  private User createUser(UUID teamId, String email, String role) {
    User u = new User();
    u.setId(IdGenerator.newId());
    u.setEmail(email);
    u.setHashedPassword("irrelevant-for-jwt-tests");
    u.setFullName(email);
    u.setTimezone("America/Chicago");
    u.setRole(role);
    u.setTeamId(teamId);
    u.setCreatedAt(OffsetDateTime.now());
    return userRepository.save(u);
  }

  private Contact createContact(UUID teamId, String name, String email) {
    Contact c = new Contact();
    c.setId(IdGenerator.newId());
    c.setTeamId(teamId);
    c.setName(name);
    c.setEmail(email);
    c.setCreatedAt(OffsetDateTime.now());
    return contactRepository.save(c);
  }

  private String createMeetingBody(String title, String start, String end, List<UUID> invitees) {
    String inviteeJson =
        invitees.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).orElse("");
    return "{\"title\":\""
        + title
        + "\",\"startTime\":\""
        + start
        + "\",\"endTime\":\""
        + end
        + "\",\"meetingTimezone\":\"America/Chicago\",\"inviteeContactIds\":["
        + inviteeJson
        + "]}";
  }

  @Test
  void createMeetingReturns201() throws Exception {
    String body =
        createMeetingBody(
            "Discovery call",
            "2026-07-20T14:00:00Z",
            "2026-07-20T14:30:00Z",
            List.of(contact.getId()));

    mockMvc
        .perform(
            post("/api/v1/meetings")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.title").value("Discovery call"))
        .andExpect(jsonPath("$.data.inviteeCount").value(1))
        .andExpect(jsonPath("$.data.host").value(host.getEmail()));
  }

  @Test
  void createMeetingWithEndBeforeStartReturns400() throws Exception {
    String body =
        createMeetingBody(
            "Bad schedule", "2026-07-20T14:30:00Z", "2026-07-20T14:00:00Z", List.of());

    mockMvc
        .perform(
            post("/api/v1/meetings")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void createMeetingWithoutTokenReturns401() throws Exception {
    String body =
        createMeetingBody("No auth", "2026-07-20T14:00:00Z", "2026-07-20T14:30:00Z", List.of());

    mockMvc
        .perform(post("/api/v1/meetings").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getMeetingFromOtherTeamReturns404() throws Exception {
    UUID meetingId = createMeetingViaApi();

    mockMvc
        .perform(
            get("/api/v1/meetings/" + meetingId)
                .header("Authorization", "Bearer " + otherTeamToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void getUnknownMeetingReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/meetings/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + hostToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateMeetingByNonHostNonAdminReturns403() throws Exception {
    UUID meetingId = createMeetingViaApi();

    mockMvc
        .perform(
            patch("/api/v1/meetings/" + meetingId)
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hijacked\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateMeetingByHostReturns200() throws Exception {
    UUID meetingId = createMeetingViaApi();

    mockMvc
        .perform(
            patch("/api/v1/meetings/" + meetingId)
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("Renamed"));
  }

  @Test
  void cancelMeetingReturns204() throws Exception {
    UUID meetingId = createMeetingViaApi();

    mockMvc
        .perform(
            delete("/api/v1/meetings/" + meetingId).header("Authorization", "Bearer " + hostToken))
        .andExpect(status().isNoContent());
  }

  @Test
  void rsvpUpdateReturns200() throws Exception {
    UUID meetingId = createMeetingViaApi();
    List<MeetingInvitee> invitees = meetingInviteeRepository.findByMeetingId(meetingId);
    assertThat(invitees).hasSize(1);
    UUID inviteeId = invitees.get(0).getId();

    mockMvc
        .perform(
            patch("/api/v1/meetings/" + meetingId + "/invitees/" + inviteeId + "/rsvp")
                .header("Authorization", "Bearer " + hostToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"response\":\"accepted\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.response").value("accepted"));
  }

  @Test
  void paginationSlicesResults() throws Exception {
    for (int i = 0; i < 3; i++) {
      createMeetingBodyAndPost("Meeting " + i, 14 + i);
    }

    mockMvc
        .perform(
            get("/api/v1/meetings?page=0&size=2").header("Authorization", "Bearer " + hostToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(2));
  }

  private void createMeetingBodyAndPost(String title, int hour) throws Exception {
    String body =
        createMeetingBody(
            title,
            "2026-07-2" + (hour % 9) + "T" + String.format("%02d", hour % 24) + ":00:00Z",
            "2026-07-2" + (hour % 9) + "T" + String.format("%02d", hour % 24) + ":30:00Z",
            List.of());
    mockMvc.perform(
        post("/api/v1/meetings")
            .header("Authorization", "Bearer " + hostToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private UUID createMeetingViaApi() throws Exception {
    String body =
        createMeetingBody(
            "Discovery call",
            "2026-07-20T14:00:00Z",
            "2026-07-20T14:30:00Z",
            List.of(contact.getId()));
    String response =
        mockMvc
            .perform(
                post("/api/v1/meetings")
                    .header("Authorization", "Bearer " + hostToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
    return UUID.fromString(id);
  }
}
