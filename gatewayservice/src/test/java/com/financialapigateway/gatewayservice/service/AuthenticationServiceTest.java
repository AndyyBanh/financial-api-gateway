package com.financialapigateway.gatewayservice.service;

import com.financialapigateway.gatewayservice.dto.LoginUserDto;
import com.financialapigateway.gatewayservice.dto.RegisterUserDto;
import com.financialapigateway.gatewayservice.entity.UserEntity;
import com.financialapigateway.gatewayservice.exceptions.UserAlreadyExistsException;
import com.financialapigateway.gatewayservice.exceptions.UserNotFoundException;
import com.financialapigateway.gatewayservice.repository.UserRepository;
import com.financialapigateway.gatewayservice.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReactiveAuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;


    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        RegisterUserDto input = new RegisterUserDto();
        input.setEmail("alice@test.com");
        input.setPassword("123456");

        when(this.userRepository.existsByEmail(input.getEmail())).thenReturn(true);

        StepVerifier.create(authenticationService.register(input))
                .expectError(UserAlreadyExistsException.class)
                .verify();

        verify(this.userRepository, never()).save(any());
    }


    @Test
    void shouldCreateUser() {
        RegisterUserDto input = new RegisterUserDto();
        input.setEmail("alice@test.com");
        input.setPassword("123456");

        when(this.userRepository.existsByEmail(input.getEmail())).thenReturn(false);
        when(this.passwordEncoder.encode(input.getPassword())).thenReturn("hashedPassword");
        when(this.userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(authenticationService.register(input))
                .assertNext(response -> {
                    assert response.getEmail().equals(input.getEmail());
                })
                .verifyComplete();
    }

    @Test
    void shouldLoginAndReturnToken() {
        LoginUserDto input = new LoginUserDto();
        input.setEmail("alice@test.com");
        input.setPassword("123456");

        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(input.getEmail());

        Authentication mockAuth = mock(Authentication.class);

        when(this.userRepository.findByEmail(input.getEmail())).thenReturn(Optional.of(userEntity));
        when(this.authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(Mono.just(mockAuth));
        when(this.jwtService.generateToken(mockAuth)).thenReturn("token");
        when(this.jwtService.getExpiration()).thenReturn(3600000L);

        StepVerifier.create(authenticationService.login(input))
                .assertNext(response -> {
                    assert response.getToken().equals("token");
                    assert response.getEmail().equals(input.getEmail());
                })
                .verifyComplete();
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        LoginUserDto input = new LoginUserDto();
        input.setEmail("alice@test.com");
        input.setPassword("123456");

        when(this.userRepository.findByEmail(input.getEmail())).thenReturn(Optional.empty());

        StepVerifier.create(this.authenticationService.login(input))
                .expectError(UserNotFoundException.class)
                .verify();

        verifyNoInteractions(this.jwtService, this.authenticationManager);
    }
}
