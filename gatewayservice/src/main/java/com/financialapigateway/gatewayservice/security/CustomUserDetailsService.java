package com.financialapigateway.gatewayservice.security;

import com.financialapigateway.gatewayservice.entity.UserEntity;
import com.financialapigateway.gatewayservice.exceptions.UserNotFoundException;
import com.financialapigateway.gatewayservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements ReactiveUserDetailsService {
    private final UserRepository userRepository;

    @Autowired
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String email) throws UserNotFoundException {
        return Mono.fromCallable(() -> {
            UserEntity user = this.userRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));

            return (UserDetails) new User(
                    user.getEmail(),
                    user.getPassword(),
                    Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                    )
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
