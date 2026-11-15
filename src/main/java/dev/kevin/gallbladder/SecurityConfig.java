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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

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
    SecurityFilterChain filterChain(HttpSecurity http, UserDetailsService users) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login", "/app.css", "/error", "/manifest.webmanifest", "/icon-*.png",
                                "/apple-touch-icon.png").permitAll()
                        // Read-only pages for a doctor; ShareController checks the link's token on every request.
                        .requestMatchers("/share/**").permitAll()
                        .anyRequest().authenticated())
                // Kept in a cookie, not the server session, so forms still submit after the app restarts
                // (Render's free plan restarts it after 15 idle minutes and on every deploy).
                .csrf(c -> c.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .addFilterAfter(loadCsrfToken(), CsrfFilter.class)
                // Share links carry their secret in the URL: never send it on to other sites, never index it.
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow")))
                .formLogin(f -> f.loginPage("/login").failureHandler((request, response, e) -> {
                    // Keep the username so only the password needs typing again.
                    request.getSession().setAttribute(LoginController.LAST_USERNAME, request.getParameter("username"));
                    response.sendRedirect(request.getContextPath() + "/login?error");
                }))
                .rememberMe(r -> r.rememberMeServices(rememberMe(users)))
                .logout(l -> l.logoutSuccessUrl("/login?logout"))
                .build();
    }

    /**
     * "Stay logged in" cookies, signed with APP_PASSWORD as the key, so changing it logs out every device.
     * The stored password hash is left out of the signature: it gets a new random salt each time the app starts,
     * which would log everyone out on every restart.
     */
    private TokenBasedRememberMeServices rememberMe(UserDetailsService users) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(password, users) {
            @Override
            protected String makeTokenSignature(long expiry, String username, String storedHash,
                                                RememberMeTokenAlgorithm algorithm) {
                return super.makeTokenSignature(expiry, username, "", algorithm);
            }
        };
        services.setParameter("remember");
        services.setTokenValiditySeconds((int) Duration.ofDays(REMEMBER_DAYS).toSeconds());
        return services;
    }

    /**
     * Creates the token before the page renders, so its cookie goes out with the headers. Otherwise it is created
     * mid-render, and on a long page the headers may already be sent.
     */
    private static OncePerRequestFilter loadCsrfToken() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (token != null) {
                    token.getToken();
                }
                chain.doFilter(request, response);
            }
        };
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
