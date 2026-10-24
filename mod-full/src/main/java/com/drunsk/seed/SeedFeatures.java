package com.drunsk.seed;

import com.google.gson.JsonObject;
import java.util.Objects;

/** Pure data contract for seed analysis; it never loads Minecraft runtime/world state. */
public record SeedFeatures(long seed, String worldVersion, String dimension, Long chunkX, Long chunkZ) {
    public SeedFeatures {
        if (worldVersion == null || worldVersion.isBlank()) throw new IllegalArgumentException("worldVersion is required");
        if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("dimension is required");
        if ((chunkX == null) != (chunkZ == null)) throw new IllegalArgumentException("chunkX and chunkZ must be supplied together");
    }
    public JsonObject toJson() {
        JsonObject o = new JsonObject(); o.addProperty("seed", seed); o.addProperty("worldVersion", worldVersion); o.addProperty("dimension", dimension);
        if (chunkX != null) { o.addProperty("chunkX", chunkX); o.addProperty("chunkZ", chunkZ); } return o;
    }
    public static SeedFeatures fromJson(JsonObject o) {
        Objects.requireNonNull(o, "json");
        if (!o.has("seed") || !o.has("worldVersion") || !o.has("dimension")) throw new IllegalArgumentException("seed, worldVersion and dimension are required");
        Long x = o.has("chunkX") ? o.get("chunkX").getAsLong() : null, z = o.has("chunkZ") ? o.get("chunkZ").getAsLong() : null;
        return new SeedFeatures(o.get("seed").getAsLong(), o.get("worldVersion").getAsString(), o.get("dimension").getAsString(), x, z);
    }
}
