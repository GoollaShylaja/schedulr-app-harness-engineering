package com.schedulr.availability.service;

import com.schedulr.auth.repository.UserRepository;
import com.schedulr.availability.dto.AvailabilityBulkSetRequest;
import com.schedulr.availability.dto.AvailabilitySlotCreateRequest;
import com.schedulr.availability.dto.AvailabilitySlotResponse;
import com.schedulr.availability.entity.AvailabilitySlot;
import com.schedulr.availability.exception.SlotNotFoundException;
import com.schedulr.availability.repository.AvailabilitySlotRepository;
import com.schedulr.common.error.exception.NotFoundException;
import com.schedulr.common.util.IdGenerator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AvailabilityService {

  private final AvailabilitySlotRepository slotRepository;
  private final UserRepository userRepository;

  public List<AvailabilitySlotResponse> listOwn(UUID userId) {
    return toResponses(slotRepository.findByUserIdOrderByWeekday(userId));
  }

  public List<AvailabilitySlotResponse> listForUser(UUID targetUserId, UUID teamId) {
    userRepository
        .findByIdAndTeamId(targetUserId, teamId)
        .orElseThrow(() -> new NotFoundException("User not found"));
    return toResponses(slotRepository.findByUserIdOrderByWeekday(targetUserId));
  }

  public AvailabilitySlotResponse add(UUID userId, AvailabilitySlotCreateRequest request) {
    AvailabilitySlot slot = new AvailabilitySlot();
    slot.setId(IdGenerator.newId());
    slot.setUserId(userId);
    slot.setWeekday(request.weekday());
    slot.setStart(request.start());
    slot.setEnd(request.end());
    return toResponse(slotRepository.save(slot));
  }

  public List<AvailabilitySlotResponse> bulkSet(UUID userId, AvailabilityBulkSetRequest request) {
    slotRepository.deleteByUserId(userId);
    List<AvailabilitySlot> slots =
        request.slots().stream()
            .map(
                s -> {
                  AvailabilitySlot slot = new AvailabilitySlot();
                  slot.setId(IdGenerator.newId());
                  slot.setUserId(userId);
                  slot.setWeekday(s.weekday());
                  slot.setStart(s.start());
                  slot.setEnd(s.end());
                  return slot;
                })
            .toList();
    return toResponses(slotRepository.saveAll(slots));
  }

  public void delete(UUID userId, UUID slotId) {
    AvailabilitySlot slot =
        slotRepository.findByIdAndUserId(slotId, userId).orElseThrow(SlotNotFoundException::new);
    slotRepository.delete(slot);
  }

  private List<AvailabilitySlotResponse> toResponses(List<AvailabilitySlot> slots) {
    return slots.stream().map(this::toResponse).toList();
  }

  private AvailabilitySlotResponse toResponse(AvailabilitySlot slot) {
    return new AvailabilitySlotResponse(
        slot.getId(), slot.getUserId(), slot.getWeekday(), slot.getStart(), slot.getEnd());
  }
}
