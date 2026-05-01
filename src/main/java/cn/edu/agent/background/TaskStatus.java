package cn.edu.agent.background;

/**
 * 后台任务状态枚举
 */
public enum TaskStatus {
    PENDING,        // 刚创建，未启动
    RUNNING,        // 正在执行
    COMPLETED,      // 成功完成（exit code 0）
    FAILED,         // 执行失败（exit code != 0）
    TIMEOUT,        // 超时被终止
    CANCELLED,      // 用户取消
    START_FAILED;   // 启动失败（command not found, permission denied）

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == TIMEOUT
            || this == CANCELLED || this == START_FAILED;
    }
}
