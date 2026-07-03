package com.yowpainter.shared.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowpainter.config.KernelProperties;
import com.yowpainter.shared.context.RequestContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KernelHttpClient {

    private final RestClient restClient;
    private final KernelProperties properties;
    private final ObjectMapper objectMapper;

    public KernelHttpClient(RestClient kernelRestClient, KernelProperties properties, ObjectMapper objectMapper) {
        this.restClient = kernelRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return exchange("POST", path, body, responseType, null);
    }

    public <T> T postBootstrap(String path, Object body, Class<T> responseType) {
        return exchangeBootstrap("POST", path, body, responseType);
    }

    public <T> T postBootstrap(String path, Object body, Class<T> responseType, String accessToken) {
        return withAccessToken(accessToken, () -> postBootstrap(path, body, responseType));
    }

    public <T> T get(String path, Class<T> responseType) {
        return exchange("GET", path, null, responseType, null);
    }

    public <T> T post(String path, Object body, Class<T> responseType, UUID organizationId) {
        return exchange("POST", path, body, responseType, organizationId);
    }

    public <T> T get(String path, Class<T> responseType, UUID organizationId) {
        return exchange("GET", path, null, responseType, organizationId);
    }

    public <T> T get(String path, Class<T> responseType, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> get(path, responseType, organizationId));
    }

    public <T> T getWithQuery(String path, Map<String, String> queryParams, Class<T> responseType, UUID organizationId) {
        return exchange("GET", buildUri(path, queryParams), null, responseType, organizationId);
    }

    public <T> List<T> getListWithQuery(String path, Map<String, String> queryParams, Class<T> elementType, UUID organizationId) {
        return parseListResponse(exchangeRaw("GET", buildUri(path, queryParams), null, organizationId), elementType);
    }

    public <T> T post(String path, Object body, Class<T> responseType, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> post(path, body, responseType, organizationId));
    }

    public <T> T getWithQuery(String path, Map<String, String> queryParams, Class<T> responseType, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> getWithQuery(path, queryParams, responseType, organizationId));
    }

    public <T> List<T> getListWithQuery(String path, Map<String, String> queryParams, Class<T> elementType, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> getListWithQuery(path, queryParams, elementType, organizationId));
    }

    public void postVoid(String path, Object body, UUID organizationId, String accessToken) {
        withAccessToken(accessToken, () -> {
            postVoid(path, body, organizationId);
            return null;
        });
    }

    public <T> T uploadMultipart(
            String path,
            byte[] content,
            String fileName,
            String contentType,
            String documentType,
            Class<T> responseType,
            UUID organizationId,
            String accessToken
    ) {
        return withAccessToken(accessToken, () -> uploadMultipart(
                path, content, fileName, contentType, documentType, responseType, organizationId
        ));
    }

    public <T> T uploadMultipart(
            String path,
            byte[] content,
            String fileName,
            String contentType,
            String documentType,
            Class<T> responseType,
            UUID organizationId
    ) {
        MediaType resolvedContentType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(resolvedContentType);
        MultiValueMap<String, Object> multipartData = new LinkedMultiValueMap<>();
        multipartData.add("file", new HttpEntity<>(fileResource, fileHeaders));
        String uri = documentType == null || documentType.isBlank()
                ? path
                : path + "?documentType=" + documentType;

        System.out.println(">>> KERNEL MULTIPART HTTP REQUEST START >>>");
        System.out.println("URL: " + properties.baseUrl() + uri);
        System.out.println("File Name: " + fileName);
        System.out.println("ContentType: " + resolvedContentType);
        System.out.println("DocumentType: " + documentType);
        System.out.println(">>> KERNEL MULTIPART HTTP REQUEST END >>>");

        RestClient.RequestBodySpec spec = restClient.post()
                .uri(uri)
                .headers(headers -> applyServerHeaders(headers, organizationId))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartData);

        ResponseEntity<String> response = spec.retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toEntity(String.class);

        return parseResponse(response.getBody(), responseType);
    }

    public ResponseEntity<byte[]> download(String path, UUID organizationId) {
        logRequest("GET", path, null, organizationId);
        return restClient.get()
                .uri(path)
                .headers(headers -> applyServerHeaders(headers, organizationId))
                .retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toEntity(byte[].class);
    }

    public ResponseEntity<byte[]> download(String path, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> download(path, organizationId));
    }

    public ResponseEntity<org.springframework.core.io.Resource> downloadStream(String path, org.springframework.http.HttpHeaders clientHeaders, UUID organizationId) {
        logRequest("GET", path, null, organizationId);
        return restClient.get()
                .uri(path)
                .headers(headers -> {
                    applyServerHeaders(headers, organizationId);
                    if (clientHeaders != null && clientHeaders.getFirst(org.springframework.http.HttpHeaders.RANGE) != null) {
                        headers.set(org.springframework.http.HttpHeaders.RANGE, clientHeaders.getFirst(org.springframework.http.HttpHeaders.RANGE));
                    }
                })
                .retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toEntity(org.springframework.core.io.Resource.class);
    }

    public ResponseEntity<org.springframework.core.io.Resource> downloadStream(String path, org.springframework.http.HttpHeaders clientHeaders, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> downloadStream(path, clientHeaders, organizationId));
    }


    public void postVoid(String path, Object body, UUID organizationId) {
        logRequest("POST", path, body, organizationId);
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(path)
                .headers(headers -> applyServerHeaders(headers, organizationId));

        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        spec.retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toBodilessEntity();
    }

    public <T> List<T> postList(String path, Object body, Class<T> elementType, UUID organizationId) {
        return parseListResponse(exchangeRaw("POST", path, body, organizationId), elementType);
    }

    public <T> List<T> postList(String path, Object body, Class<T> elementType, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> postList(path, body, elementType, organizationId));
    }

    public <T> List<T> getRawList(String path, Class<T> elementType, UUID organizationId, String accessToken) {
        return withAccessToken(accessToken, () -> getRawList(path, elementType, organizationId));
    }

    public <T> List<T> getRawList(String path, Class<T> elementType, UUID organizationId) {
        return parseRawListResponse(exchangeRaw("GET", path, null, organizationId), elementType);
    }

    private String buildUri(String path, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return path;
        }
        StringBuilder uri = new StringBuilder(path).append("?");
        queryParams.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                uri.append(key).append("=").append(value).append("&");
            }
        });
        if (uri.charAt(uri.length() - 1) == '&') {
            uri.deleteCharAt(uri.length() - 1);
        }
        return uri.toString();
    }

    private String exchangeRaw(String method, String path, Object body, UUID organizationId) {
        logRequest(method, path, body, organizationId);
        RestClient.RequestBodySpec spec = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .headers(headers -> applyServerHeaders(headers, organizationId));

        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ResponseEntity<String> response = spec.retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toEntity(String.class);
        System.out.println("<<< KERNEL HTTP RESPONSE SUCCESS (RAW) <<<");
        System.out.println("Raw HTTP Response Body: " + response.getBody());
        System.out.println("<<< KERNEL HTTP RESPONSE SUCCESS (RAW) END <<<");
        return response.getBody();
    }

    private <T> List<T> parseRawListResponse(String body, Class<T> elementType) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType);
            List<T> items = objectMapper.readValue(body, listType);
            return items == null ? List.of() : items;
        } catch (Exception ex) {
            throw new KernelClientException("Unable to parse kernel raw list response: " + ex.getMessage(), null, null);
        }
    }

    private <T> List<T> parseListResponse(String body, Class<T> elementType) {
        if (body == null || body.isBlank()) {
            throw new KernelClientException("Empty kernel response body", null, null);
        }
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructParametricType(KernelApiResponse.class,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
            KernelApiResponse<List<T>> wrapped = objectMapper.readValue(body, listType);
            if (!wrapped.success()) {
                throw new KernelClientException(
                        wrapped.message() != null ? wrapped.message() : "Kernel request failed",
                        null,
                        wrapped.errorCode()
                );
            }
            return wrapped.data() == null ? List.of() : wrapped.data();
        } catch (KernelClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KernelClientException("Unable to parse kernel list response: " + ex.getMessage(), null, null);
        }
    }

    private <T> T withAccessToken(String accessToken, java.util.function.Supplier<T> action) {
        RequestContext.State previous = RequestContext.get();
        try {
            RequestContext.set(new RequestContext.State(accessToken, previous != null ? previous.organizationId() : null, null));
            return action.get();
        } finally {
            if (previous == null) {
                RequestContext.clear();
            } else {
                RequestContext.set(previous);
            }
        }
    }

    public void postVoid(String path, Object body) {
        logRequest("POST", path, body, null);
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(path)
                .headers(headers -> applyServerHeaders(headers, null));

        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        spec.retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toBodilessEntity();
    }

    private <T> T exchange(String method, String path, Object body, Class<T> responseType, UUID organizationId) {
        logRequest(method, path, body, organizationId);
        RestClient.RequestBodySpec spec = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .headers(headers -> applyServerHeaders(headers, organizationId));

        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ResponseEntity<String> response = spec.retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toEntity(String.class);

        return parseResponse(response.getBody(), responseType);
    }

    private <T> T exchangeBootstrap(String method, String path, Object body, Class<T> responseType) {
        logRequestBootstrap(method, path, body);
        RestClient.RequestBodySpec spec = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(path)
                .headers(this::applyBootstrapServerHeaders);

        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ResponseEntity<String> response = spec.retrieve()
                .onStatus(status -> status.isError(), (request, clientResponse) -> {
                    throw toKernelException(path, clientResponse);
                })
                .toEntity(String.class);

        return parseResponse(response.getBody(), responseType);
    }

    private KernelClientException toKernelException(String path, ClientHttpResponse response) {
        try {
            org.springframework.http.HttpStatusCode statusCode = response.getStatusCode();
            String body = response.getBody() != null
                    ? new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)
                    : "";

            String message = "Kernel call failed on " + path;
            String errorCode = null;
            if (!body.isBlank()) {
                try {
                    KernelApiResponse<?> parsed = objectMapper.readValue(body, KernelApiResponse.class);
                    if (parsed.message() != null && !parsed.message().isBlank()) {
                        message = parsed.message();
                    }
                    errorCode = parsed.errorCode();
                } catch (Exception ex) {
                    message = extractFallbackKernelMessage(path, body, message);
                }
                if (message.equals("Kernel call failed on " + path)) {
                    message = extractFallbackKernelMessage(path, body, message);
                }
            }

            System.err.println("<<< KERNEL HTTP RESPONSE ERROR <<<");
            System.err.println("Path: " + path);
            System.err.println("HTTP Status Code: " + statusCode.value() + " " + statusCode);
            System.err.println("Response Headers: " + response.getHeaders());
            System.err.println("Raw HTTP Response Body: " + body);
            System.err.println("Parsed Message: " + message);
            System.err.println("Parsed Error Code: " + errorCode);
            System.err.println("<<< KERNEL HTTP RESPONSE ERROR END <<<");

            return new KernelClientException(message, statusCode, errorCode);
        } catch (IOException ex) {
            return new KernelClientException("Kernel call failed on " + path, null, null);
        }
    }

    private String extractFallbackKernelMessage(String path, String body, String defaultMessage) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) {
                return node.get("message").asText();
            }
            if (node.hasNonNull("error") && !node.get("error").asText().isBlank()) {
                String error = node.get("error").asText();
                if (node.has("status") && node.get("status").asInt() >= 500) {
                    return "Erreur interne kernel sur " + path + " : " + error
                            + ". Verifiez les logs kernel (PostgreSQL/Redis demarres, profil r2dbc).";
                }
                return error;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return defaultMessage + ": " + body;
    }

    private void applyServerHeaders(HttpHeaders headers, UUID organizationId) {
        applyClientHeaders(headers, properties.clientId(), properties.apiKey());
        applyContextHeaders(headers, organizationId);
    }

    private void applyBootstrapServerHeaders(HttpHeaders headers) {
        applyClientHeaders(headers, properties.effectiveBootstrapClientId(), properties.effectiveBootstrapApiKey());
        applyContextHeaders(headers, null);
    }

    private void applyClientHeaders(HttpHeaders headers, String clientId, String apiKey) {
        headers.set("X-Client-Id", clientId);
        headers.set("X-Api-Key", apiKey);
        headers.set("X-Tenant-Id", properties.tenantId());
    }

    private void applyContextHeaders(HttpHeaders headers, UUID organizationId) {
        RequestContext.State context = RequestContext.get();
        if (context != null && context.accessToken() != null && !context.accessToken().isBlank()) {
            headers.setBearerAuth(context.accessToken());
        }

        UUID resolvedOrganizationId = organizationId;
        if (resolvedOrganizationId == null && context != null) {
            resolvedOrganizationId = context.organizationId();
        }
        if (resolvedOrganizationId != null) {
            headers.set("X-Organization-Id", resolvedOrganizationId.toString());
        }
    }

    private <T> T parseResponse(String body, Class<T> responseType) {
        if (body == null || body.isBlank()) {
            throw new KernelClientException("Empty kernel response body", null, null);
        }
        try {
            System.out.println("<<< KERNEL HTTP RESPONSE SUCCESS <<<");
            System.out.println("Raw HTTP Response Body: " + body);
            System.out.println("<<< KERNEL HTTP RESPONSE SUCCESS END <<<");

            if (KernelApiResponse.class.equals(responseType)) {
                return objectMapper.readValue(body, responseType);
            }
            JavaType wrappedType = objectMapper.getTypeFactory()
                    .constructParametricType(KernelApiResponse.class, responseType);
            KernelApiResponse<T> wrapped = objectMapper.readValue(body, wrappedType);
            if (!wrapped.success()) {
                throw new KernelClientException(
                        wrapped.message() != null ? wrapped.message() : "Kernel request failed",
                        null,
                        wrapped.errorCode()
                );
            }
            return wrapped.data();
        } catch (KernelClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new KernelClientException("Unable to parse kernel response: " + ex.getMessage(), null, null);
        }
    }

    private void logRequest(String method, String path, Object body, UUID organizationId) {
        try {
            System.out.println(">>> KERNEL HTTP REQUEST START >>>");
            System.out.println("URL: " + properties.baseUrl() + path);
            System.out.println("Method: " + method);
            
            HttpHeaders tempHeaders = new HttpHeaders();
            applyServerHeaders(tempHeaders, organizationId);
            System.out.println("Headers: " + tempHeaders);
            
            if (body != null) {
                System.out.println("Payload: " + objectMapper.writeValueAsString(body));
            } else {
                System.out.println("Payload: [Empty]");
            }
            System.out.println(">>> KERNEL HTTP REQUEST END >>>");
        } catch (Exception ex) {
            System.err.println("Failed to log kernel request details: " + ex.getMessage());
        }
    }

    private void logRequestBootstrap(String method, String path, Object body) {
        try {
            System.out.println(">>> KERNEL BOOTSTRAP HTTP REQUEST START >>>");
            System.out.println("URL: " + properties.baseUrl() + path);
            System.out.println("Method: " + method);
            
            HttpHeaders tempHeaders = new HttpHeaders();
            applyBootstrapServerHeaders(tempHeaders);
            System.out.println("Headers: " + tempHeaders);
            
            if (body != null) {
                System.out.println("Payload: " + objectMapper.writeValueAsString(body));
            } else {
                System.out.println("Payload: [Empty]");
            }
            System.out.println(">>> KERNEL BOOTSTRAP HTTP REQUEST END >>>");
        } catch (Exception ex) {
            System.err.println("Failed to log kernel bootstrap request details: " + ex.getMessage());
        }
    }

    public ParameterizedTypeReference<KernelApiResponse<String>> stringApiResponseType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
