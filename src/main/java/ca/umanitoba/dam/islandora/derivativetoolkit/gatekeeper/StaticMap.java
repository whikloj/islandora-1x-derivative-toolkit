package ca.umanitoba.dam.islandora.derivativetoolkit.gatekeeper;

import java.util.HashMap;
import java.util.Map;

public class StaticMap {

    private Map<String, String> internalMap;

    public StaticMap() {
        internalMap = new HashMap<>();
    }

    public String get(final String key) {
        return internalMap.get(key);
    }

    public void put(final String key, final String value) {
        internalMap.put(key, value);
    }

    public boolean containsKey(final String key) {
        return internalMap.containsKey(key);
    }
}
