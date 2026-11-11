package com.expensetracker.service;

import com.expensetracker.dto.AuthRequestDTO;
import com.expensetracker.dto.AuthResponseDTO;
import com.expensetracker.dto.RegisterRequestDTO;
import com.expensetracker.dto.UserProfileDTO;
import com.expensetracker.entity.User;

public interface AuthService {

    AuthResponseDTO register(RegisterRequestDTO registerDTO);

    AuthResponseDTO login(AuthRequestDTO loginDTO);

    UserProfileDTO getProfile(Long userId);

    UserProfileDTO updateProfile(Long userId, UserProfileDTO profileDTO);

    User getCurrentAuthenticatedUser();
}
