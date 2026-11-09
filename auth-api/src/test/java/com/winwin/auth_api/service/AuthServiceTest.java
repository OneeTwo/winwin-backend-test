package com.winwin.auth_api.service;

import com.winwin.auth_api.dto.LoginRequest;
import com.winwin.auth_api.dto.LoginResponse;
import com.winwin.auth_api.dto.RegisterRequest;
import com.winwin.auth_api.entity.User;
import com.winwin.auth_api.exception.EmailAlreadyExistsException;
import com.winwin.auth_api.exception.InvalidCredentialsException;
import com.winwin.auth_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.SQLException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_savesNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(new RegisterRequest("  Test@Example.com ", "password123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("test@example.com", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already registered");

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void register_translatesConcurrentDuplicateEmailAfterRollback() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false, true);
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(
                new DataIntegrityViolationException("private SQL details", new SQLException("duplicate", "23505")));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("test@example.com", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already registered");
    }

    @Test
    void register_preservesUnrelatedConstraintFailures() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "private SQL details", new SQLException("not null", "23502"));
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("test@example.com", "password123")))
                .isSameAs(failure);
    }

    @Test
    void register_doesNotClassifyOtherUniqueConstraintsAsDuplicateEmail() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "private SQL details", new SQLException("other unique constraint", "23505"));
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(failure);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("test@example.com", "password123")))
                .isSameAs(failure);
    }

    @Test
    void login_returnsTokenForValidCredentials() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(userId)).thenReturn("signed.jwt.token");

        LoginResponse response = authService.login(new LoginRequest("  Test@Example.com ", "password123"));

        assertThat(response.token()).isEqualTo("signed.jwt.token");
        verify(jwtService).generateToken(userId);
    }

    @Test
    void login_throwsForIncorrectPassword() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_throwsForUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtService, never()).generateToken(any());
    }
}
