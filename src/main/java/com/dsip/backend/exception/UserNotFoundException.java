package com.dsip.backend.exception;

public class UserNotFoundException extends DsipException {

    public UserNotFoundException(String email) {
        super(ErrorCode.USER_NOT_FOUND, "User not found with email: " + email);
    }
}
