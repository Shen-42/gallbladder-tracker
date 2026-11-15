package dev.kevin.gallbladder;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

@Controller
public class SummaryController {

    private final SummaryService summaryService;
    private final ReportService reportService;
    private final FoodTestService foodTestService;
    private final Clock clock;

    public SummaryController(SummaryService summaryService, ReportService reportService,
                             FoodTestService foodTestService, Clock clock) {
        this.summaryService = summaryService;
        this.reportService = reportService;
        this.foodTestService = foodTestService;
        this.clock = clock;
    }

    @GetMapping("/summary")
    String summary(Model model) {
        LocalDate today = LocalDate.now(clock);
        return summaryView(model, today.minusDays(ChartService.DAYS - 1), today, null);
    }

    @GetMapping("/summary/help")
    String help(Model model) {
        model.addAttribute("minEpisodes", SummaryService.MIN_EPISODES_FOR_PERCENTAGES);
        model.addAttribute("minMeals", FoodTestService.MIN_MEALS_PER_FOOD);
        model.addAttribute("permutations", FoodTestService.PERMUTATIONS);
        return "summary-help";
    }

    @GetMapping("/summary/foods")
    String foods(Model model) {
        model.addAttribute("r", foodTestService.report());
        model.addAttribute("minEpisodes", SummaryService.MIN_EPISODES_FOR_PERCENTAGES);
        model.addAttribute("minMeals", FoodTestService.MIN_MEALS_PER_FOOD);
        return "foods";
    }

    /** Writes the PDF for appointments, or shows the summary page with an error beside the dates. */
    @GetMapping("/summary/report.pdf")
    String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                  Model model, HttpServletResponse response) throws IOException {
        if (from == null || to == null) {
            return summaryView(model, from, to, "Pick both a start and an end date.");
        }
        if (to.isBefore(from)) {
            return summaryView(model, from, to, "The end date is before the start date. Pick an end on or after "
                    + from + ".");
        }
        String filename = "gallbladder-report-" + from + "-to-" + to + ".pdf";
        byte[] pdf = reportService.pdf(from, to);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
        response.getOutputStream().write(pdf);
        return null;
    }

    private String summaryView(Model model, LocalDate from, LocalDate to, String reportError) {
        model.addAttribute("s", summaryService.summary());
        model.addAttribute("reportFrom", from);
        model.addAttribute("reportTo", to);
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("reportError", reportError);
        return "summary";
    }
}
