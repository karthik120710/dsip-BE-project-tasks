package com.dsip.backend.exception;

public class WhitelistEmailNotFoundException extends DsipException {

    public WhitelistEmailNotFoundException(String email) {
        super(ErrorCode.WHITELIST_EMAIL_NOT_FOUND, "Email not found in whitelist: " + email);
    }
}
