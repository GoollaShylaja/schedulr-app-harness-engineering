package com.schedulr.auth.exception;

import com.schedulr.common.error.exception.UnauthenticatedException;

public class InvalidCredentialsException extends UnauthenticatedException {

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
