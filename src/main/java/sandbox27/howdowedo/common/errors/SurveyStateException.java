package sandbox27.howdowedo.common.errors;

/**
 * Thrown when an operation is invalid for a survey's current state - e.g. submitting to a survey
 * that is not open, submitting twice, or requesting results before the k-anonymity threshold is met.
 */
public class SurveyStateException extends RuntimeException {

    public SurveyStateException(String message) {
        super(message);
    }
}
