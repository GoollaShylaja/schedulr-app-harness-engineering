package com.schedulr.meetings.service;

import com.schedulr.meetings.entity.Meeting;
import com.schedulr.meetings.entity.MeetingInvitee;
import jakarta.persistence.criteria.Subquery;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class MeetingSpecifications {

  private MeetingSpecifications() {}

  static Specification<Meeting> teamId(UUID teamId) {
    return (root, query, cb) -> cb.equal(root.get("teamId"), teamId);
  }

  static Specification<Meeting> hostId(UUID hostId) {
    return (root, query, cb) ->
        hostId == null ? cb.conjunction() : cb.equal(root.get("hostId"), hostId);
  }

  static Specification<Meeting> startAfter(OffsetDateTime startAfter) {
    return (root, query, cb) ->
        startAfter == null
            ? cb.conjunction()
            : cb.greaterThanOrEqualTo(root.get("startTime"), startAfter);
  }

  static Specification<Meeting> startBefore(OffsetDateTime startBefore) {
    return (root, query, cb) ->
        startBefore == null
            ? cb.conjunction()
            : cb.lessThanOrEqualTo(root.get("startTime"), startBefore);
  }

  static Specification<Meeting> status(String status) {
    return (root, query, cb) ->
        status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
  }

  static Specification<Meeting> titleContains(String search) {
    return (root, query, cb) ->
        search == null
            ? cb.conjunction()
            : cb.like(cb.lower(root.get("title")), "%" + search.toLowerCase() + "%");
  }

  static Specification<Meeting> hasInviteeContact(UUID contactId) {
    return (root, query, cb) -> {
      if (contactId == null) {
        return cb.conjunction();
      }
      Subquery<UUID> subquery = query.subquery(UUID.class);
      var invitee = subquery.from(MeetingInvitee.class);
      subquery
          .select(invitee.get("meetingId"))
          .where(cb.equal(invitee.get("contactId"), contactId));
      return root.get("id").in(subquery);
    };
  }
}
