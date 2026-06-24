package sandbox27.howdowedo.common.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import sandbox27.howdowedo.user.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternationalizationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MessageSource messages;

    @Test
    void englishIsTheDefaultLocale() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in to continue")))
                .andExpect(content().string(containsString("lang=\"en\"")));
    }

    @Test
    void langParameterSwitchesToGerman() throws Exception {
        mockMvc.perform(get("/login").param("lang", "de"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Zum Fortfahren anmelden")))
                .andExpect(content().string(containsString("lang=\"de\"")));
    }

    @Test
    void selectedLocaleIsRememberedForTheSession() throws Exception {
        MvcResult first = mockMvc.perform(get("/login").param("lang", "de"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) first.getRequest().getSession(false);

        // A later request without the parameter still renders German, proving the locale is stored.
        mockMvc.perform(get("/login").session(session))
                .andExpect(content().string(containsString("Zum Fortfahren anmelden")));
    }

    @Test
    void messageSourceResolvesBothLocales() {
        assertThat(messages.getMessage("login.subtitle", null, Locale.ENGLISH))
                .isEqualTo("Sign in to continue");
        assertThat(messages.getMessage("login.subtitle", null, Locale.GERMAN))
                .isEqualTo("Zum Fortfahren anmelden");
        assertThat(messages.getMessage("error.survey.notFound", new Object[]{42L}, Locale.GERMAN))
                .isEqualTo("Umfrage 42 nicht gefunden");
    }

    @Test
    void everyRoleHasALabelInBothLocales() {
        for (Role role : Role.values()) {
            String key = "role." + role.name();
            for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.GERMAN}) {
                assertThatCode(() -> messages.getMessage(key, null, locale))
                        .as("missing label %s for %s", key, locale)
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    void englishAndGermanBundlesDefineExactlyTheSameKeys() throws IOException {
        Properties en = load("/messages.properties");
        Properties de = load("/messages_de.properties");

        assertThat(de.stringPropertyNames())
                .as("German bundle must define the same keys as the English default")
                .containsExactlyInAnyOrderElementsOf(en.stringPropertyNames());
    }

    private Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertThat(in).as("bundle %s on classpath", resource).isNotNull();
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
