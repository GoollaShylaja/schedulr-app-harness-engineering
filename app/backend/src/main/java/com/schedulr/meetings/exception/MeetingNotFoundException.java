package com.schedulr.meetings.exception;

import com.schedulr.common.error.exception.NotFoundException;

public class MeetingNotFoundException extends NotFoundException {

  public MeetingNotFoundException() {
    super("Meeting not found");
  }
}
