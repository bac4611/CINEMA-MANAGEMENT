package com.cinema.management.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration(proxyBeanMethods = false)
public class GoogleOAuthConfiguration {
    public static boolean isConfigured(Environment environment) {
        return hasText(credential(environment, "client-id"))
                && hasText(credential(environment, "client-secret"));
    }

    private static String credential(Environment environment, String name) {
        String standard = environment.getProperty("spring.security.oauth2.client.registration.google." + name);
        return hasText(standard) ? standard : environment.getProperty("security.google." + name, "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    @Conditional(CredentialsPresent.class)
    ClientRegistrationRepository googleClientRegistrations(Environment environment) {
        var google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(credential(environment, "client-id"))
                .clientSecret(credential(environment, "client-secret"))
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    static class CredentialsPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isConfigured(context.getEnvironment());
        }
    }
}
