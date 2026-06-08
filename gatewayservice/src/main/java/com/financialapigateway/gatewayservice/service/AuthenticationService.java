package com.financialapigateway.gatewayservice.service;

import com.financialapigateway.gatewayservice.dto.LoginUserDto;
import com.financialapigateway.gatewayservice.dto.RegisterUserDto;
import com.financialapigateway.gatewayservice.entity.UserEntity;
import com.financialapigateway.gatewayservice.exceptions.UserAlreadyExistsException;
import com.financialapigateway.gatewayservice.exceptions.UserNotFoundException;
import com.financialapigateway.gatewayservice.repository.UserRepository;
import com.financialapigateway.gatewayservice.response.LoginUserResponse;
import com.financialapigateway.gatewayservice.response.RegisterUserResponse;
import com.financialapigateway.gatewayservice.security.JwtService;
import com.financialapigateway.gatewayservice.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AuthenticationService {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ReactiveAuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthenticationService(
            JwtService jwtService,
            UserRepository userRepository,
            ReactiveAuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<RegisterUserResponse> register(RegisterUserDto input) {
        return checkIfUserExist(input.getEmail())
                .then(Mono.defer(() -> createUser(input)))
                .map(userEntity -> new RegisterUserResponse(userEntity.getId(), userEntity.getEmail()));
    }


    // register helpers
    private Mono<Void> checkIfUserExist(String email) {
        return Mono.fromCallable(() -> {
            if (this.userRepository.existsByEmail(email)) {
                throw new UserAlreadyExistsException("User already exists");
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<UserEntity> createUser(RegisterUserDto input) {
        return Mono.fromCallable(() -> {
            UserEntity userEntity = new UserEntity();
            userEntity.setEmail(input.getEmail());
            userEntity.setPassword(passwordEncoder.encode(input.getPassword()));
            userEntity.setRole(Role.USER);
            this.userRepository.save(userEntity);
            return userEntity;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<LoginUserResponse> login(LoginUserDto input) {
        return findUserByEmail(input.getEmail())
                .flatMap(userEntity -> authenticate(input))
                .map(authentication -> buildLoginResponse(authentication, input.getEmail()));
    }

    // login helpers
    private Mono<UserEntity> findUserByEmail(String email) {
        return Mono.fromCallable(() -> {
            return this.userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Authentication> authenticate(LoginUserDto input) {
        return this.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getEmail(),
                        input.getPassword()
                )
        );
    }

    private LoginUserResponse buildLoginResponse(Authentication authentication, String email) {
        String token = this.jwtService.generateToken(authentication);
        return new LoginUserResponse(token, this.jwtService.getExpiration(), email);
    }
}
