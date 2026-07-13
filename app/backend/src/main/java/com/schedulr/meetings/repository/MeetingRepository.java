package com.schedulr.meetings.repository;

import com.schedulr.meetings.entity.Meeting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MeetingRepository
    extends JpaRepository<Meeting, UUID>, JpaSpecificationExecutor<Meeting> {

  Optional<Meeting> findByIdAndTeamId(UUID id, UUID teamId);
}
