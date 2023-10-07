package com.security.jwttest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.jwttest.dto.request.AuthRequest;
import com.security.jwttest.dto.request.RegisterRequest;
import com.security.jwttest.dto.response.AuthResponse;
import com.security.jwttest.model.entity.Token;
import com.security.jwttest.model.entity.TokenType;
import com.security.jwttest.model.entity.User;
import com.security.jwttest.model.repo.TokenRepo;
import com.security.jwttest.model.repo.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenRepo tokenRepo;

    public AuthResponse register(RegisterRequest request) {
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        userRepo.save(user);
        return getAuthResponse(user);
    }

    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return getAuthResponse(user);
    }

    public void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String refreshToken;
        final String userEmail;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        refreshToken = authHeader.substring(7);

        Token token = tokenRepo.findByToken(refreshToken).orElseThrow(RuntimeException::new);
        if(!token.getTokenType().equals(TokenType.REFRESH_TOKEN)){
            throw new RuntimeException();
        }

        userEmail = jwtService.extractUsername(refreshToken);
        if (userEmail != null) {
            User user = this.userRepo.findByEmail(userEmail)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
            if (jwtService.isTokenValid(refreshToken, user)) {
                String accessToken = jwtService.generateToken(user);
                String newRefreshToken = jwtService.generateRefreshToken(user);

                revokeAllUserTokens(user);
                saveUserToken(user, accessToken, TokenType.NORMAL_TOKEN);
                saveUserToken(user, newRefreshToken, TokenType.REFRESH_TOKEN);

                AuthResponse authResponse = AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(newRefreshToken)
                        .build();

                ObjectMapper objectMapper = new ObjectMapper();
                String jsonResponse = objectMapper.writeValueAsString(authResponse);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getOutputStream().write(jsonResponse.getBytes("UTF-8"));
            }
        }
    }

    private AuthResponse getAuthResponse(User user) {
        String jwtToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user, jwtToken, TokenType.NORMAL_TOKEN);
        saveUserToken(user, refreshToken, TokenType.REFRESH_TOKEN);
        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .build();
    }

    private void saveUserToken(User user, String jwtToken, TokenType tokenType) {
        Token token = Token.builder()
                .userId(user.getId())
                .token(jwtToken)
                .tokenType(tokenType)
                .expired(false)
                .revoked(false)
                .build();
        tokenRepo.save(token);
    }

    private void revokeAllUserTokens(User user) {
        List<Token> expiredTokens = tokenRepo.findAllValidTokenByUser(user.getId());
        if (!expiredTokens.isEmpty()) {
            tokenRepo.deleteAll(expiredTokens);
        }
    }
}
