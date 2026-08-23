package cn.datafuturex.zhishu.ai.platform.ai;

import cn.datafuturex.zhishu.ai.biztools.api.BizToolProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Spring AI 配置。
 * <p>
 * Spring AI 2.0.0 在「流式 + Tool Calling」时 ChunkMerger 可能抛 NoSuchElementException
 * （通义等 OpenAI 兼容端常见）。因此拆成：带 Tools 的主客户端 + 无 Tools 的流式客户端。
 */
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    public static final String DEFAULT_MODEL = "qwen-plus";
    public static final int MEMORY_WINDOW_SIZE = 20;
    public static final String STREAM_CHAT_CLIENT = "streamChatClient";

    private final EmbeddingModel embeddingModel;
    private final BizToolProviderPort bizToolProviderPort;

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(MEMORY_WINDOW_SIZE)
                .build();
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_WITH_TOOLS)
                .defaultOptions(defaultOptions())
                .defaultTools(bizToolProviderPort.toolBeans())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 无 Tools，供文档问答真流式使用，避开流式 Tool 聚合缺陷。
     */
    @Bean(name = STREAM_CHAT_CLIENT)
    public ChatClient streamChatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_DOC_QA)
                .defaultOptions(defaultOptions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public VectorStore vectorStore(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .dimensions(1024)
                .build();
    }

    private static OpenAiChatOptions.Builder defaultOptions() {
        return OpenAiChatOptions.builder()
                .model(DEFAULT_MODEL)
                .temperature(0.7)
                .maxTokens(2000);
    }

    private static final String SYSTEM_WITH_TOOLS = """
            你是「数智未来AI助手」，服务万象监测平台，擅长数据分析与智能问答。

            你可以使用工具查询：
            1. 遥测站最新/历史监测要素
            2. 遥测站在线状态与列表
            3. 工程（项目）列表
            4. 近期阈值告警
            5. 巡检计划 / 任务 / 异常（只读）

            使用规则：
            - 「最新/当前/实时」→ queryStationLatestElements
            - 「历史/趋势/某段时间」→ queryStationHistoryElements（时间 yyyy-MM-dd HH:mm:ss）
            - 「在线/离线/站点列表/在线状态概览」→ getTerminalOnlineOverview 或 listTerminals / queryTerminalOnlineStatus
            - 「工程/项目」→ listProjects
            - 「告警」→ queryRecentAlerts
            - 询问「全部遥测站在线状态」时必须调用 getTerminalOnlineOverview，用返回的 total/onlineCount/items 生成表格，禁止编造空表
            - 用户未指定日期时：日报=当日、月报=当月、年报=当年（以系统注入的当前时间为准，禁止臆造日期）
            - 相对时间（今日、昨天、上周、过去一个月）请按系统注入时间换算为具体时间范围
            - 优先用工具取真实数据，再用中文简洁专业地总结
            - 不确定时明确说明
            """;

    private static final String SYSTEM_DOC_QA = """
            你是「数智未来AI助手」的文档问答模式。请基于用户消息与上下文严谨作答，使用中文。
            """;
}
