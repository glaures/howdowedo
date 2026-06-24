package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public, unauthenticated answering flow: a participant follows their link, is guided section by
 * section, can pause and resume, and finishes anonymously.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SurveyParticipationFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SurveyResponseRepository responses;

    @MockitoBean
    private InvitationMailSender mailSender;

    private Long surveyId;
    private Long sectionA;
    private Long sectionB;
    private Long questionA;
    private Long questionB;
    private String code;

    private void buildOpenSurveyWithCode() {
        Survey survey = surveyService.createSurvey(1L,
                new CreateSurveyRequest("Climate", "How do we do?", 1, null));
        surveyId = survey.getId();
        Section a = surveyService.addSection(surveyId, "Part A");
        sectionA = a.getId();
        questionA = surveyService.addQuestion(surveyId, sectionA,
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No"))).getId();
        Section b = surveyService.addSection(surveyId, "Part B");
        sectionB = b.getId();
        questionB = surveyService.addQuestion(surveyId, sectionB,
                new NewQuestion("Comments", QuestionType.TEXT, List.of())).getId();
        surveyService.open(surveyId);

        surveyService.distributeInvitations(surveyId, List.of("p@example.com"));
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendInvitation(anyString(), anyString(), url.capture());
        String link = url.getValue();
        code = link.substring(link.indexOf("code=") + "code=".length());
    }

    @Test
    void participantIsGuidedThroughSectionsSavesEachAndFinishesAnonymously() throws Exception {
        buildOpenSurveyWithCode();

        // No login required; the first section is shown.
        mockMvc.perform(get("/s/{id}", surveyId).param("code", code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Part A")))
                .andExpect(content().string(containsString("Happy?")));

        // Answer section A and move on -> the answer is saved as in-progress (code not yet used).
        mockMvc.perform(post("/s/{id}/section", surveyId).with(csrf())
                        .param("code", code).param("sectionId", String.valueOf(sectionA))
                        .param("sectionIndex", "0").param("direction", "next")
                        .param("answer_" + questionA, "Yes"))
                .andExpect(status().is3xxRedirection());

        String hash = AccessCodes.hash(code);
        SurveyResponse inProgress = responses.findBySurveyIdAndInProgressCodeHash(surveyId, hash).orElseThrow();
        assertThat(inProgress.isCompleted()).isFalse();
        assertThat(inProgress.getAnswers()).extracting(Answer::getValue).containsExactly("Yes");
        assertThat(surveyService.turnout(surveyId)).isEqualTo(new SurveyTurnout(1, 0));

        // Section B is reachable and the saved answer for A is remembered on a later visit.
        mockMvc.perform(get("/s/{id}", surveyId).param("code", code).param("section", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Part B")))
                .andExpect(content().string(containsString("Comments")));

        // Finish from the last section.
        mockMvc.perform(post("/s/{id}/section", surveyId).with(csrf())
                        .param("code", code).param("sectionId", String.valueOf(sectionB))
                        .param("sectionIndex", "1").param("direction", "finish")
                        .param("answer_" + questionB, "Great place"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/s/" + surveyId + "/done"));

        assertThat(surveyService.turnout(surveyId)).isEqualTo(new SurveyTurnout(1, 1));
        // Completed response is severed from the code and counts towards results.
        assertThat(responses.findBySurveyIdAndInProgressCodeHash(surveyId, hash)).isEmpty();
        assertThat(surveyService.results(surveyId).responseCount()).isEqualTo(1);
    }

    @Test
    void aCommentPostedAlongsideAnAnswerIsStored() throws Exception {
        buildOpenSurveyWithCode();

        mockMvc.perform(post("/s/{id}/section", surveyId).with(csrf())
                        .param("code", code).param("sectionId", String.valueOf(sectionA))
                        .param("sectionIndex", "0").param("direction", "next")
                        .param("answer_" + questionA, "Yes")
                        .param("comment_" + questionA, "Could be better"))
                .andExpect(status().is3xxRedirection());

        assertThat(surveyService.savedComments(surveyId, code)).containsEntry(questionA, "Could be better");
    }

    @Test
    void aUsedLinkShowsAFriendlyMessageInsteadOfBouncingToLogin() throws Exception {
        buildOpenSurveyWithCode();
        surveyService.completeParticipation(surveyId, code); // consume the code

        mockMvc.perform(get("/s/{id}", surveyId).param("code", code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already been used")));
    }

    @Test
    void anUnknownCodeShowsAFriendlyMessage() throws Exception {
        buildOpenSurveyWithCode();

        mockMvc.perform(get("/s/{id}", surveyId).param("code", "not-a-real-code"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid")));
    }
}
