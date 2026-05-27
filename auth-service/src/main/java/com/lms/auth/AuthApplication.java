package com.lms.auth;

import com.lms.auth.config.AvatarProperties;
import com.lms.auth.config.EmailProperties;
import com.lms.auth.config.JwtProperties;
import com.lms.auth.config.PasswordResetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AvatarProperties.class, EmailProperties.class, PasswordResetProperties.class})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
