package io.casehub.agency.channel;

public class AgentResultParseException extends RuntimeException {
    public AgentResultParseException(String message) { super(message); }
    public AgentResultParseException(String message, Throwable cause) { super(message, cause); }
}
