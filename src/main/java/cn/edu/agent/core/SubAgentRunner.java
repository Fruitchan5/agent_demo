package cn.edu.agent.core;

/**
 * 用全新上下文执行子任务，返回摘要文本。
 */
public interface SubAgentRunner {
    String run(String prompt);
}

