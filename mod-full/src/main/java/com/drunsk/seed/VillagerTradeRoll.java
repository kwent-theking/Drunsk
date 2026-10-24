package com.drunsk.seed;

import com.google.gson.JsonObject;
import java.util.Objects;

/** Exact roll-count/filter contract for villager trade analysis; no runtime RNG is used. */
public record VillagerTradeRoll(long seed, String worldVersion, String profession, int rollCount, String itemFilter) {
    public VillagerTradeRoll {
        if (worldVersion == null || worldVersion.isBlank()) throw new IllegalArgumentException("worldVersion is required");
        if (profession == null || profession.isBlank()) throw new IllegalArgumentException("profession is required");
        if (rollCount < 1 || rollCount > 1_000_000) throw new IllegalArgumentException("rollCount must be 1..1000000");
        if (itemFilter == null || itemFilter.isBlank()) throw new IllegalArgumentException("itemFilter is required");
    }
    public JsonObject toJson() { JsonObject o=new JsonObject(); o.addProperty("seed",seed); o.addProperty("worldVersion",worldVersion); o.addProperty("profession",profession); o.addProperty("rollCount",rollCount); o.addProperty("itemFilter",itemFilter); return o; }
    public static VillagerTradeRoll fromJson(JsonObject o) { Objects.requireNonNull(o,"json"); for(String k:new String[]{"seed","worldVersion","profession","rollCount","itemFilter"}) if(!o.has(k)) throw new IllegalArgumentException(k+" is required"); return new VillagerTradeRoll(o.get("seed").getAsLong(),o.get("worldVersion").getAsString(),o.get("profession").getAsString(),o.get("rollCount").getAsInt(),o.get("itemFilter").getAsString()); }
}
