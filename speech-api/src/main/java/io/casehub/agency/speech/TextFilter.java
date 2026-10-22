package io.casehub.agency.speech;

public interface TextFilter {
    String apply(String text);

    String name();

    int destructiveness();
}
