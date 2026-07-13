package com.schedulr.export.service;

import com.schedulr.export.exception.UnsupportedExportFormatException;
import com.schedulr.meetings.dto.MeetingResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ExportService {

  private static final Set<Character> FORMULA_PREFIXES = Set.of('=', '+', '-', '@');

  private final Map<String, ExportRenderer> renderers;

  public ExportService(Map<String, ExportRenderer> renderers) {
    this.renderers = renderers;
  }

  public static String csvSafe(String value) {
    if (value != null && !value.isEmpty() && FORMULA_PREFIXES.contains(value.charAt(0))) {
      return "'" + value;
    }
    return value;
  }

  public byte[] render(String format, List<MeetingResponse> meetings, String viewerTz) {
    return resolve(format).render(meetings, viewerTz);
  }

  public String contentType(String format) {
    return resolve(format).contentType();
  }

  public String fileExtension(String format) {
    return resolve(format).fileExtension();
  }

  private ExportRenderer resolve(String format) {
    ExportRenderer renderer = renderers.get(format);
    if (renderer == null) {
      throw new UnsupportedExportFormatException(format);
    }
    return renderer;
  }
}
