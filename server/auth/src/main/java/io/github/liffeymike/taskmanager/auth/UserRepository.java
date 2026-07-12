package io.github.liffeymike.taskmanager.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  boolean userExistsByEmail(String email);

  Optional<User> findByEmail(String email);

}
