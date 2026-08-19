package cn.datafuturex.zhishu.testsupport;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条接口测试用例报告数据。
 */
public class ApiTestCaseRecord {

    private String displayName;
    private String className;
    private String methodName;
    private String httpMethod;
    private String path;
    private String requestParams = "";
    private String requestHeaders = "";
    private String requestBody = "";
    private Integer responseStatus;
    private String responseBody = "";
    private final List<String> steps = new ArrayList<>();
    private boolean passed;
    private String errorMessage = "";
    private long durationMs;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTargetApi() {
        if (httpMethod == null && path == null) {
            return "-";
        }
        return (httpMethod == null ? "" : httpMethod) + " " + (path == null ? "" : path);
    }

    public String getRequestParams() {
        return requestParams;
    }

    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams == null ? "" : requestParams;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders == null ? "" : requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody == null ? "" : requestBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void addStep(String step) {
        if (step != null && !step.isBlank()) {
            steps.add(step);
        }
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
