package com.schedulr.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.schedulr.export.service.PdfExportRenderer;
import com.schedulr.meetings.dto.MeetingResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PdfExportRendererTest {

  private final PdfExportRenderer renderer = new PdfExportRenderer();

  @Test
  void renderReturnsNonEmptyBytesForEmptyList() {
    byte[] result = renderer.render(List.of(), "UTC");
    assertThat(result).isNotEmpty();
  }

  @Test
  void renderIncludesMeetingTitleAndStatus() {
    MeetingResponse meeting =
        new MeetingResponse(
            UUID.randomUUID(),
            "Discovery call",
            "Dana Ortiz",
            UUID.randomUUID(),
            "2026-07-20 09:00 CDT",
            "2026-07-20 09:30 CDT",
            "America/Chicago",
            "scheduled",
            null,
            0,
            List.of());

    String content = new String(renderer.render(List.of(meeting), "UTC"), StandardCharsets.UTF_8);

    assertThat(content).contains("Discovery call");
    assertThat(content).contains("2026-07-20 09:00 CDT");
    assertThat(content).contains("scheduled");
  }

  @Test
  void contentTypeAndExtension() {
    assertThat(renderer.contentType()).isEqualTo("application/pdf");
    assertThat(renderer.fileExtension()).isEqualTo("pdf");
  }
}
