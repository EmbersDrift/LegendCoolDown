package com.embers.legendcooldown;
import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Pattern;

final class CooldownFormat {
    private static final Pattern COLORS=Pattern.compile("§[0-9a-fk-orx]",Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATORS=Pattern.compile("[^\\p{L}\\p{N}]");
    private CooldownFormat() {}
    static String labelKey(String label) {
        String value=COLORS.matcher(Normalizer.normalize(label,Normalizer.Form.NFKC)).replaceAll("").toLowerCase(Locale.ROOT);
        value=SEPARATORS.matcher(value).replaceAll("");
        return value.replaceFirst("cooldowns?$","");
    }
    static String time(double seconds, boolean decimals) {
        if (!Double.isFinite(seconds) || seconds<=0) return decimals?"0.0s":"0s";
        long units=(long)Math.ceil(seconds*(decimals?10:1)-1e-6);
        long total=decimals?units/10:units;
        if (total>=60) {long remainder=total%60;return total/60+":"+(remainder<10?"0":"")+remainder+(decimals?"."+units%10:"");}
        return total+(decimals?"."+units%10:"")+"s";
    }
    static String nextStyle(String current) {
        return switch(current) {case "RING"->"ICON";case "ICON"->"BAR";case "BAR"->"TEXT";default->"RING";};
    }
}
