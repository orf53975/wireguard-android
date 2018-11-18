/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.viewmodel;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.wireguard.android.BR;
import com.wireguard.config.Attribute;
import com.wireguard.config.InetEndpoint;
import com.wireguard.config.ParseException;
import com.wireguard.config.Peer;
import com.wireguard.crypto.Key;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import java9.util.Lists;
import java9.util.Sets;

public class PeerProxy extends BaseObservable implements Parcelable {
    public static final Parcelable.Creator<PeerProxy> CREATOR = new PeerProxyCreator();
    private static final Set<String> IPV4_PUBLIC_NETWORKS = new LinkedHashSet<>(Lists.of(
            "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4", "32.0.0.0/3",
            "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6", "172.0.0.0/12",
            "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7",
            "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16",
            "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10",
            "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4"
    ));
    private static final Set<String> IPV4_WILDCARD = Sets.of("0.0.0.0/0");

    private String allowedIps;
    private AllowedIpsState allowedIpsState = AllowedIpsState.INVALID;
    private String endpoint;
    @Nullable private Listener listener;
    @Nullable private ConfigProxy owner;
    private String persistentKeepalive;
    private String preSharedKey;
    private String publicKey;
    private int totalPeers;

    private PeerProxy(final Parcel in) {
        allowedIps = in.readString();
        endpoint = in.readString();
        persistentKeepalive = in.readString();
        preSharedKey = in.readString();
        publicKey = in.readString();
    }

    public PeerProxy(final Peer other) {
        allowedIps = Attribute.join(other.getAllowedIps());
        endpoint = other.getEndpoint().map(InetEndpoint::toString).orElse("");
        persistentKeepalive = other.getPersistentKeepalive().map(String::valueOf).orElse("");
        preSharedKey = other.getPreSharedKey().map(Key::toBase64).orElse("");
        publicKey = other.getPublicKey().toBase64();
    }

    public PeerProxy() {
        allowedIps = "";
        endpoint = "";
        persistentKeepalive = "";
        preSharedKey = "";
        publicKey = "";
    }

    public void bind(final ConfigProxy owner) {
        final ObservableList<PeerProxy> peers = owner.getPeers();
        if (listener == null)
            listener = new Listener(this);
        peers.addOnListChangedCallback(listener);
        setTotalPeers(peers.size());
        this.owner = owner;
    }

