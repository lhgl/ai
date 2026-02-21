package br.com.ai.connector.application.port.out;

public interface RequestLogPort {
    void saveRequestLog(String routeKey, String provider, String model, String requestSummary, String responseSummary);
}
