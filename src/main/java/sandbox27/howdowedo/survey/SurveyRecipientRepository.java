package sandbox27.howdowedo.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyRecipientRepository extends JpaRepository<SurveyRecipient, Long> {

    List<SurveyRecipient> findBySurveyIdOrderByEmailAsc(Long surveyId);

    List<SurveyRecipient> findBySurveyIdAndInvitedFalse(Long surveyId);

    boolean existsBySurveyIdAndEmailIgnoreCase(Long surveyId, String email);

    long countBySurveyId(Long surveyId);

    long countBySurveyIdAndInvitedTrue(Long surveyId);

    Optional<SurveyRecipient> findByIdAndSurveyId(Long id, Long surveyId);
}
