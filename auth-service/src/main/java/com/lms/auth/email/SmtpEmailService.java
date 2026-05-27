package com.lms.auth.email;

import com.lms.auth.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Map;

public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private static final String SUBJECT_RESET_CODE = "Password Reset Code — LMS English";
    private static final String SUBJECT_PASSWORD_CHANGED = "Your Password Has Been Changed — LMS English";
    private static final String SUBJECT_EMAIL_CHANGE_CODE = "Confirm Your New Email — LMS English";

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer renderer;
    private final EmailProperties props;

    public SmtpEmailService(JavaMailSender mailSender, EmailTemplateRenderer renderer, EmailProperties props) {
        this.mailSender = mailSender;
        this.renderer = renderer;
        this.props = props;
    }

    @Override
    public void sendResetCode(String to, String code, String displayName) {
        Map<String, Object> vars = Map.of(
                "displayName", displayName,
                "code", code,
                "expiresMinutes", 15,
                "frontendUrl", props.getFrontendUrl()
        );
        send(to, SUBJECT_RESET_CODE, "reset_code", vars);
    }

    @Override
    public void sendPasswordChangedNotice(String to, String displayName) {
        Map<String, Object> vars = Map.of(
                "displayName", displayName,
                "frontendUrl", props.getFrontendUrl()
        );
        send(to, SUBJECT_PASSWORD_CHANGED, "password_changed", vars);
    }

    @Override
    public void sendEmailChangeCode(String to, String code, String displayName) {
        Map<String, Object> vars = Map.of(
                "displayName", displayName,
                "code", code,
                "newEmail", to,
                "expiresMinutes", 15,
                "frontendUrl", props.getFrontendUrl()
        );
        send(to, SUBJECT_EMAIL_CHANGE_CODE, "email_change_code", vars);
    }

    private void send(String to, String subject, String templateName, Map<String, Object> vars) {
        try {
            String htmlBody = renderer.renderHtml(templateName, vars);
            String textBody = renderer.renderText(templateName, vars);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
