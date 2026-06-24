package sandbox27.howdowedo.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Default {@link InvitationMailSender} backed by SMTP (via {@link JavaMailSender}). Sends a plain
 * text invitation; the email is used only to deliver and is not stored anywhere.
 */
@Component
public class MailInvitationSender implements InvitationMailSender {

    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final String from;

    public MailInvitationSender(JavaMailSender mailSender,
                                MessageSource messages,
                                @Value("${app.mail.from:no-reply@howdowedo.local}") String from) {
        this.mailSender = mailSender;
        this.messages = messages;
        this.from = from;
    }

    @Override
    public void sendInvitation(String email, String surveyTitle, String responseUrl) {
        Locale locale = LocaleContextHolder.getLocale();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(messages.getMessage("mail.invitation.subject",
                new Object[]{surveyTitle}, locale));
        message.setText(messages.getMessage("mail.invitation.body",
                new Object[]{surveyTitle, responseUrl}, locale));
        mailSender.send(message);
    }
}
