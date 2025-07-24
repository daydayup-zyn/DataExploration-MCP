package cn.daydayup.dev.mcpserverplus.invoke;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @ClassName Text2SQL
 * @Description SQL语句生成
 * @Author ZhaoYanNing
 * @Date 2025/5/13 14:10
 * @Version 1.0
 */
public record Text2SQL (ChatModel chatModel) {

    /**
     * 服务接口
     */
    interface LLMService {

        /**
         * 生成SQL
         * @param tableSchema 表信息
         * @param userInput 用户输入
         * @return SQL语句
         */
        @UserMessage("""
                ### 指令: 熟悉MySQL数据库相关的SQL功能，你的任务是根据用户输入和表结构信息，生成符合要求且可实际执行的SQL语句。
                ### 表结构：{{tableSchema}}
                ### 用户输入: {{userInput}}
                ### 输出要求: 直接输出最终的SQL语句，不要包含任何解释、注释和分析内容；使用as关键字将英文字段名转为中文描述，中文描述使用英文单引号包裹；结果以Markdown格式展示SQL代码块。
                ### 示例：
                ```sql
                    select
                        id as '编号',
                        name as '姓名'
                    from
                        users_table
                    where age = 30;
                ```
                """)
        String invoke(@V("tableSchema") String tableSchema, @V("userInput") String userInput);
    }

    public String apply(String tableSchema,String userInput) {
        LLMService service = AiServices.create(LLMService.class, chatModel);
        return service.invoke(tableSchema,userInput);
    }
}
