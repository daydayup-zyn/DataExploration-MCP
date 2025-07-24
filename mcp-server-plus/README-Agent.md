## 基于LangGraph4j开发的Agent智能体MCP服务器

下面是使用LangGraph4j开发的一个简单的Agent智能体MCP服务器。

## 安装依赖

将以下 Maven 依赖添加到你的项目中：

使用MCP官方的Java SDK：

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.8.1</version>
</dependency>
```

对于 HTTP SSE 传输实现，添加以下依赖之一：

```xml
<!-- 基于 Spring WebFlux 的 SSE 客户端和服务器传输 -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webflux</artifactId>
</dependency>

<!-- 基于 Spring WebMVC 的 SSE 服务器传输 -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-spring-webmvc</artifactId>
</dependency>
```

添加数据库适配器，参考我的另外一个项目：https://github.com/daydayup-zyn/db-connector
```xml
<dependency>
    <groupId>cn.daydayup.dev</groupId>
    <artifactId>connector-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

添加LangGraph4j相关的依赖：

```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-core</artifactId>
    <version>1.6.0-rc1</version>
</dependency>

<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-core</artifactId>
    <version>1.6.0-rc1</version>
</dependency>

<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-langchain4j</artifactId>
    <version>1.6.0-rc1</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.1.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.1.0</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>1.1.0-beta7</version>
</dependency>
```

## 服务端对象

服务器同时支持同步和异步API，这里我们选择创建同步服务器。

```java
// 创建并配置MCP同步服务器
McpSyncServer syncServer = McpServer.sync(transportProvider)
        .serverInfo("wenshu-server", "1.0.0")
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
            .level(McpSchema.LoggingLevel.INFO)
            .logger("custom-logger")
            .data("Server initialized")
            .build());
}catch (Exception e){
    // 处理异常，例如打印错误信息
    log.error("MCP服务器创建时发生错误: {}" , e.getMessage());
    e.printStackTrace();
}
```

## 服务器传输

创建基于WebMvc的SSE服务器传输，需要 mcp-spring-webmvc 依赖。

```java
@Configuration
public class McpConfig{

    @Bean
    WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(ObjectMapper mapper) {
        return new WebMvcSseServerTransportProvider(mapper, "/messages");
    }

    @Bean
    RouterFunction<ServerResponse> mcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
```

如使用基于WebFlux的SSE服务器传输，则需要 mcp-spring-webflux 依赖。

```java
@Configuration
class McpConfig {
    @Bean
    WebFluxSseServerTransport webFluxSseServerTransport(ObjectMapper mapper) {
        return new WebFluxSseServerTransport(mapper, "/messages");
    }

    @Bean
    RouterFunction<?> mcpRouterFunction(WebFluxSseServerTransport transport) {
        return transport.getRouterFunction();
    }
}
```

以上WebMvcSseServerTransportProvider或者WebFluxSseServerTransport中的`/messages`​可以任意写，只要和项目中已有的URI不冲突就行。

## 服务器功能

### 智能体
1. 使用LangGraph4j开发一个智能体。

```java
@Override
public StateGraph<AgentState> buildGraph() throws Exception {
    return new StateGraph<>(new LC4jStateSerializer<>(AgentState::new))
            .addNode("listTables", node_async(this::listTables))
            .addNode("getTableSchema", node_async(this::getTableSchema))
            .addNode("text2Sql", node_async(this::text2Sql))
            .addNode("preInspection", node_async(this::preInspection))
            .addNode("executeSql", node_async(this::executeSql))
            .addEdge(START, "listTables")
            .addEdge("listTables", "getTableSchema")
            .addEdge("getTableSchema", "text2Sql")
            .addEdge("text2Sql", "preInspection")
            .addConditionalEdges("preInspection",
                    edge_async(this::isPreInspectionPass),
                    Map.of("executeSql","executeSql","text2Sql","text2Sql")
            )
            .addConditionalEdges("executeSql",
                    edge_async(this::isDataExist),
                    Map.of("continue", "text2Sql", "end", END)
            );
}
```

2. 构建StateGraph状态图和CompiledGraph编译图：

```java
@Bean
public AgentNode sqlQueryAgent(@Qualifier("chatModel") ChatModel chatModel){
    return new SqlQueryAgent(chatModel,Constants.MYSQL_CONFIG);
}

@Bean(value = "sqlQueryAgentGraph")
public CompiledGraph<AgentState> sqlQueryAgentGraph(@Qualifier("sqlQueryAgent") AgentNode sqlQueryAgent)
        throws Exception {

    return sqlQueryAgent.buildGraph().compile();
}
```

3. 运行智能体，获取结果：

```java
private String runAgent(String query){
    Optional<AgentState> queryResult = sqlQueryAgentGraph.invoke(
            Map.of(
                    "question", query
            )
    );
    return String.valueOf(queryResult.get().value(FINAL_RESPONSE).get());
}
```

4. 将智能体作为一个工具添加到MCP服务中：

```java
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
```

