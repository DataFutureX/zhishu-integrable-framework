/**
 * 监测域只读查询与 Tool Calling 适配。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "BizTools",
        allowedDependencies = {"shared", "kg :: api"}
)
package com.datafuturex.assistant.biztools;
