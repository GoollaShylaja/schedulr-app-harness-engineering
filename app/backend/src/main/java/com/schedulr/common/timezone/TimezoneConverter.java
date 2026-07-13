package com.schedulr.common.timezone;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class TimezoneConverter {

  private static final DateTimeFormatter DISPLAY_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm zzz");

  public OffsetDateTime inZone(OffsetDateTime value, String tzName) {
    return value.atZoneSameInstant(resolveZone(tzName)).toOffsetDateTime();
  }

  public String render(OffsetDateTime value, String tzName) {
    return value.atZoneSameInstant(resolveZone(tzName)).format(DISPLAY_FORMAT);
  }

  public void validateZone(String tzName) {
    resolveZone(tzName);
  }

  private ZoneId resolveZone(String tzName) {
    String zone = (tzName == null || tzName.isBlank()) ? "UTC" : tzName;
    if (!ZoneId.getAvailableZoneIds().contains(zone)) {
      throw new InvalidTimezoneException(zone);
    }
    return ZoneId.of(zone);
  }
}
