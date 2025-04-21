package cn.daydayup.dev.mcpserver.server;

import cn.daydayup.dev.mcpserver.config.Constants;
import cn.daydayup.dev.mcpserver.web.WebSearch;
import com.alibaba.fastjson2.JSON;
import cn.daydayup.dev.connection.core.adapter.DatabaseAdapter;
import cn.daydayup.dev.connection.core.database.AbstractJdbcDataSource;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName WenShuMcpServer
 * @Description 数据探索MCP服务
 * @Author ZhaoYanNing
 * @Date 2025/4/15 18:27
 * @Version 1.0
 */
@Slf4j
@Service
public class WenShuMcpServer {

    @Resource
    private WebMvcSseServerTransportProvider transportProvider;

    @Resource
    private WebSearch webSearch;

    @PostConstruct
    public void start() {
        // 创建并配置MCP同步服务器
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("wenshu-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(true,true)
                        .prompts(true)
                        .logging()
                        .build())
                .build();

        try {
            // 添加工具、资源和提示
            syncServer.addTool(webSearch());
            syncServer.addTool(listTables());
            syncServer.addTool(getTableSchema());
            syncServer.addTool(executeMysqlQuery());
            syncServer.addPrompt(selectTablePrompt());
            syncServer.addPrompt(text2sqlPrompt());
            syncServer.addPrompt(dataAnalysisPrompt());

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

    private AbstractJdbcDataSource buildAbstractDataSource(){
        DatabaseAdapter adapter = DatabaseAdapter.getAdapter();
        adapter.setConfig(Constants.DB_CONFIG);
        return  (AbstractJdbcDataSource) adapter.getDataSource();
    }

    /**
     * 网络搜索。
     * @return McpServerFeatures.SyncToolSpecification
     */
    private McpServerFeatures.SyncToolSpecification webSearch(){
        String desc = """
                使用谷歌搜索引擎搜索关键内容。。
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
                new McpSchema.Tool("webSearch", desc, schema),
                (exchange, arguments) -> {
                    List<McpSchema.Content> result = new ArrayList<>();

                    try {
                        String question = (String) arguments.get("question");
                        String searchResult = webSearch.search(question);
                        result.add(new McpSchema.TextContent("网络搜索内容: " + searchResult));
                    }catch (Exception e){
                        // 处理计算过程中的异常
                        result.add(new McpSchema.TextContent("网络搜索内容: " + e.getMessage()));
                    }
                    return new McpSchema.CallToolResult(result, false);
                }
        );
    }

    /**
     * 获取当前数据库中所有表的列表。
     * @return McpServerFeatures.SyncToolSpecification
     */
    private McpServerFeatures.SyncToolSpecification listTables(){
        String desc = """
                获取当前数据库中所有表的列表。
                内部调用 execute_mysql_query 执行 'SHOW TABLES;'。
                """;
        String schema = """
                {
                  "type" : "object",
                  "id" : "urn:jsonschema:Operation",
                  "properties" : {
                  }
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("listTables", desc, schema),
                (exchange, arguments) -> {
                    List<McpSchema.Content> result = new ArrayList<>();

                    try {
                        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource();
                        Pair<List<String>, List<List<String>>> allTableInfo = abstractJdbcDataSource.getAllTableInfo();
                        result.add(new McpSchema.TextContent("所有的表: " + JSON.toJSONString(allTableInfo)));
                    }catch (Exception e){
                        // 处理计算过程中的异常
                        result.add(new McpSchema.TextContent("所有的表: " + e.getMessage()));
                    }
                    return new McpSchema.CallToolResult(result, false);
                }
        );
    }

    /**
     * 获取指定数据表的结构（列信息）。
     * @return McpServerFeatures.SyncToolSpecification
     */
    private McpServerFeatures.SyncToolSpecification getTableSchema(){
        String desc = """
                获取指定数据表的结构（列信息）。
                    内部调用 execute_mysql_query 执行 'DESCRIBE table_name;'。
                    Args:
                        tableName (str): 需要查询结构的数据表名称。
                    Returns:
                        str: 表结构的格式化字符串或错误信息。
                """;
        String schema = """
                {
                  "type" : "object",
                  "id" : "urn:jsonschema:Operation",
                  "properties" : {
                    "tableName" : {
                      "type" : "string"
                    }
                  }
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("getTableSchema", desc, schema),
                (exchange, arguments) -> {
                    List<McpSchema.Content> result = new ArrayList<>();
                    String tableName = (String)arguments.get("tableName");
                    try {
                        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource();
                        Pair<List<String>, List<List<String>>> columnInfo = abstractJdbcDataSource.getColumnInfo(tableName);
                        result.add(new McpSchema.TextContent("表结构: " + JSON.toJSONString(columnInfo)));
                    }catch (Exception e){
                        // 处理计算过程中的异常
                        result.add(new McpSchema.TextContent("表结构: " + e.getMessage()));
                    }
                    return new McpSchema.CallToolResult(result, false);
                }
        );
    }

    /**
     * 执行给定的 SQL 查询语句并返回结果或错误信息。
     * @return McpServerFeatures.SyncToolSpecification
     */
    private McpServerFeatures.SyncToolSpecification executeMysqlQuery(){
        String desc = """
                执行给定的 SQL 查询语句并返回结果或错误信息。
                    Args:
                        sqlQuery (str): 由 LLM 生成的 SQL 查询语句。
                    Returns:
                        str: 格式化后的查询结果字符串或错误信息字符串。
                             对于 SELECT，返回表头和数据行。
                             对于 INSERT/UPDATE/DELETE，返回影响的行数。
                             对于错误，返回具体的错误信息。
                """;
        String schema = """
                {
                  "type" : "object",
                  "id" : "urn:jsonschema:Operation",
                  "properties" : {
                    "sqlQuery" : {
                      "type" : "string"
                    }
                  }
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("executeMysqlQuery", desc, schema),
                (exchange, arguments) -> {
                    List<McpSchema.Content> result = new ArrayList<>();
                    String sqlQuery = (String)arguments.get("sqlQuery");
                    try {
                        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource();
                        Pair<List<String>, List<List<String>>> queryResult = abstractJdbcDataSource.query(sqlQuery);
                        result.add(new McpSchema.TextContent("查询结果: " + JSON.toJSONString(queryResult)));
                    }catch (Exception e){
                        // 处理计算过程中的异常
                        result.add(new McpSchema.TextContent("查询结果: " + e.getMessage()));
                    }
                    return new McpSchema.CallToolResult(result, false);
                }
        );
    }


    /**
     * 添加目标表选择提示到服务器
     */
    private McpServerFeatures.SyncPromptSpecification selectTablePrompt() {
        String selectTablePrompt = """
                ### 指令: 基于表描述信息理解用户的查询内容并推断哪些表与用户的查询属性最相关。
                ### 表信息: {tableInfo}
                ### 输入: {userInput}
                ### 输出: 返回相关度最高的表名，不要返回任何不必要的解释，多个表用逗号分隔开。
                """;
        // 创建提示规范，包含提示定义和处理逻辑
        return new McpServerFeatures.SyncPromptSpecification(
                // 定义提示的基本信息和参数
                new McpSchema.Prompt("selectTablePrompt", "目标表选择提示词模板",
                        new ArrayList<>(
                                List.of(
                                    new McpSchema.PromptArgument("tableInfo", "数据库所有的表名和表描述", true),
                                    new McpSchema.PromptArgument("userInput", "用户输入的问题", true)
                        ))),
                (exchange, request) -> {
                    // 处理提示请求
                    List<McpSchema.PromptMessage> messages = new ArrayList<>();
                    String tableInfo = (String) request.arguments().get("tableInfo");
                    String userInput = (String) request.arguments().get("userInput");

                    String finalPrompt = selectTablePrompt.replace("{tableInfo}", tableInfo).replace("{userInput}", userInput);

                    // 创建对话消息序列
                    McpSchema.PromptMessage userMessage = new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(finalPrompt));

                    messages.add(userMessage);
                    return new McpSchema.GetPromptResult("目标表选择提示词模板", messages);
                });
    }

    /**
     * 添加目标表选择提示到服务器
     */
    private McpServerFeatures.SyncPromptSpecification text2sqlPrompt() {
        String selectTablePrompt = """
                ### 指令: 熟悉MySQL数据库相关的SQL功能，你的任务是根据用户输入和表结构信息，生成符合要求且可实际执行的SQL语句。
                ### 表结构：{tableSchema}
                ### 用户输入: {userInput}
                ### 输出要求: 输出分析思路和SQL语句，输出字段必须为中文描述。
                """;
        // 创建提示规范，包含提示定义和处理逻辑
        return new McpServerFeatures.SyncPromptSpecification(
                // 定义提示的基本信息和参数
                new McpSchema.Prompt("text2sqlPrompt", "Text2SQL提示词模板",
                        new ArrayList<>(
                                List.of(
                                        new McpSchema.PromptArgument("tableSchema", "数据库表结构", true),
                                        new McpSchema.PromptArgument("userInput", "用户输入的问题", true)
                                ))),
                (exchange, request) -> {
                    // 处理提示请求
                    List<McpSchema.PromptMessage> messages = new ArrayList<>();
                    String tableSchema = (String) request.arguments().get("tableSchema");
                    String userInput = (String) request.arguments().get("userInput");

                    String finalPrompt = selectTablePrompt.replace("{tableSchema}", tableSchema).replace("{userInput}", userInput);

                    // 创建对话消息序列
                    McpSchema.PromptMessage userMessage = new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(finalPrompt));

                    messages.add(userMessage);
                    return new McpSchema.GetPromptResult("Text2SQL提示词模板", messages);
                });
    }

