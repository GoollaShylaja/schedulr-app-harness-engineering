package com.schedulr.export.exception;

import com.schedulr.common.error.exception.InvalidRequestException;

public class UnsupportedExportFormatException extends InvalidRequestException {

  public UnsupportedExportFormatException(String format) {
    super("Unsupported export format: " + format);
  }
}
