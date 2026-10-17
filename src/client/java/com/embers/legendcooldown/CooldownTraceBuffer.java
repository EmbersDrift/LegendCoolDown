package com.embers.legendcooldown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded capture memory; repeated messages cannot consume the entire session. */
final class CooldownTraceBuffer {
    record Event(long elapsedMillis, String source, String raw, String parsed, String decision, String codepoints) {}
    private final int capacity;
    private final List<Event> events = new ArrayList<>();
    private final Map<String, Event> last = new LinkedHashMap<>();
    private int dropped;
    CooldownTraceBuffer(int capacity) { this.capacity = Math.max(1, capacity); }
    void add(long millis, String source, String raw, String parsed, String decision) {
        raw = limit(raw, 1024); parsed = limit(parsed, 512); decision = limit(decision, 512);
        Event prior = last.get(source);
        if (prior != null && prior.raw.equals(raw) && prior.decision.equals(decision) && millis-prior.elapsedMillis < 300) return;
        if (events.size() >= capacity) { dropped++; return; }
        String unicode = raw.codePoints().limit(128).mapToObj(c -> String.format("U+%04X",c)).collect(java.util.stream.Collectors.joining(" "));
        Event event = new Event(millis, source, raw, parsed, decision, unicode);
        events.add(event); last.put(source,event);
        while (last.size()>64) last.remove(last.keySet().iterator().next());
    }
    static String limit(String text, int points) {
        if (text == null) return "";
        int count = text.codePointCount(0,text.length());
        return count<=points ? text : text.substring(0,text.offsetByCodePoints(0,points));
    }
    List<Event> snapshot() { return List.copyOf(events); }
    int size() { return events.size(); }
    int dropped() { return dropped; }
}
