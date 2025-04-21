package cn.daydayup.dev.mcpserver.web;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

/**
 * @ClassName WebPageReader
 * @Description 读取网页内容
 * @Author ZhaoYanNing
 * @Date 2025/4/21 10:15
 * @Version 1.0
 */
@Slf4j
@Service
public class WebPageReader {
    public String read(String url) {
        try {
            // 使用Jsoup获取网页内容
            Document doc = Jsoup.connect(url).get();
            // 输出网页标题
            log.info("网页标题: {}" , doc.title());
            // 输出网页正文（假设正文在某个标签内）
            return doc.body().text();
        } catch (Exception e) {
            log.error("读取网页内容时发生错误: {}" , e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
}
