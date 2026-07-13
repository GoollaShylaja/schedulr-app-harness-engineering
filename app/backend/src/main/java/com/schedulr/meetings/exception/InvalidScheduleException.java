package com.schedulr.meetings.exception;

import com.schedulr.common.error.exception.InvalidRequestException;

public class InvalidScheduleException extends InvalidRequestException {

  public InvalidScheduleException(String message) {
    super(message);
  }
}
