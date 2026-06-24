package sandbox27.howdowedo.common.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes request-scoped values needed by the shared layout to every view. {@code currentPath} lets
 * the language switcher link back to the same page (Thymeleaf 3.1 no longer exposes {@code #request}).
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
