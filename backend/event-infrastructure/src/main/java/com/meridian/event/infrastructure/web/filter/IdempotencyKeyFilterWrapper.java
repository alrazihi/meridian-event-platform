package com.meridian.event.infrastructure.web.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.CharArrayWriter;
import java.io.PrintWriter;

class IdempotencyKeyFilterWrapper extends HttpServletResponseWrapper {

    private final CharArrayWriter charArrayWriter = new CharArrayWriter();
    private int status = HttpServletResponse.SC_OK;

    IdempotencyKeyFilterWrapper(HttpServletRequest request, HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
        super.setStatus(status);
    }

    @Override
    public void sendError(int status) throws java.io.IOException {
        this.status = status;
        super.sendError(status);
    }

    @Override
    public void sendError(int status, String message) throws java.io.IOException {
        this.status = status;
        super.sendError(status, message);
    }

    @Override
    public PrintWriter getWriter() throws java.io.IOException {
        PrintWriter writer = new PrintWriter(charArrayWriter);
        return writer;
    }

    public boolean isCommitted() {
        return charArrayWriter.size() > 0 || status >= 400;
    }

    public String getResponseBody() {
        return charArrayWriter.toString();
    }

    public int getStatus() {
        return status;
    }
}
