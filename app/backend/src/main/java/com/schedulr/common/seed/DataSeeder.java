package com.schedulr.common.seed;

import com.schedulr.auth.entity.Team;
import com.schedulr.auth.entity.User;
import com.schedulr.auth.repository.TeamRepository;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.common.util.IdGenerator;
import com.schedulr.contacts.entity.Contact;
import com.schedulr.contacts.repository.ContactRepository;
import com.schedulr.meetings.entity.Meeting;
import com.schedulr.meetings.entity.MeetingInvitee;
import com.schedulr.meetings.repository.MeetingInviteeRepository;
import com.schedulr.meetings.repository.MeetingRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("seed")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final ContactRepository contactRepository;
  private final MeetingRepository meetingRepository;
  private final MeetingInviteeRepository meetingInviteeRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(String... args) {
    if (teamRepository.count() > 0) {
      System.out.println("Seed data already present — skipping.");
      return;
    }

    Team team = new Team();
    team.setId(IdGenerator.newId());
    team.setName("Acme Sales");
    team.setSlug("acme-sales");
    team.setCreatedAt(OffsetDateTime.now());
    teamRepository.save(team);

    List<User> users =
        List.of(
            newUser(team.getId(), "dana@acme.test", "Dana Ortiz", "America/Chicago", "admin"),
            newUser(team.getId(), "lukas@acme.test", "Lukas Berg", "Europe/Berlin", "member"),
            newUser(team.getId(), "mei@acme.test", "Mei Tan", "Asia/Singapore", "member"));
    userRepository.saveAll(users);

    List<Contact> contacts =
        List.of(
            newContact(team.getId(), "Priya Shah", "priya@globex.test", "Globex", "opportunity"),
            newContact(team.getId(), "Tom Reeves", "tom@initech.test", "Initech", "lead"),
            newContact(team.getId(), "Sofia Marin", "sofia@umbrella.test", "Umbrella", "customer"));
    contactRepository.saveAll(contacts);

    OffsetDateTime base = OffsetDateTime.of(2026, 7, 20, 14, 0, 0, 0, ZoneOffset.UTC);
    for (int i = 0; i < 12; i++) {
      User host = users.get(i % users.size());
      OffsetDateTime start = base.plusDays(i).plusHours(i % 3);

      Meeting meeting = new Meeting();
      meeting.setId(IdGenerator.newId());
      meeting.setTeamId(team.getId());
      meeting.setHostId(host.getId());
      meeting.setTitle("Discovery call #" + (i + 1));
      meeting.setStartTime(start);
      meeting.setEndTime(start.plusMinutes(30));
      meeting.setMeetingTimezone(host.getTimezone());
      meeting.setStatus(i % 4 == 0 ? "completed" : "scheduled");
      meeting.setCreatedAt(OffsetDateTime.now());
      meetingRepository.save(meeting);

      MeetingInvitee invitee = new MeetingInvitee();
      invitee.setId(IdGenerator.newId());
      invitee.setMeetingId(meeting.getId());
      invitee.setContactId(contacts.get(i % contacts.size()).getId());
      meetingInviteeRepository.save(invitee);
    }

    System.out.println(
        "Seeded team="
            + team.getName()
            + " users="
            + users.size()
            + " contacts="
            + contacts.size()
            + " meetings=12");
  }

  private User newUser(
      java.util.UUID teamId, String email, String fullName, String timezone, String role) {
    User user = new User();
    user.setId(IdGenerator.newId());
    user.setEmail(email);
    user.setHashedPassword(passwordEncoder.encode("password123"));
    user.setFullName(fullName);
    user.setTimezone(timezone);
    user.setRole(role);
    user.setTeamId(teamId);
    user.setCreatedAt(OffsetDateTime.now());
    return user;
  }

  private Contact newContact(
      java.util.UUID teamId, String name, String email, String company, String stage) {
    Contact contact = new Contact();
    contact.setId(IdGenerator.newId());
    contact.setTeamId(teamId);
    contact.setName(name);
    contact.setEmail(email);
    contact.setCompany(company);
    contact.setStage(stage);
    contact.setCreatedAt(OffsetDateTime.now());
    return contact;
  }
}
