package dev.kevin.gallbladder;

/** Flash message shown after a delete, with a button that posts to {@code restoreUrl}. */
public record Undo(String message, String restoreUrl) {
}
