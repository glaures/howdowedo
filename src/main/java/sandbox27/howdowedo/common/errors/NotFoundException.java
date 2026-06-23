package sandbox27.howdowedo.common.errors;

/**
 * Thrown when a referenced entity does not exist.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
