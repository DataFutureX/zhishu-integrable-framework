package cn.datafuturex.yunqi.testsupport.report;

/**
 * 测试用例内记录目标接口、输入、输出与过程步骤
 */
public final class ApiTestRecorder {

    private ApiTestRecorder() {
    }

    public static void target(String httpMethod, String path) {
        ApiTestCaseRecord record = ApiTestContext.get();
        if (record != null) {
            record.setTargetApi(httpMethod + " " + path);
            record.getSteps().add("目标接口: " + record.getTargetApi());
        }
    }

    public static void step(String message) {
        ApiTestCaseRecord record = ApiTestContext.get();
        if (record != null) {
            record.getSteps().add(message);
        }
    }

    public static void input(String key, String value) {
        ApiTestCaseRecord record = ApiTestContext.get();
        if (record != null) {
            record.getInput().put(key, maskIfSensitive(key, value));
        }
    }

    public static void output(String key, String value) {
        ApiTestCaseRecord record = ApiTestContext.get();
        if (record != null) {
            record.getOutput().put(key, value);
        }
    }

    private static String maskIfSensitive(String key, String value) {
        if (value == null) {
            return null;
        }
        String lower = key == null ? "" : key.toLowerCase();
        if (lower.contains("password") || lower.contains("authorization") || lower.contains("token")) {
            if (value.length() <= 12) {
                return "***";
            }
            return value.substring(0, 8) + "***";
        }
        return value;
    }
}
