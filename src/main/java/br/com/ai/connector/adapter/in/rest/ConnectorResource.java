package br.com.ai.connector.adapter.in.rest;

import br.com.ai.connector.application.port.out.LlmProviderCatalogPort;
import br.com.ai.connector.application.usecase.InferenceUseCase;
import br.com.ai.connector.application.usecase.TrainingJobUseCase;
import br.com.ai.connector.dto.InferenceRequest;
import br.com.ai.connector.dto.InferenceResponse;
import br.com.ai.connector.dto.ProviderInfo;
import br.com.ai.connector.dto.TrainingMessage;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Path("/v1/connector")
@Produces(MediaType.APPLICATION_JSON)
public class ConnectorResource {

    @Inject
    InferenceUseCase inferenceUseCase;

    @Inject
    TrainingJobUseCase trainingJobUseCase;

    @Inject
    LlmProviderCatalogPort providerCatalogPort;

    @POST
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    public InferenceResponse postQuery(
            @HeaderParam("X-Data-Type") String dataType,
            @HeaderParam("X-Data-Characteristic") String characteristic,
            @HeaderParam("X-Module-Key") String moduleKey,
            @HeaderParam("X-Profile-Id") String profileId,
            InferenceRequest request
    ) {
        requireRequest(request);
        return inferenceUseCase.ask(
                require(dataType, "X-Data-Type"),
                require(characteristic, "X-Data-Characteristic"),
                moduleKey,
                profileId,
                request
        );
    }

    @PUT
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    public InferenceResponse putQuery(
            @HeaderParam("X-Data-Type") String dataType,
            @HeaderParam("X-Data-Characteristic") String characteristic,
            @HeaderParam("X-Module-Key") String moduleKey,
            @HeaderParam("X-Profile-Id") String profileId,
            InferenceRequest request
    ) {
        requireRequest(request);
        return inferenceUseCase.ask(
                require(dataType, "X-Data-Type"),
                require(characteristic, "X-Data-Characteristic"),
                moduleKey,
                profileId,
                request
        );
    }

    @POST
    @Path("/query/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> streamQuery(
            @HeaderParam("X-Data-Type") String dataType,
            @HeaderParam("X-Data-Characteristic") String characteristic,
            @HeaderParam("X-Module-Key") String moduleKey,
            @HeaderParam("X-Profile-Id") String profileId,
            InferenceRequest request
    ) {
        requireRequest(request);
        return inferenceUseCase.askStream(
                require(dataType, "X-Data-Type"),
                require(characteristic, "X-Data-Characteristic"),
                moduleKey,
                profileId,
                request
        );
    }

    @POST
    @Path("/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public InferenceResponse sendFile(
            @HeaderParam("X-Data-Type") String dataType,
            @HeaderParam("X-Data-Characteristic") String characteristic,
            @HeaderParam("X-Module-Key") String moduleKey,
            @HeaderParam("X-Profile-Id") String profileId,
            @BeanParam FileRequestForm form
    ) throws IOException {
        if (form == null || form.file == null) {
            throw new IllegalArgumentException("Multipart invalido: arquivo nao informado.");
        }
        byte[] bytes = Files.readAllBytes(form.file.toPath());
        String encoded = Base64.getEncoder().encodeToString(bytes);
        String joinedPrompt = """
                Analise o arquivo enviado (base64) junto com o prompt.
                Prompt: %s
                FileBase64: %s
                """.formatted(form.prompt, encoded);

        InferenceRequest request = new InferenceRequest(joinedPrompt, null, Map.of("source", "multipart-file"));
        return inferenceUseCase.ask(
                require(dataType, "X-Data-Type"),
                require(characteristic, "X-Data-Characteristic"),
                moduleKey,
                profileId,
                request
        );
    }

    @POST
    @Path("/training/enqueue")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> enqueueTraining(TrainingMessage message) {
        trainingJobUseCase.enqueue(message);
        return Map.of("status", "queued");
    }

    @GET
    @Path("/providers")
    public Map<String, Object> providers() {
        List<ProviderInfo> providers = providerCatalogPort.listProviders();
        return Map.of("providers", providers);
    }

    @GET
    @Path("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    private String require(String value, String headerName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Header obrigatorio ausente: " + headerName);
        }
        return value;
    }

    private void requireRequest(InferenceRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("Body invalido: campo 'prompt' e obrigatorio.");
        }
    }
}
