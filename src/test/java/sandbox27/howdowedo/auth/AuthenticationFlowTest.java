package sandbox27.howdowedo.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPublicAndListsConfiguredProvider() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Microsoft 365")))
                .andExpect(content().string(containsString("/oauth2/authorization/azure")));
    }

    @Test
    void staticBrandingAssetsArePublic() throws Exception {
        // The login page embeds these; if they were not public, Spring would save the asset request
        // and redirect the user to it (e.g. /logo.png) after login instead of to their destination.
        mockMvc.perform(get("/logo.png")).andExpect(status().isOk());
        mockMvc.perform(get("/css/theme.css")).andExpect(status().isOk());
    }

    @Test
    void protectedPageRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void oauthCallbackIsMountedAtCustomPath() throws Exception {
        // Hitting the callback without a valid authorization request fails authentication and is
        // redirected to the login page - proving the OAuth2 login filter listens on /auth/callback.
        mockMvc.perform(get("/auth/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void plainUserSeesLimitedHome() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                        .attributes(attrs -> attrs.put("name", "Ada Lovelace"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ada Lovelace")));
    }

    @Test
    void surveyWorkerIsRedirectedToSurveys() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()
                        .authorities(new SimpleGrantedAuthority("ROLE_SURVEY_MANAGER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/surveys"));
    }

    @Test
    void administratorOnlyIsRedirectedToAdminArea() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }
}
