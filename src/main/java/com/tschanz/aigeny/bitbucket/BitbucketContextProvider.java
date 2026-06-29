package com.tschanz.aigeny.bitbucket;
import com.tschanz.aigeny.chat.ContextProvider;

import com.tschanz.aigeny.bitbucket.BitbucketTokenContext;
import org.springframework.stereotype.Service;

/**
 * {@link ContextProvider} for Bitbucket integrations.
 *
 * <p>Manages the following ThreadLocal context per request:
 * <ul>
 *   <li>{@link BitbucketTokenContext} – API authentication token</li>
 * </ul>
 *
 * <p>{@code writeEnabled} is not used by Bitbucket and is silently ignored.
 */
@Service
public class BitbucketContextProvider implements ContextProvider {

    /** Token-map key used in {@code Map<String, String> tokens}. */
    public static final String KEY = "bitbucket";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public void setup(String token, boolean writeEnabled) {
        BitbucketTokenContext.set(token);
        // writeEnabled is not applicable to Bitbucket
    }

    @Override
    public void cleanup() {
        BitbucketTokenContext.clear();
    }
}

