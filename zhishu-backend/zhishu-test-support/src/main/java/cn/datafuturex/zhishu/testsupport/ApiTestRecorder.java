package cn.datafuturex.zhishu.testsupport;

/**
 * 接口测试过程录制静态入口（基于 ThreadLocal）。
 */
public final class ApiTestRecorder {

    private static final ThreadLocal<ApiTestCaseRecord> CURRENT = new ThreadLocal<>();

    private ApiTestRecorder() {
    }

    static void begin(ApiTestCaseRecord record) {
        CURRENT.set(record);
    }

    static ApiTestCaseRecord current() {
        return CURRENT.get();
    }

    static ApiTestCaseRecord end() {
        ApiTestCaseRecord record = CURRENT.get();
        CURRENT.remove();
        return record;
    }

    public static void step(String description) {
        ApiTestCaseRecord record = CURRENT.get();
        if (record != null) {
            record.addStep(description);
        }
    }

    public static void target(String httpMethod, String path) {
        ApiTestCaseRecord record = CURRENT.get();
        if (record != null) {
            record.setHttpMethod(httpMethod);
            record.setPath(path);
            record.addStep("设定目标接口: " + httpMethod + " " + path);
        }
    }

    public static void input(String params, String headers, String body) {
        ApiTestCaseRecord record = CURRENT.get();
        if (record != null) {
            if (params != null) {
                record.setRequestParams(params);
            }
            if (headers != null) {
                record.setRequestHeaders(headers);
            }
            if (body != null) {
                record.setRequestBody(body);
            }
            record.addStep("记录请求输入");
        }
    }

    public static void output(int status, String body) {
        ApiTestCaseRecord record = CURRENT.get();
        if (record != null) {
            record.setResponseStatus(status);
            record.setResponseBody(body);
            record.addStep("记录响应输出: HTTP " + status);
        }
    }
}
