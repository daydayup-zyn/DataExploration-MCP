package cn.daydayup.dev.mcpserverplus.agent;

import cn.daydayup.dev.connection.core.adapter.DatabaseAdapter;
import cn.daydayup.dev.connection.core.database.AbstractJdbcDataSource;
import cn.daydayup.dev.mcpserverplus.invoke.TargetTable;
import cn.daydayup.dev.mcpserverplus.invoke.Text2SQL;
import com.alibaba.fastjson.JSON;
import dev.langchain4j.model.chat.ChatModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * @ClassName SqlQueryAgent
 * @Description 数据库查询智能体
 * @Author ZhaoYanNing
 * @Date 2025/5/13 13:21
 * @Version 1.0
 */
public class SqlQueryAgent implements AgentNode{

    private static final Logger log = LoggerFactory.getLogger(SqlQueryAgent.class);
    private static final String FINAL_RESPONSE = "agent_response";

    private final ChatModel chatModel;
    private final String dbConfig;

    public SqlQueryAgent(ChatModel chatModel,String dbConfig) {
        this.chatModel = chatModel;
        this.dbConfig = dbConfig;
    }

    private final static String SQL_OPTIMIZE_PROMPT = """
            \n
            以上SQL语句执行结果数据量较大，请改写此SQL语句。你可以采用以下手段优化：
            1. 检查是否缺少了必要的where筛选条件，导致返回了过多无关数据；
            2. 剔除非必要字段，仅保留与用户业务需求相关的列；
            3. 在不影响最终分析结果的前提下，考虑使用聚合、分组或ROW_NUMBER() OVER分组排序取前5条数据等方式来减少输出；
                如：一个售电量表中有地区、年份、月份、行业、售电量等字段
                用户出入：查询一下2024年阿勒泰全年各个月的行业售电量是多少
                用户查询的是每个月的行业售电量情况，则应年份和地区为筛选条件，以月份分组，以售电量排序，取每个分组的前5条数据（即每个月排名前5的行业）
                SELECT
                  *\s
                FROM
                  (
                    SELECT
                      power_month AS 周期月,
                      hy_name AS 行业名称,
                      power_num AS 真实值亿 kWh,
                      ROW_NUMBER( ) OVER ( PARTITION BY power_month ORDER BY power_num DESC ) AS rn\s
                    FROM
                      tb_dlyc_area_hy_month\s
                    WHERE
                      org_name = '阿勒泰'\s
                      AND power_year = '2024'\s
                  ) t\s
                WHERE
                  rn <=5
            \n
            """;

    /**
     * 列出数据库中的所有表
     * @return
     */
    private Map<String, Object> listTables(AgentState state) {
        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource(dbConfig);
        Pair<List<String>, List<List<String>>> allTableInfo = abstractJdbcDataSource.getAllTableInfo();
        log.info("[SqlQueryAgent] allTableInfo:{}" , convert(allTableInfo));
        return Map.of("listTables",JSON.toJSONString(convert(allTableInfo)));
    }

    /**
     * 获取表的结构信息
     * @param state
     * @return
     */
    private Map<String, Object> getTableSchema(AgentState state) {
        List<Map<String,Object>> columnInfos = new ArrayList<>();
        String listTables = (String)state.value("listTables").orElseThrow();
        String question = (String) state.value("question").orElseThrow();
        List<String> targetTable = new TargetTable(chatModel).apply(listTables, question);
        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource(dbConfig);
        for (String table : targetTable) {
            Map<String,Object> tableInfo = new HashMap<>();

            Pair<List<String>, List<List<String>>> columnInfo = abstractJdbcDataSource.getColumnInfo(table);
            tableInfo.put("tableName",table);
            tableInfo.put("columnInfo",convert(columnInfo));
            tableInfo.put("sampleData",convert(getSampleData(abstractJdbcDataSource,table)));
            columnInfos.add(tableInfo);
        }
        log.info("[SqlQueryAgent] columnInfos:{}" , columnInfos);
        return Map.of("schema", JSON.toJSONString(columnInfos));
    }

    /**
     * 文本转SQL
     * @param state
     * @return
     */
    private Map<String, Object> text2Sql(AgentState state) {
        String question = (String) state.value("question").orElseThrow();
        String schema = (String)state.value("schema").orElseThrow();
        String preInspection = (String)state.value("preInspection").orElse("");
        preInspection = StringUtils.isEmpty(preInspection) ? "" : preInspection+SQL_OPTIMIZE_PROMPT;
        String text2sql = new Text2SQL(chatModel).apply(schema,question+preInspection);
        log.info("[SqlQueryAgent] text2sql:{}", text2sql);
        return Map.of("sql", text2sql);
    }

