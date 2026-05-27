package com.lms.auth.email;

public interface EmailService {

    void sendResetCode(String to, String code, String displayName);

    void sendPasswordChangedNotice(String to, String displayName);

    void sendEmailChangeCode(String to, String code, String displayName);
}
