package com.lms.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.config.PasswordResetProperties;
import com.lms.auth.email.EmailService;
import com.lms.auth.entity.PasswordResetCode;
import com.lms.auth.entity.Role;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import com.lms.auth.repository.PasswordResetCodeRepository;
import com.lms.auth.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetCodeRepository resetCodeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        PasswordResetProperties props = new PasswordResetProperties();
        props.setCodeExpireMinutes(15);
        props.setMaxAttempts(5);
        service = new PasswordResetService(userRepository, resetCodeRepository, passwordEncoder, emailService, props);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static User makeUser(long id, String email) {
        Role role = new Role();
        role.setName(RoleName.STUDENT);
        User u = new User();
        u.setEmail(email);
        u.setFirstName("Alice");
        u.setLastName("Smith");
        u.setPasswordHash("{bcrypt}hash");
        u.setRole(role);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    private static PasswordResetCode makeCode(Long userId, boolean expired, int attempts) {
        PasswordResetCode c = new PasswordResetCode();
        c.setUserId(userId);
        c.setCodeHash("{bcrypt}code");
        c.setAttempts(attempts);
        c.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        c.setExpiresAt(expired
                ? Instant.now().minus(1, ChronoUnit.MINUTES)
                : Instant.now().plus(10, ChronoUnit.MINUTES));
        return c;
    }

    // ── requestReset ─────────────────────────────────────────────────────────

    @Test
    void requestReset_unknownEmail_returnsQuietly() {
        when(userRepository.findByEmailIgnoreCase("x@x.x")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.requestReset("x@x.x"));
        verify(resetCodeRepository, never()).save(any());
        verify(emailService, never()).sendResetCode(anyString(), anyString(), anyString());
    }

    @Test
    void requestReset_noPriorCode_savesAndSendsEmail() {
        User user = makeUser(1L, "a@b.c");
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));
        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}code");
        when(resetCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("a@b.c");

        verify(resetCodeRepository).save(any(PasswordResetCode.class));
        verify(emailService).sendResetCode(eq("a@b.c"), anyString(), eq("Alice Smith"));
    }

    @Test
    void requestReset_recentCodeExists_throws429() {
        User user = makeUser(1L, "a@b.c");
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));

        PasswordResetCode recent = new PasswordResetCode();
        recent.setUserId(1L);
        recent.setCodeHash("{bcrypt}code");
        recent.setCreatedAt(Instant.now().minus(30, ChronoUnit.SECONDS)); // 30s ago — within 1min window
        recent.setExpiresAt(Instant.now().plus(14, ChronoUnit.MINUTES));
        recent.setAttempts(0);

        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(recent));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestReset("a@b.c"));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    void requestReset_oldCodeExists_allowsNewCode() {
        User user = makeUser(1L, "a@b.c");
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));

        PasswordResetCode old = new PasswordResetCode();
        old.setUserId(1L);
        old.setCodeHash("{bcrypt}old");
        old.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES)); // 5min ago — past 1min window
        old.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        old.setAttempts(0);

        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(old));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}newcode");
        when(resetCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.requestReset("a@b.c"));
        verify(emailService).sendResetCode(anyString(), anyString(), anyString());
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_unknownEmail_throws400() {
        when(userRepository.findByEmailIgnoreCase("x@x.x")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("x@x.x", "123456", "newPass1!"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void resetPassword_noCode_throws400() {
        User user = makeUser(1L, "a@b.c");
        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));
        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("a@b.c", "123456", "newPass1!"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void resetPassword_tooManyAttempts_throws429() {
        User user = makeUser(1L, "a@b.c");
        PasswordResetCode code = makeCode(1L, false, 5); // 5 == maxAttempts

        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));
        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(code));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("a@b.c", "123456", "newPass1!"));
        assertEquals(429, ex.getStatusCode().value());
    }

    @Test
    void resetPassword_expiredCode_throws400() {
        User user = makeUser(1L, "a@b.c");
        PasswordResetCode code = makeCode(1L, true, 0); // expired

        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));
        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(code));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("a@b.c", "123456", "newPass1!"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void resetPassword_wrongCode_incrementsAttemptsAndThrows400() {
        User user = makeUser(1L, "a@b.c");
        PasswordResetCode code = makeCode(1L, false, 0);

        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));
        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("wrong", code.getCodeHash())).thenReturn(false);
        when(resetCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("a@b.c", "wrong", "newPass1!"));
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(1, code.getAttempts());
        verify(resetCodeRepository).save(code);
    }

    @Test
    void resetPassword_correctCode_updatesPasswordAndMarksUsed() {
        User user = makeUser(1L, "a@b.c");
        PasswordResetCode code = makeCode(1L, false, 0);

        when(userRepository.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(user));
        when(resetCodeRepository.findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("123456", code.getCodeHash())).thenReturn(true);
        when(passwordEncoder.encode("newPass1!")).thenReturn("{bcrypt}newhash");
        when(resetCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword("a@b.c", "123456", "newPass1!");

        assertNotNull(code.getUsedAt());
        assertEquals("{bcrypt}newhash", user.getPasswordHash());
        verify(userRepository).save(user);
    }
}
