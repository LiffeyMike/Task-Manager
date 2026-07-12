package io.github.liffeymike.taskmanager.auth;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.generator.EventType;
import org.hibernate.annotations.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "Users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String password_hash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;

}
