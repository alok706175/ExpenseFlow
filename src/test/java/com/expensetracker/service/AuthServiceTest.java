package com.expensetracker.service;

import com.expensetracker.dto.AuthResponseDTO;
import com.expensetracker.dto.RegisterRequestDTO;
import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("Should successfully register a new user")
    void testRegisterUserSuccess() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Test User", "test@example.com", "SecretPass", "INR");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecretPass")).thenReturn("encodedSecretPass");

        User savedUser = new User("Test User", "test@example.com", "encodedSecretPass", "INR");
        savedUser.setId(5L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken(savedUser)).thenReturn("mocked.jwt.token");

        AuthResponseDTO response = authService.register(dto);

        assertNotNull(response);
        assertEquals(5L, response.getUserId());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("mocked.jwt.token", response.getToken());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject registration with duplicate email")
    void testRegisterDuplicateEmail() {
        RegisterRequestDTO dto = new RegisterRequestDTO("Test User", "duplicate@example.com", "SecretPass", "INR");

        when(userRepository.existsByEmailIgnoreCase("duplicate@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(dto));
        verify(userRepository, never()).save(any(User.class));
    }
}
