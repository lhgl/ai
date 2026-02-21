package br.com.ai.connector.application.port.out;

import java.util.List;

public interface KnowledgeContextPort {
    List<String> loadModuleContext(long routeId, String moduleKey, int limit);

    List<String> loadProfileContext(String profileId, String moduleKey, int limit);

    void saveModuleContext(long routeId, String moduleKey, String contextText, String sourceType);

    void saveProfileContext(String profileId, String moduleKey, String contextText, String sourceType);
}
