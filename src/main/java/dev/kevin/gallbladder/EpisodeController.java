package dev.kevin.gallbladder;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Controller
public class EpisodeController {

    private final EpisodeService episodeService;
    private final EpisodeRepository episodes;
    private final Clock clock;

    public EpisodeController(EpisodeService episodeService, EpisodeRepository episodes, Clock clock) {
        this.episodeService = episodeService;
        this.episodes = episodes;
        this.clock = clock;
    }

    @GetMapping("/episodes/new")
    String newEpisode(Model model) {
        EpisodeForm form = new EpisodeForm();
        form.setStartedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
        model.addAttribute("episodeForm", form);
        return "episode-form";
    }

    @PostMapping("/episodes")
    String create(@ModelAttribute EpisodeForm episodeForm, BindingResult result) {
        Optional<Episode> episode = episodeService.log(episodeForm, result);
        return episode.map(e -> "redirect:/episodes/" + e.getId()).orElse("episode-form");
    }

    @GetMapping("/episodes/{id}")
    String show(@PathVariable Long id, Model model) {
        model.addAttribute("episode",
                episodes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "episode";
    }
}
