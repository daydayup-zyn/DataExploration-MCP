package cn.daydayup.dev.mcpserverplus.config;

/**
 * @ClassName Constants
 * @Description 常量类
 * @Author ZhaoYanNing
 * @Date 2025/4/21 10:47
 * @Version 1.0
 */
public class Constants {

    public static final String MYSQL_CONFIG = """
            {
                "username":"xxx",
                "password":"xxx",
                "type":"mysql",
                "host":"10.8.10.xxx",
                "port":"3306",
                "schema":"xxx",
                "driver-class-name":"com.mysql.jdbc.Driver",
                "jdbcUrl":"jdbc:mysql://10.8.10.xxx:3306/xxx?characterEncoding=utf-8&serverTimezone=UTC&useSSL=false"
            }
            """;

    /**
     * oracle连接信息
     */
    public static final String ORACLE_CONFIG = """
            {
                "username": "xxx",
                "password": "xxx",
                "type": "oracle",
                "host": "10.8.10.xxx",
                "port": "1521",
                "schema": "xxx",
                "space":"xxx",
                "driver-class-name": "oracle.jdbc.OracleDriver",
                "jdbcUrl": "jdbc:oracle:thin:@10.8.10.xxx:1521:xxx"
            }
            """;

    /**
     * postgresql连接信息
     */
    public static final String PG_CONFIG = """
            {
                "username":"postgres",
                "password":"postgres",
                "type":"postgresql",
                "host":"10.8.10.xxx",
                "port":"5432",
                "schema":"xxx",
                "space":"xxx",
                "driver-class-name":"org.postgresql.Driver",
                "version":"","jdbcUrl":"jdbc:postgresql://10.8.10.xxx:5432/xxx"
            }
            """;
}
