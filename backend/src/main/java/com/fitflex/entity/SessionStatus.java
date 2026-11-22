package com.fitflex.entity;

/** Life cycle of a training session: REQUESTED -> CONFIRMED -> COMPLETED, or CANCELLED. */
public enum SessionStatus {
    REQUESTED,
    CONFIRMED,
    COMPLETED,
    CANCELLED
}
