package com.launchmate.ai;

/** The model's reply did not match the required structure or limits. The message says what was wrong. */
public class InvalidAiOutputException extends RuntimeException {

    public InvalidAiOutputException(String message) {
        super(message);
    }
}
