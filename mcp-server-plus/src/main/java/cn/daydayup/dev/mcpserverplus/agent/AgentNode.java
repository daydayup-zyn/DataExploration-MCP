package cn.daydayup.dev.mcpserverplus.agent;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;

/**
 * @ClassName AgentNode
 * @Description 智能体节点
 * @Author ZhaoYanNing
 * @Date 2025/5/16 9:48
 * @Version 1.0
 */
public interface AgentNode {

    StateGraph<AgentState> buildGraph() throws Exception;
}
