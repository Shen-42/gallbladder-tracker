package dev.kevin.gallbladder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    String summary(Model model) {
        model.addAttribute("s", summaryService.summary());
        return "summary";
    }

    @GetMapping("/summary/help")
    String help(Model model) {
        model.addAttribute("minEpisodes", SummaryService.MIN_EPISODES_FOR_PERCENTAGES);
        return "summary-help";
    }
}
