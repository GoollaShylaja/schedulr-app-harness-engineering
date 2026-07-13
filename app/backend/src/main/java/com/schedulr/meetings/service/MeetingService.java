package com.schedulr.meetings.service;

import com.schedulr.auth.entity.User;
import com.schedulr.auth.repository.UserRepository;
import com.schedulr.common.error.exception.InvalidRequestException;
import com.schedulr.common.error.exception.NotAuthorizedException;
import com.schedulr.common.security.AuthenticatedUser;
import com.schedulr.common.timezone.TimezoneConverter;
import com.schedulr.common.util.IdGenerator;
import com.schedulr.contacts.entity.Contact;
import com.schedulr.contacts.repository.ContactRepository;
import com.schedulr.meetings.dto.InviteeResponse;
import com.schedulr.meetings.dto.MeetingCreateRequest;
import com.schedulr.meetings.dto.MeetingResponse;
import com.schedulr.meetings.dto.MeetingUpdateRequest;
import com.schedulr.meetings.dto.RsvpUpdateRequest;
import com.schedulr.meetings.entity.Meeting;
import com.schedulr.meetings.entity.MeetingInvitee;
import com.schedulr.meetings.exception.InvalidScheduleException;
import com.schedulr.meetings.exception.InviteeNotFoundException;
import com.schedulr.meetings.exception.MeetingNotFoundException;
import com.schedulr.meetings.repository.MeetingInviteeRepository;
import com.schedulr.meetings.repository.MeetingRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MeetingService {

  private static final Set<String> VALID_RSVP_RESPONSES = Set.of("accepted", "declined", "pending");

  private final MeetingRepository meetingRepository;
  private final MeetingInviteeRepository meetingInviteeRepository;
  private final ContactRepository contactRepository;
  private final UserRepository userRepository;
  private final TimezoneConverter timezoneConverter;

  public Page<MeetingResponse> list(
      UUID teamId,
      UUID hostId,
      OffsetDateTime startAfter,
      OffsetDateTime startBefore,
      UUID contactId,
      String status,
      String search,
      String viewerTz,
      Pageable pageable) {
    Specification<Meeting> spec =
        Specification.allOf(
            MeetingSpecifications.teamId(teamId),
            MeetingSpecifications.hostId(hostId),
            MeetingSpecifications.startAfter(startAfter),
            MeetingSpecifications.startBefore(startBefore),
            MeetingSpecifications.status(status),
            MeetingSpecifications.titleContains(search),
            MeetingSpecifications.hasInviteeContact(contactId));
    return meetingRepository.findAll(spec, pageable).map(m -> toResponse(m, viewerTz, false));
  }

  public MeetingResponse get(UUID meetingId, UUID teamId, String viewerTz) {
    Meeting meeting = loadForTeam(meetingId, teamId);
    return toResponse(meeting, viewerTz, true);
  }

  public MeetingResponse create(
      UUID teamId, UUID hostId, MeetingCreateRequest request, String viewerTz) {
    if (!request.endTime().isAfter(request.startTime())) {
      throw new InvalidScheduleException("End time must be after start time");
    }
    timezoneConverter.validateZone(request.meetingTimezone());

    Meeting meeting = new Meeting();
    meeting.setId(IdGenerator.newId());
    meeting.setTeamId(teamId);
    meeting.setHostId(hostId);
    meeting.setTitle(request.title());
    meeting.setNotes(request.notes());
    meeting.setStartTime(request.startTime());
    meeting.setEndTime(request.endTime());
    meeting.setMeetingTimezone(request.meetingTimezone());
    meeting.setCreatedAt(OffsetDateTime.now());
    meetingRepository.save(meeting);

    List<Contact> resolvedContacts =
        request.inviteeContactIds().isEmpty()
            ? List.of()
            : contactRepository.findByIdInAndTeamId(request.inviteeContactIds(), teamId);
    for (Contact contact : resolvedContacts) {
      MeetingInvitee invitee = new MeetingInvitee();
      invitee.setId(IdGenerator.newId());
      invitee.setMeetingId(meeting.getId());
      invitee.setContactId(contact.getId());
      meetingInviteeRepository.save(invitee);
    }

    return toResponse(meeting, viewerTz, true);
  }

  public MeetingResponse update(
      UUID meetingId,
      UUID teamId,
      AuthenticatedUser current,
      MeetingUpdateRequest request,
      String viewerTz) {
    Meeting meeting = loadForTeam(meetingId, teamId);
    requireHostOrAdmin(meeting, current);

    if (request.title() != null) {
      meeting.setTitle(request.title());
    }
    if (request.notes() != null) {
      meeting.setNotes(request.notes());
    }
    if (request.startTime() != null) {
      meeting.setStartTime(request.startTime());
    }
    if (request.endTime() != null) {
      meeting.setEndTime(request.endTime());
    }
    if (!meeting.getEndTime().isAfter(meeting.getStartTime())) {
      throw new InvalidScheduleException("End time must be after start time");
    }
    if (request.meetingTimezone() != null) {
      timezoneConverter.validateZone(request.meetingTimezone());
      meeting.setMeetingTimezone(request.meetingTimezone());
    }
    if (request.status() != null) {
      meeting.setStatus(request.status());
    }
    meetingRepository.save(meeting);
    return toResponse(meeting, viewerTz, true);
  }

  public void cancel(UUID meetingId, UUID teamId, AuthenticatedUser current) {
    Meeting meeting = loadForTeam(meetingId, teamId);
    requireHostOrAdmin(meeting, current);
    meeting.setStatus("cancelled");
    meetingRepository.save(meeting);
  }

  public InviteeResponse updateRsvp(
      UUID meetingId, UUID inviteeId, UUID teamId, RsvpUpdateRequest request) {
    loadForTeam(meetingId, teamId);
    MeetingInvitee invitee =
        meetingInviteeRepository
            .findByIdAndMeetingId(inviteeId, meetingId)
            .orElseThrow(InviteeNotFoundException::new);
    if (!VALID_RSVP_RESPONSES.contains(request.response())) {
      throw new InvalidRequestException("Response must be one of " + VALID_RSVP_RESPONSES);
    }
    invitee.setResponse(request.response());
    meetingInviteeRepository.save(invitee);
    return toInviteeResponse(invitee);
  }

  private Meeting loadForTeam(UUID meetingId, UUID teamId) {
    return meetingRepository
        .findByIdAndTeamId(meetingId, teamId)
        .orElseThrow(MeetingNotFoundException::new);
  }

  private void requireHostOrAdmin(Meeting meeting, AuthenticatedUser current) {
    boolean isHost = meeting.getHostId().equals(current.id());
    boolean isAdmin = "admin".equals(current.role());
    if (!isHost && !isAdmin) {
      throw new NotAuthorizedException("Not authorized");
    }
  }

  private MeetingResponse toResponse(Meeting meeting, String viewerTz, boolean includeInvitees) {
    String hostName =
        userRepository.findById(meeting.getHostId()).map(User::getFullName).orElse(null);
    List<MeetingInvitee> invitees =
        includeInvitees ? meetingInviteeRepository.findByMeetingId(meeting.getId()) : List.of();
    int inviteeCount =
        includeInvitees
            ? invitees.size()
            : (int) meetingInviteeRepository.countByMeetingId(meeting.getId());

    List<InviteeResponse> inviteeResponses =
        includeInvitees ? invitees.stream().map(this::toInviteeResponse).toList() : List.of();

    return new MeetingResponse(
        meeting.getId(),
        meeting.getTitle(),
        hostName,
        meeting.getHostId(),
        timezoneConverter.render(meeting.getStartTime(), viewerTz),
        timezoneConverter.render(meeting.getEndTime(), viewerTz),
        meeting.getMeetingTimezone(),
        meeting.getStatus(),
        meeting.getNotes(),
        inviteeCount,
        inviteeResponses);
  }

  private InviteeResponse toInviteeResponse(MeetingInvitee invitee) {
    Contact contact = contactRepository.findById(invitee.getContactId()).orElse(null);
    return new InviteeResponse(
        invitee.getId(),
        invitee.getContactId(),
        contact != null ? contact.getName() : "",
        contact != null ? contact.getEmail() : "",
        invitee.getResponse());
  }
}
