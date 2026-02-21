package br.com.ai.connector.adapter.out.oracle;

import br.com.ai.connector.application.port.out.RequestLogPort;
import br.com.ai.connector.application.port.out.RouteConfigPort;
import br.com.ai.connector.application.port.out.TrainingQueuePort;
import br.com.ai.connector.application.port.out.KnowledgeContextPort;
import br.com.ai.connector.dto.RouteConfig;
import br.com.ai.connector.dto.TrainingQueueItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OracleLlmConfigAdapter implements RouteConfigPort, RequestLogPort, TrainingQueuePort, KnowledgeContextPort {

    @Inject
    DataSource dataSource;

    @Override
    public Optional<RouteConfig> resolveRoute(String dataType, String characteristic) {
        final String sql = """
                SELECT route_id,
                       route_key,
                       provider,
                       model_name,
                       system_prompt,
                       temperature,
                       max_tokens
                FROM llm_route_config
                WHERE enabled = 1
                  AND data_type = ?
                  AND (data_characteristic = ? OR data_characteristic = '*')
                ORDER BY CASE WHEN data_characteristic = ? THEN 0 ELSE 1 END, priority ASC
                FETCH FIRST 1 ROWS ONLY
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dataType);
            statement.setString(2, characteristic);
            statement.setString(3, characteristic);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new RouteConfig(
                            resultSet.getLong("route_id"),
                            resultSet.getString("route_key"),
                            resultSet.getString("provider"),
                            resultSet.getString("model_name"),
                            resultSet.getString("system_prompt"),
                            getNullableDouble(resultSet, "temperature"),
                            getNullableInteger(resultSet, "max_tokens")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar rota de LLM no Oracle", e);
        }
    }

    @Override
    public List<String> loadContextSnippets(long routeId, int limit) {
        final String sql = """
                SELECT context_text
                FROM llm_route_context
                WHERE route_id = ?
                ORDER BY updated_at DESC
                FETCH FIRST ? ROWS ONLY
                """;
        List<String> snippets = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, routeId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    snippets.add(resultSet.getString("context_text"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao carregar contexto da rota no Oracle", e);
        }
        return snippets;
    }

    @Override
    public void saveRequestLog(String routeKey, String provider, String model, String requestSummary, String responseSummary) {
        final String sql = """
                INSERT INTO llm_request_log
                    (route_key, provider, model_name, request_summary, response_summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, routeKey);
            statement.setString(2, provider);
            statement.setString(3, model);
            statement.setString(4, requestSummary);
            statement.setString(5, responseSummary);
            statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao salvar log da requisicao no Oracle", e);
        }
    }

    @Override
    public List<TrainingQueueItem> fetchPendingTrainingItems(int batchSize) {
        final String sql = """
                SELECT id, data_type, data_characteristic, content, route_key, module_key, profile_id, source_type
                FROM llm_training_queue
                WHERE status = 'PENDING'
                  AND (next_run_at IS NULL OR next_run_at <= SYSTIMESTAMP)
                ORDER BY created_at
                FETCH FIRST ? ROWS ONLY
                """;
        List<TrainingQueueItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(new TrainingQueueItem(
                            resultSet.getLong("id"),
                            resultSet.getString("data_type"),
                            resultSet.getString("data_characteristic"),
                            resultSet.getString("content"),
                            resultSet.getString("route_key"),
                            resultSet.getString("module_key"),
                            resultSet.getString("profile_id"),
                            resultSet.getString("source_type")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao carregar fila de treinamento", e);
        }
        return items;
    }

    @Override
    public void markTrainingDone(long id) {
        updateTrainingStatus(id, "DONE", null);
    }

    @Override
    public void markTrainingFailed(long id, String reason) {
        updateTrainingStatus(id, "FAILED", reason);
    }

    @Override
    public void enqueueTrainingItem(
            String dataType,
            String dataCharacteristic,
            String content,
            String routeKey,
            String moduleKey,
            String profileId,
            String sourceType
    ) {
        final String sql = """
                INSERT INTO llm_training_queue
                    (data_type, data_characteristic, route_key, module_key, profile_id, source_type, content, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', SYSTIMESTAMP, SYSTIMESTAMP)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dataType);
            statement.setString(2, dataCharacteristic);
            statement.setString(3, routeKey);
            statement.setString(4, moduleKey);
            statement.setString(5, profileId);
            statement.setString(6, sourceType);
            statement.setString(7, content);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao enfileirar item de treinamento", e);
        }
    }

    @Override
    public List<String> loadModuleContext(long routeId, String moduleKey, int limit) {
        if (moduleKey == null || moduleKey.isBlank()) {
            return List.of();
        }
        final String sql = """
                SELECT context_text
                FROM llm_module_context
                WHERE route_id = ?
                  AND module_key = ?
                ORDER BY updated_at DESC
                FETCH FIRST ? ROWS ONLY
                """;
        return loadTexts(sql, ps -> {
            ps.setLong(1, routeId);
            ps.setString(2, moduleKey);
            ps.setInt(3, limit);
        });
    }

    @Override
    public List<String> loadProfileContext(String profileId, String moduleKey, int limit) {
        if (profileId == null || profileId.isBlank()) {
            return List.of();
        }
        String effectiveModule = (moduleKey == null || moduleKey.isBlank()) ? "GENERAL" : moduleKey;
        final String sql = """
                SELECT context_text
                FROM llm_profile_context
                WHERE profile_id = ?
                  AND module_key = ?
                ORDER BY updated_at DESC
                FETCH FIRST ? ROWS ONLY
                """;
        return loadTexts(sql, ps -> {
            ps.setString(1, profileId);
            ps.setString(2, effectiveModule);
            ps.setInt(3, limit);
        });
    }

    @Override
    public void saveModuleContext(long routeId, String moduleKey, String contextText, String sourceType) {
        final String sql = """
                INSERT INTO llm_module_context
                    (route_id, module_key, source_type, context_text, updated_at)
                VALUES (?, ?, ?, ?, SYSTIMESTAMP)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, routeId);
            statement.setString(2, moduleKey);
            statement.setString(3, sourceType);
            statement.setString(4, contextText);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao salvar contexto de modulo", e);
        }
    }

    @Override
    public void saveProfileContext(String profileId, String moduleKey, String contextText, String sourceType) {
        final String sql = """
                INSERT INTO llm_profile_context
                    (profile_id, module_key, source_type, context_text, updated_at)
                VALUES (?, ?, ?, ?, SYSTIMESTAMP)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profileId);
            statement.setString(2, moduleKey);
            statement.setString(3, sourceType);
            statement.setString(4, contextText);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao salvar contexto de perfil", e);
        }
    }

    private void updateTrainingStatus(long id, String status, String reason) {
        final String sql = """
                UPDATE llm_training_queue
                SET status = ?,
                    last_error = ?,
                    updated_at = SYSTIMESTAMP
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, reason);
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao atualizar status da fila de treinamento", e);
        }
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private List<String> loadTexts(String sql, SqlBinder binder) {
        List<String> snippets = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    snippets.add(resultSet.getString("context_text"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao carregar contexto", e);
        }
        return snippets;
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
