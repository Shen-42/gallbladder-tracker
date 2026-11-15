package dev.kevin.gallbladder;

import dev.kevin.gallbladder.ChartService.ChartData;
import dev.kevin.gallbladder.ChartService.Point;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Controller
public class ChartController {

    private final ChartService chartService;

    public ChartController(ChartService chartService) {
        this.chartService = chartService;
    }

    @GetMapping("/chart")
    String chart(Model model) {
        ChartData data = chartService.lastDays();
        model.addAttribute("chart", data);
        model.addAttribute("days", ChartService.DAYS);
        // Plain numbers for the script. Times are local times sent as if UTC, so the browser shows them unchanged.
        model.addAttribute("mealPoints", toJs(data.meals(), "/meals/"));
        model.addAttribute("episodePoints", toJs(data.episodes(), "/episodes/"));
        model.addAttribute("fromMillis", data.from().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        model.addAttribute("toMillis", data.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        return "chart";
    }

    private static List<Map<String, Object>> toJs(List<Point> points, String urlPrefix) {
        return points.stream().<Map<String, Object>>map(p -> Map.of(
                "x", p.at().toInstant(ZoneOffset.UTC).toEpochMilli(),
                "y", p.value().doubleValue(),
                "url", urlPrefix + p.id())).toList();
    }
}
