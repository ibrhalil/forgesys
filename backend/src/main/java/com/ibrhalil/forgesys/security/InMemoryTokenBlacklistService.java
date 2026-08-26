package com.ibrhalil.forgesys.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test-profile blacklist (Docker-free): jti → expiry-millis, pruned on read. */
@Component
@Profile("test")
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private final Map<String, Long> entries = new ConcurrentHashMap<>();

    @Override
    public synchronized void blacklist(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        entries.put(jti, System.currentTimeMillis() + Math.max(1, ttlSeconds) * 1000L);
    }

    @Override
    public synchronized boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Long expiry = entries.get(jti);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            entries.remove(jti);
            return false;
        }
        return true;
    }
}
