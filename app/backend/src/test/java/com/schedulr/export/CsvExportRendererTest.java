package com.schedulr.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.schedulr.export.service.CsvExportRenderer;
import com.schedulr.meetings.dto.InviteeResponse;
import com.schedulr.meetings.dto.MeetingResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CsvExportRendererTest {

  private final CsvExportRenderer renderer = new CsvExportRenderer();

  @Test
  void renderReturnsNonEmptyBytesForEmptyList() {
    byte[] result = renderer.render(List.of(), "UTC");
    assertThat(result).isNotEmpty();
  }

  @Test
  void headerRowMatchesExpectedColumns() {
    String content = new String(renderer.render(List.of(), "UTC"), StandardCharsets.UTF_8);
    String firstLine = content.lines().findFirst().orElseThrow();
    assertThat(firstLine).endsWith("ID,Title,Start,End,Timezone,Status,Invitees");
  }

  @Test
  void csvFormulaInjectionInTitleIsEscaped() {
    MeetingResponse meeting = meetingWithTitle("=HYPERLINK(\"http://evil.com\",\"Click\")");

    String csv = new String(renderer.render(List.of(meeting), "UTC"), StandardCharsets.UTF_8);
    String dataLine = csv.lines().skip(1).findFirst().orElseThrow();

    assertThat(dataLine).doesNotContain(",=HYPERLINK");
    assertThat(dataLine).contains("'=HYPERLINK");
  }

  @Test
  void csvFormulaInjectionInInviteeNameIsEscaped() {
    InviteeResponse invitee =
        new InviteeResponse(
            UUID.randomUUID(), UUID.randomUUID(), "=cmd|calc", "evil@test.com", "pending");
    MeetingResponse meeting =
        new MeetingResponse(
            UUID.randomUUID(),
            "Normal title",
            "Dana Ortiz",
            UUID.randomUUID(),
            "2026-07-20 09:00 CDT",
            "2026-07-20 09:30 CDT",
            "America/Chicago",
            "scheduled",
            null,
            1,
            List.of(invitee));

    String csv = new String(renderer.render(List.of(meeting), "UTC"), StandardCharsets.UTF_8);

    assertThat(csv).contains("'=cmd|calc");
  }

  private MeetingResponse meetingWithTitle(String title) {
    return new MeetingResponse(
        UUID.randomUUID(),
        title,
        "Dana Ortiz",
        UUID.randomUUID(),
        "2026-07-20 09:00 CDT",
        "2026-07-20 09:30 CDT",
        "America/Chicago",
        "scheduled",
        null,
        0,
        List.of());
  }
}
