package sandbox27.howdowedo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import sandbox27.howdowedo.auth.service.ProvisioningOidcUserService;
import sandbox27.howdowedo.user.Role;

/**
 * Central security configuration.
 *
 * <p>Authentication is delegated entirely to OAuth2 / OIDC identity providers. Which providers are
 * available is driven purely by the {@code spring.security.oauth2.client.registration.*} entries in
 * configuration - no code change is required to add another provider (e.g. Google). The custom login
 * page ({@code /login}) renders one button per registered provider.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String LOGIN_PAGE = "/login";

    /** Where identity providers redirect back to after authentication. Must match the configured
     *  {@code redirect-uri} ({baseUrl}/auth/callback). A single fixed path serves all providers -
     *  Spring resolves which registration to use from the OAuth2 {@code state} parameter. */
    static final String CALLBACK_PATH = "/auth/callback";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ProvisioningOidcUserService oidcUserService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN_PAGE, "/css/**", "/js/**", "/images/**",
                                "/favicon.ico", "/logo.png").permitAll()
                        // Public survey-answering flow: authorised by the one-time code, not a login.
                        .requestMatchers("/s/**").permitAll()
                        .requestMatchers("/admin/**").hasRole(Role.ADMINISTRATOR.name())
                        // Creating surveys and managing scales stays a Survey Manager privilege ...
                        .requestMatchers("/surveys/new").hasRole(Role.SURVEY_MANAGER.name())
                        .requestMatchers(HttpMethod.POST, "/surveys").hasRole(Role.SURVEY_MANAGER.name())
                        .requestMatchers("/scales/**").hasRole(Role.SURVEY_MANAGER.name())
                        // ... but an individual survey is reachable by anyone it was shared with;
                        // the per-survey permission check in the controller decides what they may do.
                        .requestMatchers("/surveys", "/surveys/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .loginPage(LOGIN_PAGE)
                        .redirectionEndpoint(redirect -> redirect.baseUri(CALLBACK_PATH))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)))
                .logout(logout -> logout
                        .logoutSuccessUrl(LOGIN_PAGE + "?logout")
                        .permitAll());
        return http.build();
    }
}
