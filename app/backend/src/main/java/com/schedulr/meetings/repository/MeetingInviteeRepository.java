package com.schedulr.meetings.repository;

import com.schedulr.meetings.entity.MeetingInvitee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingInviteeRepository extends JpaRepository<MeetingInvitee, UUID> {

  List<MeetingInvitee> findByMeetingId(UUID meetingId);

  Optional<MeetingInvitee> findByIdAndMeetingId(UUID id, UUID meetingId);

  long countByMeetingId(UUID meetingId);

  List<MeetingInvitee> findByContactId(UUID contactId);
}
