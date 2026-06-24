package sandbox27.howdowedo.common.errors;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Application-wide exception handling.
 *
 * <ul>
 *   <li>{@link LocalizedException} (business/validation errors, e.g. "add a question first") are
 *       user mistakes: redirect back to the originating page and show the message inline.</li>
 *   <li>{@link NotFoundException} and anything unexpected render the generic error page.</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String ERROR_FLASH = "errorMessage";
    static final String SUBMITTED_FLASH = "submitted";

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    /** Resource genuinely missing (or not accessible): show the error page. */
    @ExceptionHandler(NotFoundException.class)
    public String handleNotFound(NotFoundException ex, Model model, Locale locale, HttpServletRequest request) {
        String message = messages.getMessage(ex.getMessageKey(), ex.getArgs(), locale);
        log.warn("{} {} -> not found: {} (key={})", request.getMethod(), request.getRequestURI(),
                message, ex.getMessageKey());
        model.addAttribute("message", message);
        return "error";
    }

    /** Business/validation error: redirect back to where the user was, with an inline message. */
    @ExceptionHandler(LocalizedException.class)
    public String handleLocalized(LocalizedException ex, Locale locale, HttpServletRequest request) {
        String message = messages.getMessage(ex.getMessageKey(), ex.getArgs(), locale);
        log.warn("{} {} -> {} (key={}, args={})", request.getMethod(), request.getRequestURI(),
                message, ex.getMessageKey(), Arrays.toString(ex.getArgs()));
        var flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put(ERROR_FLASH, message);
        // Carry the submitted form values back so the form can be repopulated after the redirect.
        flash.put(SUBMITTED_FLASH, request.getParameterMap());
        return "redirect:" + backTo(request);
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception ex, Model model, Locale locale, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
        model.addAttribute("message", messages.getMessage("error.unexpected", null, locale));
        return "error";
    }

    /** The same-origin path of the {@code Referer} the request came from, defaulting to the home page. */
    private String backTo(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() != null && !uri.getHost().equals(request.getServerName())) {
                return "/"; // ignore cross-origin referers (avoid open redirects)
            }
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                return "/";
            }
            return uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;
        } catch (IllegalArgumentException malformed) {
            return "/";
        }
    }
}
