package sandbox27.howdowedo.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, String> {

    long countBySurveyId(Long surveyId);

    List<SurveyResponse> findBySurveyId(Long surveyId);

    long countBySurveyIdAndCompletedTrue(Long surveyId);

    List<SurveyResponse> findBySurveyIdAndCompletedTrue(Long surveyId);

    Optional<SurveyResponse> findBySurveyIdAndInProgressCodeHash(Long surveyId, String inProgressCodeHash);
}
