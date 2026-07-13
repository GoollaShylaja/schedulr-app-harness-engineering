package com.schedulr.common.util;

import com.fasterxml.uuid.Generators;
import java.util.UUID;

public final class IdGenerator {

  private IdGenerator() {}

  public static UUID newId() {
    return Generators.timeBasedEpochGenerator().generate();
  }
}
