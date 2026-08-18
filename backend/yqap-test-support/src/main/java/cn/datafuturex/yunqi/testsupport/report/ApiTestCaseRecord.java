package cn.datafuturex.yunqi.testsupport.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单条 API 测试用例报告记录
 */
public class ApiTestCaseRecord {

    private String className;
    private String displayName;
    private String methodName;
    private String targetApi;
    private final Map<String, String> input = new LinkedHashMap<>();
    private final Map<String, String> output = new LinkedHashMap<>();
    private final List<String> steps = new ArrayList<>();
    private boolean passed;
    private String errorMessage;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getTargetApi() {
        return targetApi;
    }

    public void setTargetApi(String targetApi) {
        this.targetApi = targetApi;
    }

    public Map<String, String> getInput() {
        return input;
    }

    public Map<String, String> getOutput() {
        return output;
    }

    public List<String> getSteps() {
        return steps;
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
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