    private void calculateAllowedIpsState() {
        final AllowedIpsState newState;
        if (totalPeers == 1) {
            // String comparison works because we only care if allowedIps is a superset of one of
            // the above sets of (valid) *networks*. We are not checking for a superset based on
            // the individual addresses in each set.
            final Collection<String> networkStrings = getAllowedIpsSet();
            // If allowedIps contains both the wildcard and the public networks, then private
            // networks aren't excluded!
            if (networkStrings.containsAll(IPV4_WILDCARD))
                newState = AllowedIpsState.CONTAINS_IPV4_WILDCARD;
            else if (networkStrings.containsAll(IPV4_PUBLIC_NETWORKS))
                newState = AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS;
            else
                newState = AllowedIpsState.OTHER;
        } else {
            newState = AllowedIpsState.INVALID;
        }
        if (newState != allowedIpsState) {
            allowedIpsState = newState;
            notifyPropertyChanged(BR.ableToExcludePrivateIps);
            notifyPropertyChanged(BR.excludingPrivateIps);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Bindable
    public String getAllowedIps() {
        return allowedIps;
    }

    private Set<String> getAllowedIpsSet() {
        return new LinkedHashSet<>(Lists.of(Attribute.split(allowedIps)));
    }

    @Bindable
    public String getEndpoint() {
        return endpoint;
    }

    @Bindable
    public String getPersistentKeepalive() {
        return persistentKeepalive;
    }

    @Bindable
    public String getPreSharedKey() {
        return preSharedKey;
    }

    @Bindable
    public String getPublicKey() {
        return publicKey;
    }

    @Bindable
    public boolean isAbleToExcludePrivateIps() {
        return allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS
                || allowedIpsState == AllowedIpsState.CONTAINS_IPV4_WILDCARD;
    }

    @Bindable
    public boolean isExcludingPrivateIps() {
        return allowedIpsState == AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS;
    }

    public Peer resolve() throws ParseException {
        final Peer.Builder builder = new Peer.Builder();
        if (!allowedIps.isEmpty())
            builder.parseAllowedIPs(allowedIps);
        if (!endpoint.isEmpty())
            builder.parseEndpoint(endpoint);
        if (!persistentKeepalive.isEmpty())
            builder.parsePersistentKeepalive(persistentKeepalive);
        if (!preSharedKey.isEmpty())
            builder.parsePreSharedKey(preSharedKey);
        if (!publicKey.isEmpty())
            builder.parsePublicKey(publicKey);
        return builder.build();
    }

    public void setAllowedIps(final String allowedIps) {
        this.allowedIps = allowedIps;
        notifyPropertyChanged(BR.allowedIps);
        calculateAllowedIpsState();
    }

    public void setEndpoint(final String endpoint) {
        this.endpoint = endpoint;
        notifyPropertyChanged(BR.endpoint);
    }

    public void setExcludingPrivateIps(final boolean excludingPrivateIps) {
        if (!isAbleToExcludePrivateIps() || isExcludingPrivateIps() == excludingPrivateIps)
            return;
        final Set<String> oldNetworks = excludingPrivateIps ? IPV4_WILDCARD : IPV4_PUBLIC_NETWORKS;
        final Set<String> newNetworks = excludingPrivateIps ? IPV4_PUBLIC_NETWORKS : IPV4_WILDCARD;
        final Collection<String> input = getAllowedIpsSet();
        final Collection<String> output =
                new LinkedHashSet<>(input.size() - oldNetworks.size() + newNetworks.size());
        boolean replaced = false;
        // Replace the first instance of the wildcard with the public network list, or vice versa.
        for (final String network : input) {
            if (oldNetworks.contains(network)) {
                if (!replaced) {
                    for (final String replacement : newNetworks)
                        if (!output.contains(replacement))
                            output.add(replacement);
                    replaced = true;
                }
            } else if (!output.contains(network)) {
                output.add(network);
            }
        }
        allowedIps = Attribute.join(output);
        allowedIpsState = excludingPrivateIps ?
                AllowedIpsState.CONTAINS_IPV4_PUBLIC_NETWORKS : AllowedIpsState.CONTAINS_IPV4_WILDCARD;
        notifyPropertyChanged(BR.allowedIps);
        notifyPropertyChanged(BR.ableToExcludePrivateIps);
        notifyPropertyChanged(BR.excludingPrivateIps);
    }

    public void setPersistentKeepalive(final String persistentKeepalive) {
        this.persistentKeepalive = persistentKeepalive;
        notifyPropertyChanged(BR.persistentKeepalive);
    }

    public void setPreSharedKey(final String preSharedKey) {
        this.preSharedKey = preSharedKey;
        notifyPropertyChanged(BR.preSharedKey);
    }

    public void setPublicKey(final String publicKey) {
        this.publicKey = publicKey;
        notifyPropertyChanged(BR.publicKey);
    }

    private void setTotalPeers(final int totalPeers) {
        if (this.totalPeers == totalPeers)
            return;
        this.totalPeers = totalPeers;
        calculateAllowedIpsState();
    }

    public void unbind() {
        if (owner == null)
            return;
        final ObservableList<PeerProxy> peers = owner.getPeers();
        if (listener != null)
            peers.removeOnListChangedCallback(listener);
        peers.remove(this);
        setTotalPeers(0);
        owner = null;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(allowedIps);
        dest.writeString(endpoint);
        dest.writeString(persistentKeepalive);
        dest.writeString(preSharedKey);
        dest.writeString(publicKey);
    }

    private enum AllowedIpsState {
        CONTAINS_IPV4_PUBLIC_NETWORKS,
        CONTAINS_IPV4_WILDCARD,
        INVALID,
        OTHER
    }

    private static final class Listener
            extends ObservableList.OnListChangedCallback<ObservableList<PeerProxy>> {
        private final WeakReference<PeerProxy> weakPeerProxy;

        private Listener(final PeerProxy peerProxy) {
            weakPeerProxy = new WeakReference<>(peerProxy);
        }

        @Override
        public void onChanged(final ObservableList<PeerProxy> sender) {
            @Nullable final PeerProxy peerProxy = weakPeerProxy.get();
            if (peerProxy != null)
                peerProxy.setTotalPeers(sender.size());
            else
                sender.removeOnListChangedCallback(this);
        }

        @Override
        public void onItemRangeChanged(final ObservableList<PeerProxy> sender,
                                       final int positionStart, final int itemCount) {
            // Do nothing.
        }

        @Override
        public void onItemRangeInserted(final ObservableList<PeerProxy> sender,
                                        final int positionStart, final int itemCount) {
            onChanged(sender);
        }

        @Override
        public void onItemRangeMoved(final ObservableList<PeerProxy> sender,
                                     final int fromPosition, final int toPosition,
                                     final int itemCount) {
            // Do nothing.
        }

        @Override
        public void onItemRangeRemoved(final ObservableList<PeerProxy> sender,
                                       final int positionStart, final int itemCount) {
            onChanged(sender);
        }
    }

    private static class PeerProxyCreator implements Parcelable.Creator<PeerProxy> {
        @Override
        public PeerProxy createFromParcel(final Parcel in) {
            return new PeerProxy(in);
        }

        @Override
        public PeerProxy[] newArray(final int size) {
            return new PeerProxy[size];
        }
    }
}
