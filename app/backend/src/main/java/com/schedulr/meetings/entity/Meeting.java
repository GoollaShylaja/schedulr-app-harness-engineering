package com.schedulr.meetings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor
public class Meeting {

  @Id private UUID id;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "host_id", nullable = false)
  private UUID hostId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "start_time", nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime startTime;

  @Column(name = "end_time", nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime endTime;

  @Column(name = "meeting_timezone", nullable = false, length = 64)
  private String meetingTimezone = "UTC";

  @Column(nullable = false, length = 32)
  private String status = "scheduled";

  @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
