package com.schedulr.meetings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meeting_invitees")
@Getter
@Setter
@NoArgsConstructor
public class MeetingInvitee {

  @Id private UUID id;

  @Column(name = "meeting_id", nullable = false)
  private UUID meetingId;

  @Column(name = "contact_id", nullable = false)
  private UUID contactId;

  @Column(nullable = false, length = 32)
  private String response = "pending";
}
