package sandbox27.howdowedo.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyAccessCodeRepository extends JpaRepository<SurveyAccessCode, Long> {

    Optional<SurveyAccessCode> findBySurveyIdAndCodeHash(Long surveyId, String codeHash);

    long countBySurveyId(Long surveyId);

    long countBySurveyIdAndUsedTrue(Long surveyId);
}
