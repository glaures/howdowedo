package sandbox27.howdowedo.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import sandbox27.howdowedo.common.errors.NotFoundException;

/**
 * Resolves the locally provisioned {@link User} for the authenticated principal. The identity is
 * (provider, subject) where {@code provider} is the OAuth2 client registration id and
 * {@code subject} is the OIDC {@code sub} claim - consistent with how
 * {@code ProvisioningOidcUserService} stores users on login. The {@code sub} is read explicitly
 * from the {@link OidcUser}; {@code Authentication#getName()} cannot be used because it returns the
 * provider's configured name attribute (e.g. the display name), not the subject.
 */
@Component
public class CurrentUser {

    private final UserService userService;

    public CurrentUser(UserService userService) {
        this.userService = userService;
    }

    public User require(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new NotFoundException("error.user.notFound", "current");
        }
        String provider = token.getAuthorizedClientRegistrationId();
        OAuth2User principal = token.getPrincipal();
        String subject = principal instanceof OidcUser oidc ? oidc.getSubject() : principal.getName();
        return userService.findByIdentity(provider, subject)
                .orElseThrow(() -> new NotFoundException("error.user.notFound", subject));
    }
}
