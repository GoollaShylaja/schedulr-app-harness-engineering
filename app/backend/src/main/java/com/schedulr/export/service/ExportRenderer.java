package com.schedulr.export.service;

import com.schedulr.meetings.dto.MeetingResponse;
import java.util.List;

public interface ExportRenderer {

  String contentType();

  String fileExtension();

  byte[] render(List<MeetingResponse> meetings, String viewerTz);
}
