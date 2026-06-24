package sandbox27.howdowedo.common.errors;

/**
 * Base for exceptions whose message is an i18n message key (with optional arguments) rather than a
 * literal string. The key is resolved against the active locale by {@link GlobalExceptionHandler}.
 */
public class LocalizedException extends RuntimeException {

    private final transient Object[] args;

    public LocalizedException(String messageKey, Object... args) {
        super(messageKey);
        this.args = args;
    }

    /** The i18n message key (stored as the exception message). */
    public String getMessageKey() {
        return getMessage();
    }

    /** Arguments for placeholders ({@code {0}}, {@code {1}}, ...) in the resolved message. */
    public Object[] getArgs() {
        return args;
    }
}
