package com.schedulr.export.service;

import com.schedulr.meetings.dto.InviteeResponse;
import com.schedulr.meetings.dto.MeetingResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component("csv")
public class CsvExportRenderer implements ExportRenderer {

  private static final String HEADER = "ID,Title,Start,End,Timezone,Status,Invitees";
  private static final char BOM = '﻿';

  @Override
  public String contentType() {
    return "text/csv";
  }

  @Override
  public String fileExtension() {
    return "csv";
  }

  @Override
  public byte[] render(List<MeetingResponse> meetings, String viewerTz) {
    StringBuilder sb = new StringBuilder();
    sb.append(BOM).append(HEADER).append('\n');
    for (MeetingResponse meeting : meetings) {
      sb.append(csvField(meeting.id().toString()))
          .append(',')
          .append(csvField(ExportService.csvSafe(meeting.title())))
          .append(',')
          .append(csvField(meeting.start()))
          .append(',')
          .append(csvField(meeting.end()))
          .append(',')
          .append(csvField(meeting.timezone()))
          .append(',')
          .append(csvField(meeting.status()))
          .append(',')
          .append(csvField(inviteesSummary(meeting)))
          .append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String inviteesSummary(MeetingResponse meeting) {
    return meeting.invitees().stream().map(this::formatInvitee).collect(Collectors.joining("; "));
  }

  private String formatInvitee(InviteeResponse invitee) {
    return ExportService.csvSafe(invitee.contactName())
        + " <"
        + ExportService.csvSafe(invitee.contactEmail())
        + ">";
  }

  private String csvField(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }
}
