package io.casehub.agency.communication.channel;

import io.casehub.qhorus.api.channel.ChannelSemantic;

import java.util.UUID;

public record ChannelBinding(UUID channelId, ChannelSemantic semantic) {}
