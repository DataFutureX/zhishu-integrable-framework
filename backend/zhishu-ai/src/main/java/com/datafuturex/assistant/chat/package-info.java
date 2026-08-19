/**
 * 对话、会话、问答历史、观测。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Chat",
        allowedDependencies = {
                "shared",
                "agent :: api",
                "knowledge :: api",
                "biztools :: api",
                "modelconfig :: api"
        }
)
package com.datafuturex.assistant.chat;
