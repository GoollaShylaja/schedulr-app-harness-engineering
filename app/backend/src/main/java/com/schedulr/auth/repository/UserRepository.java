package com.schedulr.auth.repository;

import com.schedulr.auth.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  List<User> findAllByTeamIdOrderByFullName(UUID teamId);

  Optional<User> findByIdAndTeamId(UUID id, UUID teamId);
}
