package cn.daydayup.dev.mcpserverplus.server;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @ClassName WenShuPlusMcpServer
 * @Description 问数智能体MCP服务器
 * @Author ZhaoYanNing
 * @Date 2025/7/23 18:00
 * @Version 1.0
 */
@Slf4j
@Service
public class WenShuPlusMcpServer {

    @Resource
    private WebMvcSseServerTransportProvider transportProvider;

    @Resource(name = "sqlQueryAgentGraph")
    private CompiledGraph<AgentState> sqlQueryAgentGraph;

    public static final String FINAL_RESPONSE = "agent_response";

    @PostConstruct
    public void start() {
        // 创建并配置MCP同步服务器
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("Mcp-Server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .build();

        try {
            // 添加工具、资源和提示
            syncServer.addTool(sqlQueryAgent());

            // 发送日志通知
            syncServer.loggingNotification(McpSchema.LoggingMessageNotification.builder()
                    .level(McpSchema.LoggingLevel.DEBUG)
                    .logger("custom-logger")
                    .data("Server initialized")
                    .build());
        }catch (Exception e){
            // 处理异常，例如打印错误信息
            log.error("创建JSON Schema时发生错误: {}" , e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 智能体封装成一个工具
     * @return McpServerFeatures.SyncToolSpecification
     */
    private McpServerFeatures.SyncToolSpecification sqlQueryAgent(){
        String desc = """
                使用Text2SQL智能问数智能体查询数据库内容。
                """;
        String schema = """
                {
                  "type" : "object",
                  "id" : "urn:jsonschema:Operation",
                  "properties" : {
                    "question" : {
                      "type" : "string"
                    }
                  }
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("sqlQueryAgent", desc, schema),
                (exchange, arguments) -> {
                    List<McpSchema.Content> result = new ArrayList<>();

                    try {
                        String question = (String) arguments.get("question");
                        String finalResult = runAgent(question);
                        result.add(new McpSchema.TextContent("结果内容: " + finalResult));
                        return new McpSchema.CallToolResult(result, false);
                    }catch (Exception e){
                        // 处理计算过程中的异常
                        result.add(new McpSchema.TextContent("结果内容: " + e.getMessage()));
                        return new McpSchema.CallToolResult(result, true);
                    }
                }
        );
    }

    /**
     * 运行智能体
     * @param query 智能体输入参数
     * @return 智能体返回结果
     */
    private String runAgent(String query){
        Optional<AgentState> queryResult = sqlQueryAgentGraph.invoke(
                Map.of(
                        "question", query
                )
        );
        return String.valueOf(queryResult.get().value(FINAL_RESPONSE).get());
    }
}
