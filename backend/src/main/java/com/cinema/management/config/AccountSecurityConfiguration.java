package com.cinema.management.config;

import com.cinema.management.service.authentication.DefaultJwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.util.function.SingletonSupplier;

import java.util.List;

@Configuration
public class AccountSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain accountApiSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return http.securityMatcher("/api/account/**", "/api/chatbot/**", "/login", "/webhooks/sepay")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/login", "/api/account/login", "/api/account/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/recommend").permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhooks/sepay").permitAll()
                        .requestMatchers("/api/account/me").hasAnyRole("Admin", "Staff", "KhachHang")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder)
                        .jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain accountMvcSecurity(HttpSecurity http,
                                            ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/GoogleAuth/**", "/google-auth/**",
                                "/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/Account/Profile", "/Account/profile", "/account/Profile", "/account/profile")
                        .authenticated()
                        .requestMatchers("/Admin/**", "/admin/**").hasRole("Admin")
                        .requestMatchers("/Staff/**", "/staff/**").hasAnyRole("Staff", "Admin")
                        .requestMatchers(
                                "/Home/AddReview", "/home/AddReview",
                                "/Home/add-review", "/home/add-review")
                        .hasRole("KhachHang")
                        .requestMatchers(
                                "/Booking/CompleteDevPayment", "/booking/CompleteDevPayment",
                                "/Booking/PaymentStatus", "/booking/PaymentStatus",
                                "/Booking/CancelBooking", "/booking/CancelBooking",
                                "/Booking/PaymentSuccess", "/booking/PaymentSuccess")
                        .hasRole("KhachHang")
                        .anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/Account/Login"))
                        .accessDeniedPage("/Account/AccessDenied"));
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .defaultSuccessUrl("/GoogleAuth/Callback", true)
                    .failureUrl("/GoogleAuth/Callback?remoteError=true"));
        }
        return http.build();
    }

    @Bean
    JwtDecoder accountJwtDecoder(Environment environment) {
        // Delay requiring JWT_KEY until an API bearer token is actually validated.
        var decoder = SingletonSupplier.of(() -> {
            var key = DefaultJwtTokenService.getSigningKey(environment.getProperty("security.jwt.key"));
            var jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            String issuer = environment.getRequiredProperty("security.jwt.issuer");
            String audience = environment.getRequiredProperty("security.jwt.audience");
            var audienceValidator = new JwtClaimValidator<List<String>>("aud",
                    values -> values != null && values.contains(audience));
            jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
            return jwtDecoder;
        });
        return token -> decoder.get().decode(token);
    }
}
