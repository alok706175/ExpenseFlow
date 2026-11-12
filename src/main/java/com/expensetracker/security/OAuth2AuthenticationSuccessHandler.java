package com.expensetracker.security;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String sub = oAuth2User.getAttribute("sub");

        if (email == null) {
            log.error("OAuth2 user does not provide an email attribute");
            response.sendRedirect("/index.html?error=" + URLEncoder.encode("Email not provided by Google account", StandardCharsets.UTF_8));
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        // Find existing user or auto-register new Google user
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).map(existingUser -> {
            if ("LOCAL".equals(existingUser.getAuthProvider())) {
                existingUser.setAuthProvider("GOOGLE");
            }
            if (existingUser.getProviderId() == null && sub != null) {
                existingUser.setProviderId(sub);
            }
            return userRepository.save(existingUser);
        }).orElseGet(() -> {
            log.info("Auto-registering new Google OAuth2 user: {}", normalizedEmail);
            String displayName = (name != null && !name.trim().isEmpty()) ? name.trim() : normalizedEmail.split("@")[0];
            User newUser = new User(displayName, normalizedEmail, "GOOGLE", sub, "INR");
            return userRepository.save(newUser);
        });

        // Issue JWT token for the authenticated user
        String token = jwtUtil.generateToken(user);
        log.info("Google OAuth2 login successful for user id: {}, email: {}", user.getId(), user.getEmail());

        // Redirect back to Frontend Single Page Application with JWT payload in URL query
        String targetUrl = UriComponentsBuilder.fromUriString("/index.html")
                .queryParam("token", token)
                .queryParam("userId", user.getId())
                .queryParam("name", user.getName())
                .queryParam("email", user.getEmail())
                .queryParam("currency", user.getCurrency())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

