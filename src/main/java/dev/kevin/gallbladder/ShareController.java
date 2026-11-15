package dev.kevin.gallbladder;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/** Read-only pages for whoever holds an active share link. Nothing here changes data. */
@Controller
public class ShareController {

    private final ShareLinkService shareLinks;
    private final SummaryService summaryService;
    private final ChartService chartService;
    private final TimelineService timeline;
    private final ReportService reportService;
    private final SettingsRepository settings;
    private final Clock clock;

    public ShareController(ShareLinkService shareLinks, SummaryService summaryService, ChartService chartService,
                           TimelineService timeline, ReportService reportService, SettingsRepository settings,
                           Clock clock) {
        this.shareLinks = shareLinks;
        this.summaryService = summaryService;
        this.chartService = chartService;
        this.timeline = timeline;
        this.reportService = reportService;
        this.settings = settings;
        this.clock = clock;
    }

    @GetMapping("/share/{token}")
    String overview(@PathVariable String token, Model model, HttpServletResponse response) {
        LocalDate today = LocalDate.now(clock);
        return overviewView(token, model, response, today.minusDays(ChartService.DAYS - 1), today, null);
    }

    @GetMapping("/share/{token}/days/{date}")
    String day(@PathVariable String token, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
               Model model, HttpServletResponse response) {
        Optional<ShareLink> link = shareLinks.findActive(token);
        if (link.isEmpty()) {
            return expired(response);
        }
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("token", token);
        model.addAttribute("link", link.get());
        model.addAttribute("date", date);
        model.addAttribute("entries", timeline.day(date));
        model.addAttribute("threshold", settings.current().getFatThresholdGrams());
        model.addAttribute("previous", date.minusDays(1));
        model.addAttribute("next", date.isBefore(today) ? date.plusDays(1) : null);
        return "share-day";
    }

    @GetMapping("/share/{token}/report.pdf")
    String report(@PathVariable String token,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                  Model model, HttpServletResponse response) throws IOException {
        if (shareLinks.findActive(token).isEmpty()) {
            return expired(response);
        }
        if (from == null || to == null) {
            return overviewView(token, model, response, from, to, "Pick both a start and an end date.");
        }
        if (to.isBefore(from)) {
            return overviewView(token, model, response, from, to,
                    "The end date is before the start date. Pick an end on or after " + from + ".");
        }
        byte[] pdf = reportService.pdf(from, to);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("gallbladder-report-" + from + "-to-" + to + ".pdf").build().toString());
        response.getOutputStream().write(pdf);
        return null;
    }

    private String overviewView(String token, Model model, HttpServletResponse response,
                                LocalDate reportFrom, LocalDate reportTo, String reportError) {
        Optional<ShareLink> link = shareLinks.findActive(token);
        if (link.isEmpty()) {
            return expired(response);
        }
        model.addAttribute("token", token);
        model.addAttribute("link", link.get());
        model.addAttribute("s", summaryService.summary());
        ChartController.addChart(model, chartService.lastDays(), false);
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("reportFrom", reportFrom);
        model.addAttribute("reportTo", reportTo);
        model.addAttribute("reportError", reportError);
        return "share";
    }

    /** Unknown, expired and revoked links look the same, so the page says nothing about which. */
    private static String expired(HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return "share-expired";
    }
}
