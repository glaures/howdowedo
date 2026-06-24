package sandbox27.howdowedo.common.errors;

/**
 * Thrown when a referenced entity does not exist. The message is an i18n key (see
 * {@link LocalizedException}).
 */
public class NotFoundException extends LocalizedException {

    public NotFoundException(String messageKey, Object... args) {
        super(messageKey, args);
    }
}
