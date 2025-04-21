package cn.daydayup.dev.mcpserver.web;

import cn.daydayup.dev.mcpserver.config.Constants;
import com.google.gson.JsonElement;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import serpapi.GoogleSearch;
import serpapi.SerpApiSearch;
import serpapi.SerpApiSearchException;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName WebSearch
 * @Description 网络搜索
 * @Author ZhaoYanNing
 * @Date 2025/4/21 9:52
 * @Version 1.0
 */
@Service
public class WebSearch {

    @Resource
    private WebPageReader webPageReader;

    public String search(String question) throws SerpApiSearchException {
        StringBuilder webResults = new StringBuilder();
        Map<String, String> parameter = new HashMap<>();
        parameter.put("api_key", Constants.WEB_SEARCH_KEY);
        parameter.put("q", question);
        parameter.put("hl", "zh-cn");
        parameter.put("gl", "cn");
        parameter.put("google_domain", "google.com");
        parameter.put("safe", "active");
        parameter.put("num", "3");
        parameter.put("device", "desktop");
        SerpApiSearch googleSearch = new GoogleSearch(parameter);
        JsonObject json = googleSearch.getJson();
        JsonElement organicResults = json.get("organic_results");
        for (JsonElement jsonElement : organicResults.getAsJsonArray()) {
            String link = jsonElement.getAsJsonObject().get("link").getAsString();
            String webResult = webPageReader.read(link);
            webResults.append(webResult).append("\n");
        }
        return webResults.toString();
    }
}