    /**
     * SQL预检查
     * @param state
     * @return
     */
    private Map<String,Object> preInspection(AgentState state){
        String sql = (String)state.value("sql").orElseThrow();
        sql = getSqlFromCodeBlock(sql);
        List<String> sqlList = extractSqlQueries(sql);
        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource(dbConfig);
        Map<String,Object> sqls = new HashMap<>();
        try {
            for (String sqlInfo : sqlList) {
                Pair<List<String>, List<List<String>>> queryResult = abstractJdbcDataSource.query(sqlInfo);
                List<Map<String, Object>> convert = convert(queryResult);
                // 如果数据量过大或者字符串过长
                if (JSON.toJSONString(convert).length() > 4096) {
                    sqls.put(sql, "SQL执行结果数据量过大："+JSON.toJSONString(convert).length());
                }
            }
            log.info("[SqlQueryAgent] preInspection:{}", JSON.toJSONString(sqls));
            if (sqls.isEmpty()){
                return Map.of("preInspection", "continue");
            }else {
                return Map.of("preInspection", JSON.toJSONString(sqls));
            }
        }catch (Exception e){
            return Map.of("preInspection", "SQL执行失败，报错信息："+e.getMessage());
        }
    }

    /**
     * 执行SQL执行SQL
     * @param state
     * @return
     */
    private Map<String, Object> executeSql(AgentState state) {
        String sql = (String)state.value("sql").orElseThrow();
        sql = getSqlFromCodeBlock(sql);
        List<String> sqlList = extractSqlQueries(sql);
        AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource(dbConfig);
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (String sqlInfo : sqlList) {
                Pair<List<String>, List<List<String>>> queryResult = abstractJdbcDataSource.query(sqlInfo);
                List<Map<String, Object>> convert = convert(queryResult);
                result.addAll(convert);
            }
            log.info("[SqlQueryAgent] queryResult:{}", JSON.toJSONString(result));
            return Map.of(FINAL_RESPONSE, JSON.toJSONString(result));
        }catch (Exception e){
            return Map.of(FINAL_RESPONSE, "SQL执行失败，报错信息："+e.getMessage());
        }
    }

    public String isPreInspectionPass(AgentState state) {
        String preInspection = (String) state.value("preInspection").orElseThrow();
        if ("continue".equals(preInspection)) {
            log.info("[SqlQueryAgent] preInspection passed. Next step is <executeSql>.");
            return "executeSql";
        }
        log.warn("[SqlQueryAgent] preInspection not passed,Agent continue...");
        return "text2Sql";
    }

    public String isDataExist(AgentState state) {
        String result = (String) state.value(FINAL_RESPONSE).orElseThrow();
        if (StringUtils.isNotBlank(result)) {
            log.info("[SqlQueryAgent] exist result. Next step is <END>.");
            return "end";
        }
        log.warn("[SqlQueryAgent] No result,Agent continue...");
        return "continue";
    }

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

    private AbstractJdbcDataSource buildAbstractDataSource(String dbConfig){
        DatabaseAdapter adapter = DatabaseAdapter.getAdapter();
        adapter.setConfig(dbConfig);
        return  (AbstractJdbcDataSource) adapter.getDataSource();
    }

    private Pair<List<String>, List<List<String>>> getSampleData(AbstractJdbcDataSource abstractJdbcDataSource,String tableName){
        //AbstractJdbcDataSource abstractJdbcDataSource = buildAbstractDataSource();
        return abstractJdbcDataSource.query("select * from " + tableName + " limit 3;");
    }

    public String getSqlFromCodeBlock(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // 去除开头的 ```sql 或 ```任何语言
        String result = input.trim();
        if (result.contains("```sql") && result.contains("```")) {
            // 正则表达式匹配 ```json 和 ``` 之间的内容（包括换行）
            Pattern pattern = Pattern.compile("```sql\\n(.*?)\\n```", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                result = matcher.group(1);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> convert(Pair<List<String>, List<List<String>>> pair) {
        List<String> headers = pair.getKey();
        List<List<String>> rows = pair.getValue();
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<String> row : rows) {
            Map<String, Object> map = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String value = row.get(i);
                map.put(header, value);
            }
            result.add(map);
        }
        return result;
    }

    private static List<String> extractSqlQueries(String input) {
        List<String> queries = new ArrayList<>();
        StringBuilder currentQuery = new StringBuilder();
        boolean insideBlockComment = false;
        int curlyBracesDepth = 0;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '#' && !insideBlockComment) {
                // 处理 # 注释：跳过该行剩余字符
                while (i < input.length() && input.charAt(i) != '\n') {
                    i++;
                }
            }else if (ch == '-' && i + 1 < input.length() && input.charAt(i + 1) == '-') {
                while (i < input.length() && input.charAt(i) != '\n') {
                    i++;
                }
            } else if (ch == '/' && i + 1 < input.length() && input.charAt(i + 1) == '*') {
                insideBlockComment = true;
                i++;
            } else if (ch == '*' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
                insideBlockComment = false;
                i++;
            } else if (!insideBlockComment) {
                if (ch == '{') {
                    curlyBracesDepth++;
                } else if (ch == '}') {
                    curlyBracesDepth--;
                } else if (ch == ';' && curlyBracesDepth == 0) {
                    if (currentQuery.length() > 0) {
                        queries.add(currentQuery.toString().trim());
                    }
                    currentQuery.setLength(0);
                } else {
                    currentQuery.append(ch);
                }
            }
        }

        return queries;
    }

    private static boolean isWithStatement(String query) {
        // Simple check to see if the query starts with WITH (case insensitive)
        return query.trim().toLowerCase().startsWith("with");
    }
}
