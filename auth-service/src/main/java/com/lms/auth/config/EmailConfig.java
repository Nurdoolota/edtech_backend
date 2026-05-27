package com.lms.auth.config;

import com.lms.auth.email.ConsoleEmailService;
import com.lms.auth.email.EmailService;
import com.lms.auth.email.EmailTemplateRenderer;
import com.lms.auth.email.SendGridEmailService;
import com.lms.auth.email.SmtpEmailService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Configuration
public class EmailConfig {

    @Bean
    public EmailTemplateRenderer emailTemplateRenderer() {
        TemplateEngine htmlEngine = buildEngine(TemplateMode.HTML, ".html");
        TemplateEngine textEngine = buildEngine(TemplateMode.TEXT, ".txt");
        return new EmailTemplateRenderer(htmlEngine, textEngine);
    }

    private TemplateEngine buildEngine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        resolver.setOrder(1);

        TemplateEngine engine = new TemplateEngine();
        engine.addTemplateResolver(resolver);
        return engine;
    }

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "console", matchIfMissing = true)
    public EmailService consoleEmailService(EmailTemplateRenderer emailTemplateRenderer,
                                            EmailProperties props) {
        return new ConsoleEmailService(emailTemplateRenderer, props);
    }

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "smtp")
    public EmailService smtpEmailService(JavaMailSender mailSender,
                                         EmailTemplateRenderer emailTemplateRenderer,
                                         EmailProperties props) {
        return new SmtpEmailService(mailSender, emailTemplateRenderer, props);
    }

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "sendgrid")
    public EmailService sendGridEmailService(EmailTemplateRenderer emailTemplateRenderer,
                                             EmailProperties props) {
        return new SendGridEmailService(emailTemplateRenderer, props);
    }
}
