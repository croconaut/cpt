package com.croconaut.cpt.link.handler.main;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class IgnoredHashesManager {
    private final Map<String, Map<String, AtomicInteger>> hashMap = new HashMap<>();

    void addIgnoredHash(String crocoId, String hash) {
        Map<String, AtomicInteger> hashMapForCrocoId = hashMap.get(crocoId);
        if (hashMapForCrocoId == null) {
            hashMapForCrocoId = new HashMap<>();
            hashMap.put(crocoId, hashMapForCrocoId);
        }
        // overwrite or add
        hashMapForCrocoId.put(hash, new AtomicInteger(5)); // max iterations
    }

    boolean isHashIgnored(String crocoId, String hash) {
        if (hashMap.containsKey(crocoId)) {
            Map<String, AtomicInteger> hashMapForCrocoId = hashMap.get(crocoId);
            if (hashMapForCrocoId.containsKey(hash)) {
                int counter = hashMapForCrocoId.get(hash).decrementAndGet();
                if (counter == 0) {
                    hashMap.remove(crocoId);
                }
            } else {
                hashMap.remove(crocoId);
            }
        }

        return hashMap.containsKey(crocoId) && hashMap.get(crocoId).containsKey(hash);
    }
}
