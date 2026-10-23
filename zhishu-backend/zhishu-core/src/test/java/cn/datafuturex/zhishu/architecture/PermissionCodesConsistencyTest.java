package cn.datafuturex.zhishu.architecture;

import cn.datafuturex.zhishu.modules.constant.PermissionConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限码一致性守卫：{@link PermissionConstants} 里的每个权限码都必须由 db 脚本创建。
 * <p>
 * 背景：{@code @PreAuthorize} 的判据来自 {@code sys_menu}（{@code menu_type=BUTTON}，
 * 权限码存 {@code route_name} 列），由 ADMIN 之外的角色经 {@code sys_role_menu} 获得。
 * 因此「加了注解但没建权限码」会让非 ADMIN 直接 403，「手工在库里插权限码而脚本没有」
 * 会让新环境缺权限 —— 两者都只能靠这道断言拦住。
 * <p>
 * 方向性说明：只校验「常量 → 脚本」，不校验「脚本 → 常量」；
 * 库里允许存在仅供前端按钮显隐使用、后端未加注解的权限码。
 */
class PermissionCodesConsistencyTest {

    @Test
    @DisplayName("每个权限码常量都能在 db 脚本中找到对应 BUTTON 行")
    void everyPermissionCodeIsCreatedByScripts() throws Exception {
        String scripts = loadAllDbScripts();
        assertFalse(scripts.isEmpty(), "未读到任何 db/*.sql 脚本，检查 classpath 资源路径");

        List<String> missing = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        for (Field field : PermissionConstants.class.getFields()) {
            if (field.getType() != String.class) {
                continue;
            }
            String code = (String) field.get(null);
            // 只关心形如 ai:agent:add 的权限码，跳过 MENU_TYPE_BUTTON / ROLE_ADMIN 等枚举值
            if (code == null || code.indexOf(':') < 0) {
                continue;
            }
            checked.add(code);
            if (!scripts.contains("'" + code + "'")) {
                missing.add(field.getName() + " = " + code);
            }
        }

        assertTrue(checked.size() > 30, "权限码常量数量异常（" + checked.size() + "），确认反射是否生效");
        assertTrue(missing.isEmpty(),
                "以下权限码没有任何 db 脚本创建（会导致非 ADMIN 用户 403）：" + System.lineSeparator()
                        + "  " + String.join(System.lineSeparator() + "  ", missing));
    }

    private String loadAllDbScripts() throws Exception {
        StringBuilder text = new StringBuilder();
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/*.sql");
        for (Resource resource : resources) {
            try (var in = resource.getInputStream()) {
                text.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return text.toString();
    }
}

