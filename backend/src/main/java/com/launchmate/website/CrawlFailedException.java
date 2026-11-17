package com.launchmate.website;

/** The crawl could not proceed. The message is safe to show to the user. */
public class CrawlFailedException extends RuntimeException {

    public CrawlFailedException(String message) {
        super(message);
    }
}
