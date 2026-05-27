package com.lms.auth.email;

import com.lms.auth.config.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    private static final String SUBJECT_RESET_CODE = "Password Reset Code — LMS English";
    private static final String SUBJECT_PASSWORD_CHANGED = "Your Password Has Been Changed — LMS English";
    private static final String SUBJECT_EMAIL_CHANGE_CODE = "Confirm Your New Email — LMS English";

    private final EmailTemplateRenderer renderer;
    private final EmailProperties props;

    public ConsoleEmailService(EmailTemplateRenderer renderer, EmailProperties props) {
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
        String textBody = renderer.renderText("reset_code", vars);
        log.info("[EMAIL] To={} Subject='{}'\n{}", to, SUBJECT_RESET_CODE, textBody);
    }

    @Override
    public void sendPasswordChangedNotice(String to, String displayName) {
        Map<String, Object> vars = Map.of(
                "displayName", displayName,
                "frontendUrl", props.getFrontendUrl()
        );
        String textBody = renderer.renderText("password_changed", vars);
        log.info("[EMAIL] To={} Subject='{}'\n{}", to, SUBJECT_PASSWORD_CHANGED, textBody);
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
        String textBody = renderer.renderText("email_change_code", vars);
        log.info("[EMAIL] To={} Subject='{}'\n{}", to, SUBJECT_EMAIL_CHANGE_CODE, textBody);
    }
}
