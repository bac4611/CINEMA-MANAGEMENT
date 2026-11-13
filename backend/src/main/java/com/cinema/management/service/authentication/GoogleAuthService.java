package com.cinema.management.service.authentication;

import com.cinema.management.model.entity.account.User;

import java.util.Map;

public interface GoogleAuthService {
    boolean isEnabled();
    AuthenticationResult authenticate(Map<String, Object> attributes);

    enum Status { SUCCESS, INVALID_PROFILE, EMAIL_UNVERIFIED, INACTIVE, LINK_FAILED }
    record AuthenticationResult(Status status, User user, String message) { }
}
