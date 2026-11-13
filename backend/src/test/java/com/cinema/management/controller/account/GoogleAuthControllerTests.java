package com.cinema.management.controller.account;

import com.cinema.management.model.entity.account.User;
import com.cinema.management.service.authentication.GoogleAuthService;
import com.cinema.management.service.authentication.UserSignInService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoogleAuthControllerTests {
    private final GoogleAuthService google = mock(GoogleAuthService.class);
    private final UserSignInService signIn = mock(UserSignInService.class);
    private final GoogleAuthController controller = new GoogleAuthController(google, signIn);

    @Test
    void disabledGoogleLoginReturnsToAccountLogin() {
        when(google.isEnabled()).thenReturn(false);

        String view = controller.login(null, new MockHttpServletRequest(), new RedirectAttributesModelMap());

        assertEquals("redirect:/Account/Login", view);
    }

    @Test
    void successfulCallbackUsesSafeLocalReturnUrlAndCreatesApplicationSession() {
        when(google.isEnabled()).thenReturn(true);
        User user = user("KhachHang");
        when(google.authenticate(anyMap())).thenReturn(
                new GoogleAuthService.AuthenticationResult(GoogleAuthService.Status.SUCCESS, user, null));
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of("sub", "1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.login("/Booking/Checkout", request, new RedirectAttributesModelMap());

        String view = controller.callback(null, principal, request, response, new RedirectAttributesModelMap());

        assertEquals("redirect:/Booking/Checkout", view);
        verify(signIn).signIn(request, response, user, true);
    }

    @Test
    void externalReturnUrlIsDiscardedAndRoleControlsRedirect() {
        when(google.isEnabled()).thenReturn(true);
        User user = user("Admin");
        when(google.authenticate(anyMap())).thenReturn(
                new GoogleAuthService.AuthenticationResult(GoogleAuthService.Status.SUCCESS, user, null));
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of("sub", "1"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.login("https://evil.example", request, new RedirectAttributesModelMap());

        String view = controller.callback(null, principal, request, response, new RedirectAttributesModelMap());

        assertEquals("redirect:/Admin/Index", view);
    }

    @Test
    void failedLinkNeverCreatesApplicationSession() {
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttributes()).thenReturn(Map.of("sub", "1"));
        when(google.authenticate(anyMap())).thenReturn(
                new GoogleAuthService.AuthenticationResult(GoogleAuthService.Status.LINK_FAILED, null, "failed"));

        String view = controller.callback(null, principal, new MockHttpServletRequest(),
                new MockHttpServletResponse(), new RedirectAttributesModelMap());

        assertEquals("redirect:/Account/Login", view);
        verifyNoInteractions(signIn);
    }

    private User user(String role) {
        User user = new User(); user.setUserId(7); user.setFullName("Google Person");
        user.setEmail("person@example.com"); user.setRole(role); user.setStatus(true); return user;
    }
}
