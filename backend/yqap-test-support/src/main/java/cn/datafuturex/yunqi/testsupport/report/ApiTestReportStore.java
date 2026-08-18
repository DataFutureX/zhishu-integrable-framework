package cn.datafuturex.yunqi.testsupport.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 进程内汇总全部 API 用例报告记录
 */
public final class ApiTestReportStore {

    private static final List<ApiTestCaseRecord> RECORDS = Collections.synchronizedList(new ArrayList<>());

    private ApiTestReportStore() {
    }

    public static void add(ApiTestCaseRecord record) {
        RECORDS.add(record);
    }

    public static List<ApiTestCaseRecord> snapshot() {
        synchronized (RECORDS) {
            return new ArrayList<>(RECORDS);
        }
    }

    public static void clear() {
        RECORDS.clear();
    }
}
