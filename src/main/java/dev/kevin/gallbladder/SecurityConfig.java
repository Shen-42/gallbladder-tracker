package dev.kevin.gallbladder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

/** Every page needs the one account, whose name and password come from APP_USERNAME and APP_PASSWORD. */
@Configuration
public class SecurityConfig {

    private static final int REMEMBER_DAYS = 30;

    private final String username;
    private final String password;

    public SecurityConfig(@Value("${app.username:me}") String username, @Value("${app.password:}") String password) {
        if (password.isBlank()) {
            throw new IllegalStateException("Set APP_PASSWORD to the password for logging in to the app.");
        }
        this.username = username;
        this.password = password;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/app.css", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login"))
                // Signed with the password, so changing APP_PASSWORD logs out every device.
                .rememberMe(r -> r.key(password).rememberMeParameter("remember")
                        .tokenValiditySeconds((int) Duration.ofDays(REMEMBER_DAYS).toSeconds()))
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(encoder.encode(password)).roles("USER").build());
    }
}
