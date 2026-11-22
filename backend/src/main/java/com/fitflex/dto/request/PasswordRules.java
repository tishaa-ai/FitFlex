package com.fitflex.dto.request;

/**
 * One place for the password policy, shared by the user and trainer register requests.
 * The frontend applies the same rule (at least 8 characters including a number).
 */
public final class PasswordRules {

    /** (?=.*\d) = "somewhere there is a digit", .{8,} = "8 or more characters". */
    public static final String REGEX = "^(?=.*\\d).{8,}$";
    public static final String MESSAGE = "Password must be at least 8 characters and contain a number";

    private PasswordRules() {
    }
}
