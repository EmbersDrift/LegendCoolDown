package com.embers.legendcooldown;

import java.util.regex.Pattern;

/** Strict server-system-message parser. Player chat is never passed to this reader. */
final class ServerCooldownText {
    private static final Pattern COLORS = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    // Resource packs also assign glyphs to unassigned supplementary codepoints,
    // e.g. KingMC's U+CFFAE/U+CFFFE/U+4FB50 spacing and progress-bar characters.
    private static final Pattern FONT_GLYPHS = Pattern.compile("[\\p{Co}\\p{Cn}]");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200b-\\u200d\\ufeff]");
    enum Kind { READY, ACTIVATED, REMAINING }
    record Signal(Kind kind, String name, double seconds) {}
    private static final String TIME = "(\\d+(?:\\.\\d+)?|\\d+:\\d{2}(?:\\.\\d+)?)\\s*(s|sec(?:ond)?s?|秒|m|min(?:ute)?s?|分钟)?";
    private static final Pattern READY = Pattern.compile("^Your (.{1,128}?) is now (?:available|ready)[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATE = Pattern.compile("^You ate (?:a|an) (.{1,128}?) and gained .+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTIVATED = Pattern.compile("^(?:Your )?(.{1,128}?) (?:ability )?(?:was |has been )?activated[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED = Pattern.compile("^(?:Your )?(.{1,128}?) (?:is (?:still )?on cooldown|cooldown(?: remaining)?)[ :：-]*" + TIME + "[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile("^(.{1,128}?)[ :：|»]+(?:cooldown(?: remaining)?[ :：]*)?" + TIME + "[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARROW = Pattern.compile("^(.{1,128}?)\\s*(?:->|[→➜➔➝➞➟➠➡➥➦➧➨➤►])\\s*" + TIME + "(?:[█▓▒░▌▐─━■□\\s]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASH = Pattern.compile("^(.{1,128}?)\\s+[\\p{Pd}>›»|]\\s*" + TIME + "(?:[█▓▒░▌▐─━■□\\s]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LORE_SUFFIX = Pattern.compile("^" + TIME + "\\s+Cooldown$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARRIER_FINISHED = Pattern.compile("^(.{1,128}?) barrier (?:was cancelled|has ended)[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WAIT = Pattern.compile("^(?:You (?:must |have to )?wait|Please wait|Cooldown(?: remaining)?)[ :：]*" + TIME + "(?: before .+| to .+| remaining)?[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LORE = Pattern.compile("^(?:Ability )?Cooldown[ :：]+" + TIME + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIVE_LORE = Pattern.compile("^(?:Ability )?(?:Cooldown remaining|Remaining cooldown)[ :：]+" + TIME + "$", Pattern.CASE_INSENSITIVE);
    static String clean(String raw) {
        if (raw == null || raw.length() > 32_768) return "";
        String text = FONT_GLYPHS.matcher(COLORS.matcher(raw).replaceAll("")).replaceAll("");
        text = ZERO_WIDTH.matcher(text.replace('\u00a0', ' ')).replaceAll("").strip();
        return text.length() <= 2048 ? text : "";
    }
    static Signal read(String raw, boolean overlay) {
        String text = clean(raw); var match = READY.matcher(text);
        if (match.matches()) return new Signal(Kind.READY, match.group(1).strip(), 0);
        match = ATE.matcher(text);
        if (match.matches()) return new Signal(Kind.ACTIVATED, match.group(1).strip(), 0);
        match = ACTIVATED.matcher(text);
        if (match.matches()) return new Signal(Kind.ACTIVATED, match.group(1).strip(), 0);
        match = BARRIER_FINISHED.matcher(text);
        if (match.matches()) return new Signal(Kind.ACTIVATED, match.group(1).strip(), 0);
        match = NAMED.matcher(text);
        if (match.matches()) return timed(match.group(1), match.group(2), match.group(3));
        match = WAIT.matcher(text);
        if (match.matches()) return timed("", match.group(1), match.group(2));
        // Compact name/time displays are action bars only; never parse ordinary system chat this way.
        if (overlay) {
            Signal arrow = arrowTimer(text);
            if (arrow != null) return arrow;
            match = SHORT.matcher(text);
            if (match.matches() && (match.group(3) != null || match.group(2).contains(":")))
                return timed(match.group(1), match.group(2), match.group(3));
        }
        return null;
    }
    static Signal arrowTimer(String text) {
        for (String line : clean(text).split("\\R")) {
            var match = ARROW.matcher(line.strip());
            if (match.matches() && (match.group(3) != null || match.group(2).contains(":")))
                return timed(match.group(1), match.group(2), match.group(3));
            match = DASH.matcher(line.strip());
            if (match.matches() && (match.group(3) != null || match.group(2).contains(":")))
                return timed(match.group(1), match.group(2), match.group(3));
        }
        return null;
    }
    static String itemKey(String label, String serverAddress) {
        String key = CooldownFormat.labelKey(label);
        String host = serverAddress == null ? "" : serverAddress.toLowerCase(java.util.Locale.ROOT).replaceFirst(":\\d+$", "");
        // On KingMC the Smoke timer is the ability of the Creaking Scythe shown in the reference.
        return host.equals("kingmc.vn") && key.equals("creakingsmoke") ? "creakingscythe" : key;
    }
    static double loreSeconds(String text, boolean remaining) {
        var match = (remaining ? LIVE_LORE : LORE).matcher(clean(text));
        if (match.matches()) return seconds(match.group(1), match.group(2));
        if (!remaining) {
            match = LORE_SUFFIX.matcher(clean(text));
            if (match.matches()) return seconds(match.group(1), match.group(2));
        }
        return -1;
    }
    private static Signal timed(String name, String value, String unit) {
        double seconds = seconds(value, unit);
        return seconds >= 0 ? new Signal(Kind.REMAINING, name.strip(), seconds) : null;
    }
    private static double seconds(String value, String unit) {
        try {
            double seconds;
            if (value.contains(":")) {
                String[] pieces = value.split(":");
                double tail = Double.parseDouble(pieces[1]);
                if (tail >= 60 || (unit != null && unit.startsWith("m"))) return -1;
                seconds = Double.parseDouble(pieces[0]) * 60 + tail;
            } else seconds = Double.parseDouble(value) * (unit != null && (unit.toLowerCase().startsWith("m") || unit.equals("分钟")) ? 60 : 1);
            return Double.isFinite(seconds) && seconds >= 0 && seconds <= 86_400 ? seconds : -1;
        } catch (RuntimeException ignored) { return -1; }
    }
}
