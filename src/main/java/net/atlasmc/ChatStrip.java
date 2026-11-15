package net.atlasmc;

/** Renk kodlarını (&x) siler. */
final class ChatStrip {
    private ChatStrip() {}

    static String strip(String s) {
        return s.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}
