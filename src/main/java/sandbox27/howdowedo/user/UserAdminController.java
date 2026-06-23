package sandbox27.howdowedo.user;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.Set;

/**
 * Administrator-only screen for viewing users and assigning roles.
 * Access is restricted to {@link Role#ADMINISTRATOR} via the {@code /admin/**} security rule.
 */
@Controller
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/admin/users")
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("allRoles", Role.values());
        return "admin/users";
    }

    @PostMapping("/admin/users/{id}/roles")
    public String updateRoles(@PathVariable Long id,
                              @RequestParam(name = "roles", required = false) Set<Role> roles) {
        userService.setRoles(id, roles != null ? roles : Collections.emptySet());
        return "redirect:/admin/users";
    }
}
