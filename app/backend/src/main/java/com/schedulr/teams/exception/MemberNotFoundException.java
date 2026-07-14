package com.schedulr.teams.exception;

import com.schedulr.common.error.exception.NotFoundException;

public class MemberNotFoundException extends NotFoundException {

  public MemberNotFoundException() {
    super("Member not found");
  }
}
