package sandbox27.howdowedo.auth.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import sandbox27.howdowedo.auth.dto.AuthProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the login page with one button per configured OAuth2 provider.
 *
 * <p>The list is derived at runtime from the {@link ClientRegistrationRepository}, so the page
 * automatically reflects whatever providers are configured - no template change needed to add one.
 */
@Controller
public class LoginController {

    private static final String AUTHORIZATION_BASE_URI =
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    public LoginController(ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.clientRegistrations = clientRegistrations;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("providers", availableProviders());
        return "login";
    }

    private List<AuthProvider> availableProviders() {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        if (!(repository instanceof Iterable<?> registrations)) {
            return List.of();
        }
        List<AuthProvider> providers = new ArrayList<>();
        for (Object entry : registrations) {
            if (entry instanceof ClientRegistration registration) {
                providers.add(new AuthProvider(
                        registration.getRegistrationId(),
                        displayName(registration),
                        AUTHORIZATION_BASE_URI + "/" + registration.getRegistrationId()));
            }
        }
        return providers;
    }

    private String displayName(ClientRegistration registration) {
        String name = registration.getClientName();
        // Spring defaults clientName to the registrationId when none is set.
        return (name == null || name.isBlank()) ? registration.getRegistrationId() : name;
    }
}
