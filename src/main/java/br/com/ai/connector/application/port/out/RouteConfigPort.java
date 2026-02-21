package br.com.ai.connector.application.port.out;

import br.com.ai.connector.dto.RouteConfig;

import java.util.List;
import java.util.Optional;

public interface RouteConfigPort {
    Optional<RouteConfig> resolveRoute(String dataType, String characteristic);

    List<String> loadContextSnippets(long routeId, int limit);
}
