package com.schedulr.contacts.repository;

import com.schedulr.contacts.entity.Contact;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

  List<Contact> findByIdInAndTeamId(Collection<UUID> ids, UUID teamId);
}
