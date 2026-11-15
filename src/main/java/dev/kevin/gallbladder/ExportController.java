package dev.kevin.gallbladder;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class ExportController {

    private final CsvExportService export;
    private final Clock clock;

    public ExportController(CsvExportService export, Clock clock) {
        this.export = export;
        this.clock = clock;
    }

    @GetMapping("/export.zip")
    ResponseEntity<byte[]> csv() {
        String filename = "gallbladder-tracker-" + LocalDate.now(clock) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(export.zip());
    }
}
