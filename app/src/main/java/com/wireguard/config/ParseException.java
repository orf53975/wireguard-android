package com.wireguard.config;

/**
 * An exception representing a failure to parse an element of a WireGuard configuration. The context
 * for this failure can be retrieved with {@link #getContext}.
 */
public class ParseException extends Exception {
    private final CharSequence context;

    public ParseException(final CharSequence context, final String message) {
        super(message);
        this.context = context;
    }

    public ParseException(final CharSequence context, final Throwable cause) {
        super(cause);
        this.context = context;
    }

    public CharSequence getContext() {
        return context;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ": " + context;
    }
}
