package com.schedulr.common.timezone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimezoneConverterTest {

  private final TimezoneConverter converter = new TimezoneConverter();

  private final OffsetDateTime utcInstant =
      OffsetDateTime.of(2026, 7, 20, 14, 0, 0, 0, ZoneOffset.UTC);

  @Test
  void rendersDifferentStringsForDifferentTimezones() {
    String utc = converter.render(utcInstant, "UTC");
    String berlin = converter.render(utcInstant, "Europe/Berlin");
    String chicago = converter.render(utcInstant, "America/Chicago");

    assertThat(utc).isNotEqualTo(berlin);
    assertThat(utc).isNotEqualTo(chicago);
    assertThat(berlin).isNotEqualTo(chicago);
  }

  @Test
  void rendersExpectedFormat() {
    String rendered = converter.render(utcInstant, "UTC");
    assertThat(rendered).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} .+");
  }

  @Test
  void defaultsToUtcWhenTzNameIsBlank() {
    String rendered = converter.render(utcInstant, "");
    assertThat(rendered).isEqualTo(converter.render(utcInstant, "UTC"));
  }

  @Test
  void unknownZoneThrowsInvalidTimezoneException() {
    assertThatThrownBy(() -> converter.render(utcInstant, "Not/A_Zone"))
        .isInstanceOf(InvalidTimezoneException.class);
  }
}
