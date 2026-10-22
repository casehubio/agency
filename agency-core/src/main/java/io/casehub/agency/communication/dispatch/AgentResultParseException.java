package io.casehub.agency.communication.dispatch;

public class AgentResultParseException extends RuntimeException {
    public AgentResultParseException(String message) { super(message); }
    public AgentResultParseException(String message, Throwable cause) { super(message, cause); }
}
