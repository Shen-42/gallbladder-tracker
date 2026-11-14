package dev.kevin.gallbladder;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class TimelineController {

    private final TimelineService timeline;
    private final SettingsRepository settings;
    private final Clock clock;

    public TimelineController(TimelineService timeline, SettingsRepository settings, Clock clock) {
        this.timeline = timeline;
        this.settings = settings;
        this.clock = clock;
    }

    @GetMapping("/days")
    String today() {
        return "redirect:/days/" + LocalDate.now(clock);
    }

    @GetMapping("/days/{date}")
    String day(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        LocalDate today = LocalDate.now(clock);
        model.addAttribute("date", date);
        model.addAttribute("isToday", date.equals(today));
        model.addAttribute("entries", timeline.day(date));
        model.addAttribute("threshold", settings.current().getFatThresholdGrams());
        model.addAttribute("previous", date.minusDays(1));
        // Future times can't be logged, so there is nothing to see after today.
        model.addAttribute("next", date.isBefore(today) ? date.plusDays(1) : null);
        return "day";
    }
}
