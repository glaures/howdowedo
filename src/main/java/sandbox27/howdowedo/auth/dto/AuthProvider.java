package sandbox27.howdowedo.auth.dto;

/**
 * A single OAuth2 login option offered to the user.
 *
 * @param registrationId the Spring registration id (e.g. {@code azure}, {@code google})
 * @param displayName    human readable provider name shown on the button
 * @param authorizationUrl the URL that starts the OAuth2 authorization flow
 */
public record AuthProvider(String registrationId, String displayName, String authorizationUrl) {
}
