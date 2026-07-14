package com.schedulr.contacts.service;

import com.schedulr.common.util.IdGenerator;
import com.schedulr.contacts.dto.ContactCreateRequest;
import com.schedulr.contacts.dto.ContactResponse;
import com.schedulr.contacts.dto.ContactUpdateRequest;
import com.schedulr.contacts.entity.Contact;
import com.schedulr.contacts.exception.ContactNotFoundException;
import com.schedulr.contacts.repository.ContactRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContactService {

  private final ContactRepository contactRepository;

  public Page<ContactResponse> list(UUID teamId, Pageable pageable) {
    return contactRepository.findByTeamId(teamId, pageable).map(this::toResponse);
  }

  public ContactResponse get(UUID id, UUID teamId) {
    return toResponse(loadForTeam(id, teamId));
  }

  public ContactResponse create(UUID teamId, ContactCreateRequest request) {
    Contact contact = new Contact();
    contact.setId(IdGenerator.newId());
    contact.setTeamId(teamId);
    contact.setName(request.name());
    contact.setEmail(request.email());
    contact.setCompany(request.company());
    contact.setPhone(request.phone());
    contact.setTitle(request.title());
    contact.setNotes(request.notes());
    contact.setStage(request.stage());
    contact.setCreatedAt(OffsetDateTime.now());
    return toResponse(contactRepository.save(contact));
  }

  public ContactResponse update(UUID id, UUID teamId, ContactUpdateRequest request) {
    Contact contact = loadForTeam(id, teamId);
    if (request.name() != null) {
      contact.setName(request.name());
    }
    if (request.email() != null) {
      contact.setEmail(request.email());
    }
    if (request.company() != null) {
      contact.setCompany(request.company());
    }
    if (request.phone() != null) {
      contact.setPhone(request.phone());
    }
    if (request.title() != null) {
      contact.setTitle(request.title());
    }
    if (request.notes() != null) {
      contact.setNotes(request.notes());
    }
    if (request.stage() != null) {
      contact.setStage(request.stage());
    }
    return toResponse(contactRepository.save(contact));
  }

  public void delete(UUID id, UUID teamId) {
    contactRepository.delete(loadForTeam(id, teamId));
  }

  private Contact loadForTeam(UUID id, UUID teamId) {
    return contactRepository
        .findByIdAndTeamId(id, teamId)
        .orElseThrow(ContactNotFoundException::new);
  }

  private ContactResponse toResponse(Contact contact) {
    return new ContactResponse(
        contact.getId(),
        contact.getName(),
        contact.getEmail(),
        contact.getCompany(),
        contact.getPhone(),
        contact.getTitle(),
        contact.getNotes(),
        contact.getStage(),
        contact.getCreatedAt());
  }
}
