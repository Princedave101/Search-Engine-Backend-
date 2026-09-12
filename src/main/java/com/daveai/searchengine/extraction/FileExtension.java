package com.daveai.searchengine.extraction;

import java.nio.file.Path;
import java.util.Locale;

/** Reads a file's extension. */
final class FileExtension {

    private FileExtension() {
    }

    /**
     * Returns a file's extension in lowercase, without the dot.
     * For example {@code "Report.PDF"} gives {@code "pdf"}, and {@code "README"} gives {@code ""}.
     *
     * @param file the file; not null
     * @return the lowercase extension, or an empty string if the name has none
     */
    static String of(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
