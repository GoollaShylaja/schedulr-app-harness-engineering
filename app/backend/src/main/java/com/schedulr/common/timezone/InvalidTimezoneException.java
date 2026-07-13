package com.schedulr.common.timezone;

import com.schedulr.common.error.exception.InvalidRequestException;

public class InvalidTimezoneException extends InvalidRequestException {

  public InvalidTimezoneException(String tzName) {
    super("Invalid timezone: " + tzName);
  }
}
