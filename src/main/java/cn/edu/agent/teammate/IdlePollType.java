package cn.edu.agent.teammate;

/**
 * 空闲轮询结果类型
 */
public enum IdlePollType {
    MESSAGES,       // 收到消息
    TASK_CLAIMED,   // 认领到任务
    TIMEOUT         // 超时
}
