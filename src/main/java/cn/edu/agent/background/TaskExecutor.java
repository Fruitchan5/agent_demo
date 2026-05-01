package cn.edu.agent.background;

/**
 * 后台任务执行器接口（策略模式）
 */
public interface TaskExecutor {
    /**
     * 执行任务
     * @param task 任务逻辑
     * @param taskId 任务 ID（用于线程命名）
     */
    void execute(Runnable task, String taskId);

    /**
     * 关闭执行器
     */
    void shutdown();
}
