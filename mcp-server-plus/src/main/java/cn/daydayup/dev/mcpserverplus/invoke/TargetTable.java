package cn.daydayup.dev.mcpserverplus.invoke;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.function.BiFunction;

/**
 * @ClassName TargetTable
 * @Description 确定目标表
 * @Author ZhaoYanNing
 * @Date 2025/5/13 13:43
 * @Version 1.0
 */
public record TargetTable (ChatModel chatModel) implements BiFunction<String, String, List<String>> {

    /**
     * 服务接口
     */
    interface LLMService {

        /**
         * 确定目标表
         *
         * @param tableInfo 表信息
         * @param userInput 用户输入
         * @return 目标表名
         */
        @UserMessage("""
                ### 指令: 基于表描述信息理解用户的查询内容并推断哪些表与用户的查询属性最相关。
                ### 表信息: {{tableInfo}}
                ### 输入: {{userInput}}
                ### 输出: 返回相关度最高的表名，不要返回任何不必要的解释。
                """)
        List<String> invoke(@V("tableInfo") String tableInfo, @V("userInput") String userInput);
    }

    @Override
    public List<String> apply(String tableInfo, String userInput) {
        LLMService service = AiServices.create(LLMService.class, chatModel);
        return service.invoke(tableInfo, userInput);
    }
}
