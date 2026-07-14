package com.schedulr.availability.exception;

import com.schedulr.common.error.exception.NotFoundException;

public class SlotNotFoundException extends NotFoundException {

  public SlotNotFoundException() {
    super("Slot not found");
  }
}
