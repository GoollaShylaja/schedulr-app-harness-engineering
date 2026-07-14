package com.schedulr.contacts.exception;

import com.schedulr.common.error.exception.NotFoundException;

public class ContactNotFoundException extends NotFoundException {

  public ContactNotFoundException() {
    super("Contact not found");
  }
}
