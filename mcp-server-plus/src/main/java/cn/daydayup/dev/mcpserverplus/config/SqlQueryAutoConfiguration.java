package cn.daydayup.dev.mcpserverplus.config;

import cn.daydayup.dev.mcpserverplus.agent.AgentNode;
import cn.daydayup.dev.mcpserverplus.agent.SqlQueryAgent;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName DatabaseQueryAutoConfiguration
 * @Description 数据库查询智能体配置类
 * @Author ZhaoYanNing
 * @Date 2025/5/13 14:35
 * @Version 1.0
 */
@Configuration
@RequiredArgsConstructor
public class SqlQueryAutoConfiguration {

    @Value("${llm.dashscope.chat-model}")
    private String modelName;

    @Value("${llm.dashscope.api-key}")
    private String apiKey;

    @Bean
    public ChatModel chatModel(){
        return QwenChatModel
                .builder()
                .modelName(modelName)
                .apiKey(apiKey)
                .temperature(0.1f)
                .build();
    }

    @Bean
    public AgentNode sqlQueryAgent(@Qualifier("chatModel") ChatModel chatModel){
        return new SqlQueryAgent(chatModel,Constants.MYSQL_CONFIG);
    }

    @Bean(value = "sqlQueryAgentGraph")
    public CompiledGraph<AgentState> sqlQueryAgentGraph(@Qualifier("sqlQueryAgent") AgentNode sqlQueryAgent)
            throws Exception {

        return sqlQueryAgent.buildGraph().compile();
    }
}
