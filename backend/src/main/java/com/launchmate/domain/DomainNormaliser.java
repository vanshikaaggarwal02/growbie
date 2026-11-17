package com.launchmate.domain;

import java.net.IDN;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Turns user input such as "https://www.BareBotanics.com/shop" into "barebotanics.com", or rejects it. */
public final class DomainNormaliser {

    private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern TLD = Pattern.compile("^([a-z]{2,24}|xn--[a-z0-9-]{2,59})$");

    private DomainNormaliser() {}

    public static Optional<String> normalise(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        for (char stop : new char[] {'/', '?', '#'}) {
            int cut = s.indexOf(stop);
            if (cut >= 0) {
                s = s.substring(0, cut);
            }
        }
        if (s.contains("@") || s.contains(":") || s.contains(" ")) {
            return Optional.empty();
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            s = IDN.toASCII(s).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (s.length() > 253 || s.indexOf('.') < 0) {
            return Optional.empty();
        }
        String[] labels = s.split("\\.", -1);
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                return Optional.empty();
            }
        }
        if (!TLD.matcher(labels[labels.length - 1]).matches()) {
            return Optional.empty();
        }
        return Optional.of(s);
    }
}
