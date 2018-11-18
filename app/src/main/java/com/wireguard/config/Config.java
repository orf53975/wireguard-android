/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents the contents of a wg-quick configuration file, made up of one or more "Interface"
 * sections (combined together), and zero or more "Peer" sections (treated individually).
 * <p>
 * Instances of this class are immutable.
 */
public final class Config {
    public static final Pattern LINE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*([^\\s#][^#]*)");
    public static final Pattern LIST_SEPARATOR = Pattern.compile("\\s*,\\s*");

    private final Interface interfaze;
    private final List<Peer> peers;

    private Config(final Builder builder) {
        interfaze = Objects.requireNonNull(builder.interfaze, "An [Interface] section is required");
        // Defensively copy to ensure immutability even if the Builder is reused.
        peers = Collections.unmodifiableList(new ArrayList<>(builder.peers));
    }

    /**
     * Parses an series of "Interface" and "Peer" sections into a {@code Config}. Throws
     * {@link ParseException} if the input is not well-formed or contains unparseable sections.
     *
     * @param stream a stream of UTF-8 text that is interpreted as a WireGuard configuration file
     * @return a {@code Config} instance representing the supplied configuration
     */
    public static Config parse(final InputStream stream) throws IOException, ParseException {
        final Builder builder = new Builder();
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            final Collection<String> interfaceLines = new ArrayList<>();
            final Collection<String> peerLines = new ArrayList<>();
            boolean inInterfaceSection = false;
            boolean inPeerSection = false;
            @Nullable String line;
            while ((line = reader.readLine()) != null) {
                final int commentIndex = line.indexOf('#');
                if (commentIndex != -1)
                    line = line.substring(0, commentIndex);
                line = line.trim();
                if (line.isEmpty())
                    continue;
                if (line.startsWith("[")) {
                    // Consume all [Peer] lines read so far.
                    if (inPeerSection) {
                        builder.parsePeer(peerLines);
                        peerLines.clear();
                    }
                    if ("[Interface]".equalsIgnoreCase(line)) {
                        inInterfaceSection = true;
                        inPeerSection = false;
                    } else if ("[Peer]".equalsIgnoreCase(line)) {
                        inInterfaceSection = false;
                        inPeerSection = true;
                    } else {
                        throw new ParseException(line, "Unknown configuration section name");
                    }
                } else if (inInterfaceSection) {
                    interfaceLines.add(line);
                } else if (inPeerSection) {
                    peerLines.add(line);
                } else {
                    throw new ParseException(line, "Expected [Interface] or [Peer]");
                }
            }
            if (inPeerSection)
                builder.parsePeer(peerLines);
            else if (!inInterfaceSection)
                throw new ParseException("", "Empty configuration");
            // Combine all [Interface] sections in the file.
            builder.parseInterface(interfaceLines);
        }
        return builder.build();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Config))
            return false;
        final Config other = (Config) obj;
        return interfaze.equals(other.interfaze) && peers.equals(other.peers);
    }

    /**
     * Returns the interface section of the configuration.
     *
     * @return the interface configuration
     */
    public Interface getInterface() {
        return interfaze;
    }

    /**
     * Returns a list of the configuration's peer sections.
     *
     * @return a list of {@link Peer}s
     */
    public List<Peer> getPeers() {
        return peers;
    }

    @Override
    public int hashCode() {
        return interfaze.hashCode() ^ peers.hashCode();
    }

    /**
     * Converts the {@code Config} into a string suitable for debugging purposes. The {@code Config}
     * is identified by its interface's public key and the number of peers it has.
     *
     * @return a concise single-line identifier for the {@code Config}
     */
    @Override
    public String toString() {
        return "(Config " + interfaze + " (" + peers.size() + " peers))";
    }

    /**
     * Converts the {@code Config} into a string suitable for use as a {@code wg-quick}
     * configuration file.
     *
     * @return the {@code Config} represented as one [Interface] and zero or more [Peer] sections
     */
    public String toWgQuickString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n").append(interfaze.toWgQuickString());
        for (final Peer peer : peers)
            sb.append("\n[Peer]\n").append(peer.toWgQuickString());
        return sb.toString();
    }

    /**
     * Serializes the {@code Config} for use with the WireGuard cross-platform userspace API.
     *
     * @return the {@code Config} represented as a series of "key=value" lines
     */
    public String toWgUserspaceString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(interfaze.toWgUserspaceString());
        sb.append("replace_peers=true\n");
        for (final Peer peer : peers)
            sb.append(peer.toWgUserspaceString());
        return sb.toString();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder {
        // Defaults to an empty set.
        private final Set<Peer> peers = new LinkedHashSet<>();
        // No default; must be provided before building.
        @Nullable private Interface interfaze;

        public Builder addPeer(final Peer peer) {
            peers.add(peer);
            return this;
        }

        public Builder addPeers(final Collection<Peer> peers) {
            this.peers.addAll(peers);
            return this;
        }

        public Config build() {
            return new Config(this);
        }

        public Builder parseInterface(final Iterable<? extends CharSequence> lines) throws ParseException {
            return setInterface(Interface.parse(lines));
        }

        public Builder parsePeer(final Iterable<? extends CharSequence> lines) throws ParseException {
            return addPeer(Peer.parse(lines));
        }

        public Builder setInterface(final Interface interfaze) {
            this.interfaze = interfaze;
            return this;
        }
    }
}
