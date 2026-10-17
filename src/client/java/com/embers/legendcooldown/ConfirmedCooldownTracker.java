package com.embers.legendcooldown;

import java.util.LinkedHashMap;
import java.util.Map;

/** Only server-confirmed activations can teach a duration. Clicking an item cannot. */
final class ConfirmedCooldownTracker {
    private final Map<String, Long> pending = new LinkedHashMap<>();
    void activated(String name, long now) {
        pending.put(name, now);
        while (pending.size() > 32) pending.remove(pending.keySet().iterator().next());
    }
    double ready(String name, long now) {
        Long start = pending.remove(name);
        if (start == null) return 0;
        double seconds = (now - start) / 1_000_000_000.0;
        return seconds >= 0.25 && seconds <= 86_400 ? Math.ceil(seconds * 10) / 10.0 : 0;
    }
    void clear() { pending.clear(); }
}
