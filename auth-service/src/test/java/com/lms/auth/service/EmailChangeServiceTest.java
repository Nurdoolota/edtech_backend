package com.lms.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lms.auth.email.EmailService;
import com.lms.auth.entity.PendingEmailChange;
import com.lms.auth.entity.Role;
import com.lms.auth.entity.RoleName;
import com.lms.auth.entity.User;
import com.lms.auth.repository.PendingEmailChangeRepository;
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
class EmailChangeServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PendingEmailChangeRepository pendingEmailChangeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private EmailChangeService service;

    @BeforeEach
    void setUp() {
        service = new EmailChangeService(
                userRepository, pendingEmailChangeRepository, passwordEncoder, emailService);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static User makeUser(long id, String email) {
        Role role = new Role();
        role.setName(RoleName.STUDENT);
        User u = new User();
        u.setEmail(email);
        u.setFirstName("Jane");
        u.setLastName("Doe");
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

    private static PendingEmailChange makePending(Long userId, String newEmail, boolean expired) {
        PendingEmailChange p = new PendingEmailChange();
        p.setUserId(userId);
        p.setNewEmail(newEmail);
        p.setCodeHash("{bcrypt}code");
        p.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        p.setExpiresAt(expired
                ? Instant.now().minus(1, ChronoUnit.MINUTES)
                : Instant.now().plus(10, ChronoUnit.MINUTES));
        return p;
    }

    // ── requestChange ─────────────────────────────────────────────────────────

    @Test
    void requestChange_userNotFound_throws404() {
        when(userRepository.findByIdWithRole(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestChange(99L, "new@example.com", "pass"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void requestChange_wrongPassword_throws401() {
        User user = makeUser(1L, "old@example.com");
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", user.getPasswordHash())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestChange(1L, "new@example.com", "wrongpass"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void requestChange_emailAlreadyTaken_throws409() {
        User user = makeUser(1L, "old@example.com");
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpass", user.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestChange(1L, "taken@example.com", "correctpass"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void requestChange_valid_deletesOldAndSavesAndSendsEmail() {
        User user = makeUser(1L, "old@example.com");
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctpass", user.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}codehash");
        when(pendingEmailChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestChange(1L, "new@example.com", "correctpass");

        verify(pendingEmailChangeRepository).deleteAllByUserId(1L);
        verify(pendingEmailChangeRepository).save(any(PendingEmailChange.class));
        verify(emailService).sendEmailChangeCode(eq("new@example.com"), anyString(), eq("Jane Doe"));
    }

    @Test
    void requestChange_normalizesEmailToLowercase() {
        User user = makeUser(1L, "old@example.com");
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", user.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}hash");
        when(pendingEmailChangeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestChange(1L, "NEW@EXAMPLE.COM", "pass");

        verify(emailService).sendEmailChangeCode(eq("new@example.com"), anyString(), anyString());
    }

    // ── confirmChange ─────────────────────────────────────────────────────────

    @Test
    void confirmChange_noPendingRecord_throws400() {
        when(pendingEmailChangeRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmChange(1L, "123456"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void confirmChange_expiredCode_throws400() {
        PendingEmailChange pending = makePending(1L, "new@example.com", true);
        when(pendingEmailChangeRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(pending));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmChange(1L, "123456"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void confirmChange_wrongCode_throws400() {
        PendingEmailChange pending = makePending(1L, "new@example.com", false);
        when(pendingEmailChangeRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("wrong", pending.getCodeHash())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmChange(1L, "wrong"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void confirmChange_emailConflict_throws409() {
        PendingEmailChange pending = makePending(1L, "taken@example.com", false);
        User user = makeUser(1L, "old@example.com");
        when(pendingEmailChangeRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("123456", pending.getCodeHash())).thenReturn(true);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmChange(1L, "123456"));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void confirmChange_valid_updatesEmailAndDeletesPending() {
        PendingEmailChange pending = makePending(1L, "new@example.com", false);
        User user = makeUser(1L, "old@example.com");
        when(pendingEmailChangeRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("123456", pending.getCodeHash())).thenReturn(true);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.confirmChange(1L, "123456");

        assertEquals("new@example.com", user.getEmail());
        verify(userRepository).save(user);
        verify(pendingEmailChangeRepository).delete(pending);
        verify(emailService, never()).sendEmailChangeCode(anyString(), anyString(), anyString());
    }
}
