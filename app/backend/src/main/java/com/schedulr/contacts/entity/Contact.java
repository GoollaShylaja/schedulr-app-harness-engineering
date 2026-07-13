package com.schedulr.contacts.entity;

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
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
public class Contact {

  @Id private UUID id;

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false)
  private String email;

  @Column(length = 160)
  private String company;

  @Column(length = 40)
  private String phone;

  @Column(length = 120)
  private String title;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(nullable = false, length = 40)
  private String stage = "lead";

  @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
