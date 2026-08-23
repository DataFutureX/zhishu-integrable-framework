package cn.datafuturex.zhishu.ai.agent.graph;

import java.util.ArrayList;
import java.util.List;

public record GraphValidationResult(boolean valid, List<String> errors) {
    public static GraphValidationResult ok() {
        return new GraphValidationResult(true, List.of());
    }

    public static GraphValidationResult fail(List<String> errors) {
        return new GraphValidationResult(false, errors == null ? List.of() : List.copyOf(errors));
    }

    public static GraphValidationResult fail(String error) {
        List<String> list = new ArrayList<>();
        list.add(error);
        return fail(list);
    }
}
