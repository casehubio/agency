package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;

public final class MessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageCodec() {}

    public static String encode(AvatarMessage msg) {
        try {
            return MAPPER.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static AvatarMessage decodeClient(String json) {
        try {
            AvatarMessage msg = MAPPER.readValue(json, AvatarMessage.class);
            return switch (msg) {
                case AvatarMessage.Start s -> s;
                case AvatarMessage.Stop s -> s;
                case AvatarMessage.Text t -> t;
                case AvatarMessage.SpeakerIdentify si -> si;
                case AvatarMessage.PlaybackComplete pc -> pc;
                case AvatarMessage.UserActivity ua -> ua;
                default -> throw new IllegalArgumentException(
                        "Unknown client message type: " + msg.getClass().getSimpleName());
            };
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid message JSON", e);
        }
    }
}
