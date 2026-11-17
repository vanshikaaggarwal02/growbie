package com.launchmate.channel;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a YouTube channel is identified in a link. Only forms the YouTube API can look up cheaply are
 * supported: the @handle, the /channel/ id and the legacy /user/ name. Legacy /c/ custom links can
 * only be resolved through a search, which costs 100 times more quota, so they are refused with
 * instructions instead.
 */
public sealed interface ChannelRef {

    record Handle(String handle) implements ChannelRef {}

    record Id(String channelId) implements ChannelRef {}

    record Username(String username) implements ChannelRef {}

    /** A legacy custom name (youtube.com/c/name or youtube.com/name), tried as a handle first. */
    record Custom(String name) implements ChannelRef {}

    /** The link cannot be used; the message tells the user what to do. */
    class Unsupported extends RuntimeException {
        public Unsupported(String message) {
            super(message);
        }
    }

    Pattern HANDLE = Pattern.compile("^@([A-Za-z0-9._-]{3,30})(/.*)?$");
    Pattern CHANNEL_ID = Pattern.compile("^channel/(UC[A-Za-z0-9_-]{22})(/.*)?$");
    Pattern USER = Pattern.compile("^user/([A-Za-z0-9._-]{1,50})(/.*)?$");

    Pattern CUSTOM = Pattern.compile("^(?:c/)?([A-Za-z0-9._-]{3,50})(/.*)?$");
    Set<String> RESERVED = Set.of("watch", "playlist", "shorts", "feed", "results", "live", "embed", "hashtag",
            "channel", "user", "c", "about", "premium", "gaming", "music", "kids", "account", "podcasts");

    /**
     * Turns what a person typed into a canonical https youtube.com link: adds a missing https://,
     * accepts a bare @handle, and refuses links that clearly are not a channel (a video, a playlist)
     * with a message saying what to paste instead.
     */
    static String normalise(String input) {
        if (input == null || input.isBlank()) {
            throw new Unsupported("Enter a YouTube channel link.");
        }
        String s = input.trim();
        if (s.startsWith("@")) {
            s = "https://www.youtube.com/" + s;
        } else if (s.regionMatches(true, 0, "http://", 0, 7)) {
            s = "https://" + s.substring(7);
        } else if (!s.regionMatches(true, 0, "https://", 0, 8)) {
            s = "https://" + s;
        }
        URI uri;
        try {
            uri = new URI(s);
        } catch (URISyntaxException e) {
            throw new Unsupported("The channel link is not valid.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("youtu.be")) {
            throw new Unsupported("That looks like a video link. Open the channel page on YouTube and copy its "
                    + "address, which looks like https://www.youtube.com/@yourhandle.");
        }
        boolean youtube = host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com");
        if (!youtube) {
            throw new Unsupported("The channel link must be a youtube.com address, for example "
                    + "https://www.youtube.com/@yourhandle.");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/watch") || path.startsWith("/playlist") || path.startsWith("/shorts/")
                || path.startsWith("/live/")) {
            throw new Unsupported("That is a video or playlist link, not a channel link. Open the channel page on "
                    + "YouTube and copy its address, which looks like https://www.youtube.com/@yourhandle.");
        }
        return s;
    }

    static ChannelRef parse(String url) {
        String normal = normalise(url);
        URI uri;
        try {
            uri = new URI(normal);
        } catch (URISyntaxException e) {
            throw new Unsupported("The channel link is not valid.");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        path = path.startsWith("/") ? path.substring(1) : path;

        Matcher m = HANDLE.matcher(path);
        if (m.matches()) {
            return new Handle(m.group(1));
        }
        m = CHANNEL_ID.matcher(path);
        if (m.matches()) {
            return new Id(m.group(1));
        }
        m = USER.matcher(path);
        if (m.matches()) {
            return new Username(m.group(1));
        }
        m = CUSTOM.matcher(path);
        if (m.matches() && !RESERVED.contains(m.group(1).toLowerCase(Locale.ROOT))) {
            return new Custom(m.group(1));
        }
        throw new Unsupported("Use a channel link such as https://www.youtube.com/@yourhandle.");
    }
}
