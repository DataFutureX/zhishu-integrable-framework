package cn.datafuturex.zhishu.testsupport.report;

/**
 * 当前线程的 API 测试记录上下文
 */
public final class ApiTestContext {

    private static final ThreadLocal<ApiTestCaseRecord> CURRENT = new ThreadLocal<>();

    private ApiTestContext() {
    }

    public static void set(ApiTestCaseRecord record) {
        CURRENT.set(record);
    }

    public static ApiTestCaseRecord get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
