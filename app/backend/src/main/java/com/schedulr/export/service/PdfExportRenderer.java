package com.schedulr.export.service;

import com.schedulr.meetings.dto.MeetingResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component("pdf")
public class PdfExportRenderer implements ExportRenderer {

  @Override
  public String contentType() {
    return "application/pdf";
  }

  @Override
  public String fileExtension() {
    return "pdf";
  }

  @Override
  public byte[] render(List<MeetingResponse> meetings, String viewerTz) {
    StringBuilder sb =
        new StringBuilder("Meetings Export (PDF)\n").append("=".repeat(24)).append('\n');
    for (MeetingResponse meeting : meetings) {
      sb.append(meeting.title())
          .append(" | ")
          .append(meeting.start())
          .append(" | ")
          .append(meeting.status())
          .append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }
}
