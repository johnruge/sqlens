package com.sqlens.services;

/** Thrown when user input is not a single, read-only SELECT statement. */
public class InvalidQueryException extends IllegalArgumentException {
    public InvalidQueryException(String message) {
        super(message);
    }
}
