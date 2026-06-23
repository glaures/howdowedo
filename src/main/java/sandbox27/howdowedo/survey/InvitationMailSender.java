package sandbox27.howdowedo.survey;

/**
 * Sends a survey invitation containing the personal response link. The email address is passed in
 * only to deliver the message and is never persisted - this is the boundary that keeps the
 * email&rarr;code mapping out of the system.
 */
public interface InvitationMailSender {

    void sendInvitation(String email, String surveyTitle, String responseUrl);
}
