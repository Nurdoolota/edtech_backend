package com.lms.auth.email;

import com.lms.auth.config.EmailProperties;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class SendGridEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailService.class);

    private static final String SUBJECT_RESET_CODE = "Password Reset Code — LMS English";
    private static final String SUBJECT_PASSWORD_CHANGED = "Your Password Has Been Changed — LMS English";
    private static final String SUBJECT_EMAIL_CHANGE_CODE = "Confirm Your New Email — LMS English";

    private final EmailTemplateRenderer renderer;
    private final EmailProperties props;

    public SendGridEmailService(EmailTemplateRenderer renderer, EmailProperties props) {
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
        String htmlBody = renderer.renderHtml(templateName, vars);

        Email from = new Email(props.getFrom());
        Email toEmail = new Email(to);
        Content content = new Content("text/html", htmlBody);
        Mail mail = new Mail(from, subject, toEmail, content);

        SendGrid sg = new SendGrid(props.getSendgridApiKey());
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid error: status={} body={}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("SendGrid returned status " + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("Failed to send email via SendGrid to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email via SendGrid", e);
        }
    }
}
