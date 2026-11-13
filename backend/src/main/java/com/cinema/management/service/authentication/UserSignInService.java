package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class UserSignInService {
    private static final Duration REMEMBER_ME_DURATION = Duration.ofDays(30);

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public void signIn(
            HttpServletRequest request,
            HttpServletResponse response,
            User user,
            boolean rememberMe) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("UserEmail", user.getEmail());
        session.setAttribute("UserFullName", user.getFullName());
        session.setAttribute("UserRole", user.getRole());
        session.setAttribute("UserID", user.getUserId());
        session.setAttribute("RememberMe", rememberMe);

        String role = user.getRole().startsWith("ROLE_")
                ? user.getRole() : "ROLE_" + user.getRole();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        if (rememberMe) {
            session.setMaxInactiveInterval((int) REMEMBER_ME_DURATION.toSeconds());
        }
    }

    public void signIn(
            HttpServletRequest request,
            HttpServletResponse response,
            User user) {
        signIn(request, response, user, false);
    }
}

