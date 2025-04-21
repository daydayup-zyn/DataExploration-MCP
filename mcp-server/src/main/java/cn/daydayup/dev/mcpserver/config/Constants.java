package cn.daydayup.dev.mcpserver.config;

/**
 * @ClassName Constants
 * @Description 常量类
 * @Author ZhaoYanNing
 * @Date 2025/4/21 10:47
 * @Version 1.0
 */
public class Constants {

    /**
     * mysql
     */
    public static final String DB_CONFIG = """
            {   "username":"root",
                "password":"123456",
                "type":"mysql",
                "host":"10.8.10.xxx",
                "port":"3306",
                "schema":"xxx",
                "driver-class-name":"com.mysql.jdbc.Driver",
                "jdbcUrl":"jdbc:mysql://10.8.10.xxx:3306/xxx?characterEncoding=utf-8&serverTimezone=UTC&useSSL=false"
            }
            """;

    public static final String WEB_SEARCH_KEY = """
            48d1bd8f7419387ef582b7f04149ac47ffe45xxxxxxxxxxxxxxxxxxxxxx
            """;
}
