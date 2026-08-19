package cn.datafuturex.zhishu.testsupport.http;

import cn.datafuturex.zhishu.testsupport.report.ApiTestRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 包装 RestClient，自动写入 API 测试报告
 */
public class RecordingRestClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RecordingRestClient(RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public ResponseEntity<String> get(String path, String bearerToken) {
        return exchange(HttpMethod.GET, path, null, bearerToken, null);
    }

    public ResponseEntity<String> get(String path, String bearerToken, Map<String, ?> queryParams) {
        String fullPath = appendQuery(path, queryParams);
        return exchange(HttpMethod.GET, fullPath, null, bearerToken, null);
    }

    public ResponseEntity<String> postJson(String path, Object body, String bearerToken) {
        return exchange(HttpMethod.POST, path, body, bearerToken, MediaType.APPLICATION_JSON);
    }

    public ResponseEntity<String> putJson(String path, Object body, String bearerToken) {
        return exchange(HttpMethod.PUT, path, body, bearerToken, MediaType.APPLICATION_JSON);
    }

    public ResponseEntity<String> delete(String path, String bearerToken) {
        return exchange(HttpMethod.DELETE, path, null, bearerToken, null);
    }

    public ResponseEntity<String> postMultipart(String path, MultiValueMap<String, Resource> multipart, String bearerToken) {
        ApiTestRecorder.target("POST", path);
        ApiTestRecorder.step("发起 multipart 请求");
        ApiTestRecorder.input("method", "POST");
        ApiTestRecorder.input("path", path);
        if (bearerToken != null) {
            ApiTestRecorder.input("Authorization", "Bearer " + bearerToken);
        }
        try {
            MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            multipart.forEach((k, list) -> list.forEach(r -> body.add(k, r)));
            ResponseEntity<String> response = restClient.post()
                    .uri(path)
                    .headers(h -> {
                        if (bearerToken != null && !bearerToken.isBlank()) {
                            h.setBearerAuth(bearerToken);
                        }
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            recordResponse(response);
            return response;
        } catch (RestClientResponseException ex) {
            return recordError(ex);
        }
    }

    public ResponseEntity<String> exchange(HttpMethod method, String path, Object body, String bearerToken,
                                           MediaType contentType) {
        ApiTestRecorder.target(method.name(), path);
        ApiTestRecorder.step("发起 HTTP " + method.name() + " 请求");
        ApiTestRecorder.input("method", method.name());
        ApiTestRecorder.input("path", path);
        if (bearerToken != null) {
            ApiTestRecorder.input("Authorization", "Bearer " + bearerToken);
        }
        String bodyJson = null;
        if (body != null) {
            try {
                bodyJson = body instanceof String s ? s : objectMapper.writeValueAsString(body);
                ApiTestRecorder.input("body", bodyJson);
            } catch (Exception e) {
                ApiTestRecorder.input("body", String.valueOf(body));
            }
        }

        try {
            RestClient.RequestBodySpec spec = restClient.method(method)
                    .uri(path)
                    .headers(h -> {
                        if (bearerToken != null && !bearerToken.isBlank()) {
                            h.setBearerAuth(bearerToken);
                        }
                        if (contentType != null) {
                            h.setContentType(contentType);
                        }
                    });
            ResponseEntity<String> response;
            if (bodyJson != null) {
                response = spec.body(bodyJson).retrieve().toEntity(String.class);
            } else {
                response = spec.retrieve().toEntity(String.class);
            }
            recordResponse(response);
            return response;
        } catch (RestClientResponseException ex) {
            return recordError(ex);
        }
    }

    public JsonNode readTree(ResponseEntity<String> response) {
        try {
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("解析响应 JSON 失败: " + response.getBody(), e);
        }
    }

    public int resultCode(ResponseEntity<String> response) {
        JsonNode node = readTree(response);
        JsonNode code = node.get("code");
        return code == null || code.isNull() ? -1 : code.asInt();
    }

    private void recordResponse(ResponseEntity<String> response) {
        ApiTestRecorder.step("收到响应 HTTP " + response.getStatusCode().value());
        ApiTestRecorder.output("httpStatus", String.valueOf(response.getStatusCode().value()));
        ApiTestRecorder.output("body", response.getBody() == null ? "" : response.getBody());
    }

    private ResponseEntity<String> recordError(RestClientResponseException ex) {
        ApiTestRecorder.step("收到错误响应 HTTP " + ex.getStatusCode().value());
        ApiTestRecorder.output("httpStatus", String.valueOf(ex.getStatusCode().value()));
        ApiTestRecorder.output("body", ex.getResponseBodyAsString());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
    }

    private static String appendQuery(String path, Map<String, ?> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return path;
        }
        StringBuilder sb = new StringBuilder(path);
        sb.append(path.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, ?> e : queryParams.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
