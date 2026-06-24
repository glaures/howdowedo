package sandbox27.howdowedo.common.errors;

/**
 * Thrown when an operation is invalid for a survey's current state - e.g. submitting to a survey
 * that is not open, submitting twice, or requesting results before the k-anonymity threshold is met.
 * The message is an i18n key (see {@link LocalizedException}).
 */
public class SurveyStateException extends LocalizedException {

    public SurveyStateException(String messageKey, Object... args) {
        super(messageKey, args);
    }
}
