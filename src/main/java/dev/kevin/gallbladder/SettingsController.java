package dev.kevin.gallbladder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class SettingsController {

    private final SettingsService settingsService;
    private final SettingsRepository settings;
    private final ShareLinkService shareLinks;

    public SettingsController(SettingsService settingsService, SettingsRepository settings,
                              ShareLinkService shareLinks) {
        this.settingsService = settingsService;
        this.settings = settings;
        this.shareLinks = shareLinks;
    }

    @ModelAttribute("shareLinks")
    List<ShareLink> shareLinks() {
        return shareLinks.active();
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

    /** Shows the full link once, on the next page; afterwards only its label and expiry are known. */
    @PostMapping("/settings/share")
    String share(@RequestParam(required = false) String label,
                 @RequestParam(defaultValue = "" + ShareLinkService.DEFAULT_DAYS) int days,
                 RedirectAttributes redirect) {
        ShareLinkService.Created created = shareLinks.create(label, days);
        redirect.addFlashAttribute("newShareUrl", ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/share/{token}").buildAndExpand(created.token()).toUriString());
        redirect.addFlashAttribute("newShare", created.link());
        return "redirect:/settings#share";
    }

    @PostMapping("/settings/share/{id}/revoke")
    String revoke(@PathVariable long id, RedirectAttributes redirect) {
        shareLinks.revoke(id);
        redirect.addFlashAttribute("revoked", true);
        return "redirect:/settings#share";
    }
}
