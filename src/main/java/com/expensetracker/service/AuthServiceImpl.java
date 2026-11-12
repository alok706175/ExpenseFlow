package com.expensetracker.service;

import com.expensetracker.dto.AuthRequestDTO;
import com.expensetracker.dto.AuthResponseDTO;
import com.expensetracker.dto.RegisterRequestDTO;
import com.expensetracker.dto.UserProfileDTO;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.CustomUserDetails;
import com.expensetracker.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@SuppressWarnings("null")
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public AuthResponseDTO register(RegisterRequestDTO registerDTO) {
        String email = registerDTO.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account already exists with email: " + email);
        }

        User user = new User(
                registerDTO.getName().trim(),
                email,
                passwordEncoder.encode(registerDTO.getPassword()),
                registerDTO.getCurrency()
        );

        User savedUser = userRepository.save(user);
        log.info("Registered new user with id: {} and email: {}", savedUser.getId(), savedUser.getEmail());

        String token = jwtUtil.generateToken(savedUser);
        return new AuthResponseDTO(token, savedUser.getId(), savedUser.getName(), savedUser.getEmail(), savedUser.getCurrency());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthRequestDTO loginDTO) {
        String email = loginDTO.getEmail().trim().toLowerCase();

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, loginDTO.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            log.info("User logged in successfully: {}", email);
            String token = jwtUtil.generateToken(user);
            return new AuthResponseDTO(token, user.getId(), user.getName(), user.getEmail(), user.getCurrency());
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for email: {}", email);
            throw new BadCredentialsException("Invalid email or password");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return new UserProfileDTO(user.getId(), user.getName(), user.getEmail(), user.getCurrency());
    }

    @Override
    public UserProfileDTO updateProfile(Long userId, UserProfileDTO profileDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setName(profileDTO.getName().trim());
        if (profileDTO.getCurrency() != null && !profileDTO.getCurrency().trim().isEmpty()) {
            user.setCurrency(profileDTO.getCurrency().trim().toUpperCase());
        }

        User updated = userRepository.save(user);
        log.info("Updated profile for user id: {}", userId);
        return new UserProfileDTO(updated.getId(), updated.getName(), updated.getEmail(), updated.getCurrency());
    }

    @Override
    @Transactional(readOnly = true)
    public User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new IllegalArgumentException("No authenticated user found in session");
        }
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userDetails.getId()));
    }
}
