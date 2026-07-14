package com.schedulr.contacts.repository;

import com.schedulr.contacts.entity.Contact;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

  List<Contact> findByIdInAndTeamId(Collection<UUID> ids, UUID teamId);

  Page<Contact> findByTeamId(UUID teamId, Pageable pageable);

  Optional<Contact> findByIdAndTeamId(UUID id, UUID teamId);
}
