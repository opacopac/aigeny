package com.tschanz.aigeny;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Loads {@code messages.properties} and provides helper methods for message lookup.
 * Message key constants live in the classes that use them.
 */
public final class Messages {

    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("messages");

    private Messages() {}

    /** Returns the message for the given key without any argument substitution. */
    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    /** Returns the message for the given key with {@link MessageFormat} argument substitution. */
    public static String get(String key, Object... args) {
        return MessageFormat.format(BUNDLE.getString(key), args);
    }
}
