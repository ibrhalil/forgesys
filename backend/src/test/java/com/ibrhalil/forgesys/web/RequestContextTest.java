package com.ibrhalil.forgesys.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestContextTest {

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void initialStateIsEmpty() {
        assertTrue(RequestContext.current().isEmpty());
    }

    @Test
    void setAndCurrentReturnsMeta() {
        RequestMeta meta = new RequestMeta("trace-1", "10.0.0.1", "Mozilla/5.0");

        RequestContext.set(meta);

        Optional<RequestMeta> current = RequestContext.current();
        assertTrue(current.isPresent());
        assertEquals(meta, current.get());
    }

    @Test
    void clearRemovesMeta() {
        RequestContext.set(new RequestMeta("trace-2", "10.0.0.2", "ua"));

        RequestContext.clear();

        assertTrue(RequestContext.current().isEmpty());
    }
}
