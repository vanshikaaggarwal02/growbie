package com.launchmate.website;

/** A website address that must not be fetched. The message is safe to show to the user. */
public class UrlRejectedException extends RuntimeException {

    public UrlRejectedException(String message) {
        super(message);
    }
}
