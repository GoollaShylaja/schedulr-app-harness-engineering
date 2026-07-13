package com.schedulr.auth.entity;

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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "hashed_password", nullable = false)
  private String hashedPassword;

  @Column(name = "full_name", nullable = false, length = 160)
  private String fullName;

  @Column(nullable = false, length = 64)
  private String timezone = "UTC";

  @Column(nullable = false, length = 32)
  private String role = "member";

  @Column(name = "team_id", nullable = false)
  private UUID teamId;

  @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
  private OffsetDateTime createdAt;
}
