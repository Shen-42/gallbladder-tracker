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
        addChart(model, chartService.lastDays(), true);
        return "chart";
    }

    /** What the "chart :: chart" fragment needs. {@code links} makes points open their meal or episode. */
    static void addChart(Model model, ChartData data, boolean links) {
        model.addAttribute("chart", data);
        model.addAttribute("days", ChartService.DAYS);
        model.addAttribute("chartLinks", links);
        // Plain numbers for the script. Times are local times sent as if UTC, so the browser shows them unchanged.
        model.addAttribute("mealPoints", toJs(data.meals(), links ? "/meals/" : null));
        model.addAttribute("episodePoints", toJs(data.episodes(), links ? "/episodes/" : null));
        model.addAttribute("fromMillis", data.from().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        model.addAttribute("toMillis", data.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    private static List<Map<String, Object>> toJs(List<Point> points, String urlPrefix) {
        return points.stream().<Map<String, Object>>map(p -> urlPrefix == null
                ? Map.of("x", p.at().toInstant(ZoneOffset.UTC).toEpochMilli(), "y", p.value().doubleValue())
                : Map.of("x", p.at().toInstant(ZoneOffset.UTC).toEpochMilli(), "y", p.value().doubleValue(),
                        "url", urlPrefix + p.id())).toList();
    }
}
