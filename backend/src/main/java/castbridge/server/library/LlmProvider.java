package castbridge.server.library;

import java.io.IOException;

/**
 * Transport to an LLM service: one system prompt + one user message in, text and token counts out. Implementations know nothing about file
 * names; tests plug a fake one, so that no test ever calls a real service.
 */
public interface LlmProvider {
    /** @param jsonOnly ask the service for a JSON object when it supports it (OpenAI-compatible: response_format) */
    record Request(String model, String system, String user, int maxOutputTokens, boolean jsonOnly) {}

    /** Token counts are what the service reported; -1 when it reported nothing (the cost is then estimated). */
    record Reply(String text, long inputTokens, long outputTokens) {}

    Reply complete(Request request) throws IOException, InterruptedException;
}
