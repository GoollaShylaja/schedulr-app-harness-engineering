package com.schedulr.meetings.exception;

import com.schedulr.common.error.exception.NotFoundException;

public class InviteeNotFoundException extends NotFoundException {

  public InviteeNotFoundException() {
    super("Invitee not found");
  }
}
