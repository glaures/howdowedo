package sandbox27.howdowedo.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Default {@link InvitationMailSender} backed by SMTP (via {@link JavaMailSender}). Sends a plain
 * text invitation; the email is used only to deliver and is not stored anywhere.
 */
@Component
public class MailInvitationSender implements InvitationMailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public MailInvitationSender(JavaMailSender mailSender,
                                @Value("${app.mail.from:no-reply@howdowedo.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendInvitation(String email, String surveyTitle, String responseUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("You are invited to the survey: " + surveyTitle);
        message.setText("""
                Hello,

                you have been invited to take part in the anonymous survey "%s".

                Open this personal link to start - your answers cannot be traced back to you:
                %s

                The link works once. We never store who received which link, so we cannot send
                reminders - please respond at your convenience.

                Thank you!""".formatted(surveyTitle, responseUrl));
        mailSender.send(message);
    }
}
