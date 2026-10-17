package com.embers.legendcooldown;

/** Formatting is refreshed when the displayed tenth/second changes, not every frame. */
final class CooldownTimeCache {
    private record Entry(long units, boolean decimals, String text) {}
    private final BoundedCache<String,Entry> entries=new BoundedCache<>(128);
    String time(String name,double seconds,boolean decimals){
        long units=!Double.isFinite(seconds)||seconds<=0 ? 0 : (long)Math.ceil(seconds*(decimals?10:1)-1e-6);
        Entry cached=entries.get(name);
        if(cached!=null && cached.units==units && cached.decimals==decimals)return cached.text;
        String text=CooldownFormat.time(seconds,decimals);
        entries.put(name,new Entry(units,decimals,text));
        return text;
    }
    void clear(){entries.clear();}
}
