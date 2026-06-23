package sandbox27.howdowedo.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, String> {

    long countBySurveyId(Long surveyId);

    List<SurveyResponse> findBySurveyId(Long surveyId);
}
