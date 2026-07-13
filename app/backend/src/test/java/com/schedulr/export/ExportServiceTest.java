package com.schedulr.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schedulr.export.exception.UnsupportedExportFormatException;
import com.schedulr.export.service.CsvExportRenderer;
import com.schedulr.export.service.ExportService;
import com.schedulr.export.service.PdfExportRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExportServiceTest {

  private final ExportService exportService =
      new ExportService(Map.of("pdf", new PdfExportRenderer(), "csv", new CsvExportRenderer()));

  @Test
  void csvSafeEscapesFormulaPrefixes() {
    assertThat(ExportService.csvSafe("=SUM(A1)")).isEqualTo("'=SUM(A1)");
    assertThat(ExportService.csvSafe("+1")).isEqualTo("'+1");
    assertThat(ExportService.csvSafe("-1")).isEqualTo("'-1");
    assertThat(ExportService.csvSafe("@cmd")).isEqualTo("'@cmd");
  }

  @Test
  void csvSafeLeavesNormalValuesUnchanged() {
    assertThat(ExportService.csvSafe("Discovery call")).isEqualTo("Discovery call");
    assertThat(ExportService.csvSafe(null)).isNull();
    assertThat(ExportService.csvSafe("")).isEmpty();
  }

  @Test
  void resolvesKnownFormat() {
    assertThat(exportService.contentType("pdf")).isEqualTo("application/pdf");
    assertThat(exportService.contentType("csv")).isEqualTo("text/csv");
  }

  @Test
  void unknownFormatThrowsUnsupportedExportFormatException() {
    assertThatThrownBy(() -> exportService.render("xlsx", java.util.List.of(), "UTC"))
        .isInstanceOf(UnsupportedExportFormatException.class);
  }
}