    /**
     * 添加数据分析提示到服务器
     */
    private McpServerFeatures.SyncPromptSpecification dataAnalysisPrompt() {
        String selectTablePrompt = """
                ### 指令: 基于查询到的相关数据和资料，进行详细的数据分析。
                ### SQL查询结果：{SQLResult}
                ### 用户输入: {userInput}
                ### 输出要求: 输出数据分析报告，要求：详细、专业、严谨。
                """;
        // 创建提示规范，包含提示定义和处理逻辑
        return new McpServerFeatures.SyncPromptSpecification(
                // 定义提示的基本信息和参数
                new McpSchema.Prompt("dataAnalysisPrompt", "数据分析报告提示词模板",
                        new ArrayList<>(
                                List.of(
                                        new McpSchema.PromptArgument("SQLResult", "数据库查询结果", true)
                                ))),
                (exchange, request) -> {
                    // 处理提示请求
                    List<McpSchema.PromptMessage> messages = new ArrayList<>();
                    String SQLResult = (String) request.arguments().get("SQLResult");

                    String finalPrompt = selectTablePrompt.replace("{SQLResult}", SQLResult);

                    // 创建对话消息序列
                    McpSchema.PromptMessage userMessage = new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(finalPrompt));

                    messages.add(userMessage);
                    return new McpSchema.GetPromptResult("数据分析报告提示词模板", messages);
                });
    }


    /**
     * 执行给定的 SQL 查询语句并返回结果或错误信息。
     * @param sql 查询语句
     * @return 查询结果
     */
    public String execute_mysql_query(String sql) {
        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource();
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            Statement statement = abstractJdbcDataSource.getConnection().createStatement();
            ResultSet resultSet = statement.executeQuery(sql);

            // 获取结果集元数据
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                // 创建一个Map来存储当前行的数据
                Map<String, Object> rowMap = new HashMap<>();
                // 遍历结果集中的列
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    // 获取列名
                    String columnName = metaData.getColumnLabel(i);
                    // 获取列值
                    Object columnValue = resultSet.getObject(i);
                    // 判断是不是小数
                    if (isStringFloatingPointNumber(String.valueOf(columnValue))){
                        // 限制小数点位数
                        rowMap.put(columnName, limitDecimalPlaces(columnValue));
                    }else {
                        // 将列名和列值放入Map
                        rowMap.put(columnName, columnValue);
                    }
                }
                // 将当前行的Map添加到结果列表中
                list.add(rowMap);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return JSON.toJSONString(list);
    }

    private boolean isStringFloatingPointNumber(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            Double.parseDouble(strNum);
            // 检查是否确实含有小数点
            return strNum.indexOf('.') >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private BigDecimal limitDecimalPlaces(Object number) {
        if (number == null) {
            throw new IllegalArgumentException("Number cannot be null");
        }
        // 将传入的对象转换为BigDecimal
        BigDecimal bigDecimalNumber = new BigDecimal(number.toString());
        // 获取小数部分的位数
        int scale = bigDecimalNumber.scale();
        // 如果小数部分超过4位，则进行处理
        if (scale > 4) {
            // 设置最多四位小数，并且定义舍入模式
            bigDecimalNumber = bigDecimalNumber.setScale(4, RoundingMode.HALF_UP);
        }
        return bigDecimalNumber;
    }
}
