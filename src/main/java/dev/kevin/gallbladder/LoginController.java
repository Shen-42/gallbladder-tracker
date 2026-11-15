package dev.kevin.gallbladder;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /** Session attribute holding the username of the last failed login. */
    static final String LAST_USERNAME = "lastUsername";

    @GetMapping("/login")
    String login(HttpSession session, Model model) {
        model.addAttribute("username", session.getAttribute(LAST_USERNAME));
        session.removeAttribute(LAST_USERNAME);
        return "login";
    }
}
