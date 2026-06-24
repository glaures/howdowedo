package sandbox27.howdowedo.home.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import sandbox27.howdowedo.user.Role;

/**
 * Landing page. Authenticated users are sent straight to the area most useful for their role:
 * survey workers (manager/analyst) to the surveys list, a pure administrator to user administration.
 * Users without any working role stay on a short informational page.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication, @AuthenticationPrincipal OAuth2User user, Model model) {
        if (hasRole(authentication, Role.SURVEY_MANAGER) || hasRole(authentication, Role.SURVEY_ANALYST)) {
            return "redirect:/surveys";
        }
        if (hasRole(authentication, Role.ADMINISTRATOR)) {
            return "redirect:/admin/users";
        }
        if (user != null) {
            String name = user.getAttribute("name");
            model.addAttribute("displayName", name != null ? name : user.getName());
        }
        return "index";
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role.authority()));
    }
}
