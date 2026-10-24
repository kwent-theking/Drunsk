package com.drunsk.seed;

import com.google.gson.JsonObject;
import java.util.Objects;

/** A caller-provided prediction result. Computation requires seed + world data and is intentionally absent here. */
public record WeatherPrediction(long seed, String worldVersion, long gameTime, String weather, double confidence) {
    public WeatherPrediction {
        if (worldVersion == null || worldVersion.isBlank()) throw new IllegalArgumentException("worldVersion is required");
        if (gameTime < 0) throw new IllegalArgumentException("gameTime must be non-negative");
        if (weather == null || weather.isBlank()) throw new IllegalArgumentException("weather is required");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be in [0,1]");
    }
    public JsonObject toJson() { JsonObject o=new JsonObject(); o.addProperty("seed",seed); o.addProperty("worldVersion",worldVersion); o.addProperty("gameTime",gameTime); o.addProperty("weather",weather); o.addProperty("confidence",confidence); return o; }
    public static WeatherPrediction fromJson(JsonObject o) { Objects.requireNonNull(o,"json"); for(String k:new String[]{"seed","worldVersion","gameTime","weather","confidence"}) if(!o.has(k)) throw new IllegalArgumentException(k+" is required"); return new WeatherPrediction(o.get("seed").getAsLong(),o.get("worldVersion").getAsString(),o.get("gameTime").getAsLong(),o.get("weather").getAsString(),o.get("confidence").getAsDouble()); }
}
