package com.meridian.event.infrastructure.observability;

import org.slf4j.MDC;

public final class CorrelationIdContext {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";

    private CorrelationIdContext() {}

    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String getSpanId() {
        return MDC.get(SPAN_ID_KEY);
    }

    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        } else {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        } else {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    public static void setSpanId(String spanId) {
        if (spanId != null) {
            MDC.put(SPAN_ID_KEY, spanId);
        } else {
            MDC.remove(SPAN_ID_KEY);
        }
    }

    public static void clear() {
        MDC.clear();
    }

    public static boolean hasCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY) != null;
    }
}