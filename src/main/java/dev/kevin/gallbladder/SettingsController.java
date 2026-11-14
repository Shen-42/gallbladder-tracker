package dev.kevin.gallbladder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SettingsController {

    private final SettingsService settingsService;
    private final SettingsRepository settings;

    public SettingsController(SettingsService settingsService, SettingsRepository settings) {
        this.settingsService = settingsService;
        this.settings = settings;
    }

    @GetMapping("/settings")
    String edit(Model model) {
        model.addAttribute("settings", settings.current());
        return "settings";
    }

    @PostMapping("/settings")
    String save(@ModelAttribute("settings") Settings form, BindingResult result, RedirectAttributes redirect) {
        if (!settingsService.save(form, result)) {
            return "settings";
        }
        redirect.addFlashAttribute("saved", true);
        return "redirect:/settings";
    }
}
