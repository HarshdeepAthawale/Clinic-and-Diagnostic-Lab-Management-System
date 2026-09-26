package com.cdlms.dashboard;

import java.util.List;

/**
 * One dashboard panel. The frontend has a component per {@code type}; unknown types are skipped,
 * so a phase can add a widget server-side and ship its component independently.
 *
 * @param type  widget kind, e.g. {@code stats}, {@code schedule}, {@code recentPatients}
 * @param title panel heading
 * @param span  layout hint: {@code full}, {@code wide} (2/3) or {@code narrow} (1/3)
 * @param data  widget-specific payload
 */
public record Widget(String type, String title, String span, Object data) {

    public record Stat(String key, String label, long value, String hint) {
    }

    public static Widget stats(List<Stat> items) {
        return new Widget("stats", null, "full", items);
    }

    /** A module this role will use that isn't built yet — shown as an honest empty state, never fake data. */
    public record Upcoming(String module, int phase, String description) {
    }

    public static Widget upcoming(String title, List<Upcoming> modules) {
        return new Widget("upcoming", title, "full", modules);
    }
}
