package com.schedulr.availability.repository;

import com.schedulr.availability.entity.AvailabilitySlot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

  List<AvailabilitySlot> findByUserIdOrderByWeekday(UUID userId);

  Optional<AvailabilitySlot> findByIdAndUserId(UUID id, UUID userId);

  void deleteByUserId(UUID userId);
}
