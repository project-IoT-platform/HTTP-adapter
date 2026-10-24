package org.example.сonfig;
public class AdapterConfig {

    public static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    public static final String CORE_URL = System.getenv().getOrDefault("IOT_CORE_URL", "http://localhost:8080/ingest");
    public static final int MAX_BODY = 1024 * 1024;

    private AdapterConfig() {
    }
}