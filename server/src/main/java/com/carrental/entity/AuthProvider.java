package com.carrental.entity;

/** How a user can authenticate. See GoogleOAuthService for GOOGLE/LOCAL_AND_GOOGLE transitions. */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    LOCAL_AND_GOOGLE
}
