package dev.kevin.gallbladder;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Tells the nav which link is the current page. */
@ControllerAdvice
public class NavAdvice {

    @ModelAttribute("navSection")
    String navSection(HttpServletRequest request) {
        return section(request.getRequestURI().substring(request.getContextPath().length()));
    }

    static String section(String path) {
        if (path.equals("/meals/new") || path.equals("/meals")) return "meal";
        if (path.equals("/episodes/new") || path.equals("/episodes")) return "pain";
        if (path.startsWith("/days") || path.startsWith("/meals/") || path.startsWith("/episodes/")) return "timeline";
        if (path.startsWith("/chart")) return "chart";
        if (path.startsWith("/summary")) return "summary";
        if (path.startsWith("/settings")) return "settings";
        return "";
    }
}
