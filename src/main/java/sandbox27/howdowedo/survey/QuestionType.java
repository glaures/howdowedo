package sandbox27.howdowedo.survey;

/**
 * Supported question types. Choice types use the question's option list; {@link #TEXT} and
 * {@link #SCALE} ignore it.
 */
public enum QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TEXT,
    SCALE
}
