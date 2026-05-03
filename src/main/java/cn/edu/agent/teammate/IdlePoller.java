package cn.edu.agent.teammate;

import cn.edu.agent.config.AppConfig;
import cn.edu.agent.task.ClaimResult;
import cn.edu.agent.task.Task;
import cn.edu.agent.task.TaskManager;

import java.util.List;

/**
 * 空闲轮询器
 * 在 teammate 进入 IDLE 状态后，定期轮询收件箱和任务板
 */
public class IdlePoller {
    private static final int POLL_INTERVAL_SECONDS = AppConfig.getIdlePollInterval();
    private static final int IDLE_TIMEOUT_SECONDS = AppConfig.getIdleTimeout();

    private final String teammateName;
    private final MessageBus messageBus;
    private final TaskManager taskManager;

    public IdlePoller(String teammateName, MessageBus messageBus, TaskManager taskManager) {
        this.teammateName = teammateName;
        this.messageBus = messageBus;
        this.taskManager = taskManager;
    }

    /**
     * 执行空闲轮询
     * 轮询顺序：先检查收件箱（响应式），再扫描任务板（主动式）
     *
     * @return 轮询结果（消息/任务/超时）
     */
    public IdlePollResult poll() {
        int maxPolls = IDLE_TIMEOUT_SECONDS / Math.max(POLL_INTERVAL_SECONDS, 1);

        for (int i = 0; i < maxPolls; i++) {
            // 1. 检查收件箱
            List<Message> inbox = messageBus.readInbox(teammateName);
            if (!inbox.isEmpty()) {
                return IdlePollResult.foundMessages(inbox);
            }

            // 2. 扫描任务板
            try {
                List<Task> unclaimed = taskManager.scanUnclaimedTasks();
                if (!unclaimed.isEmpty()) {
                    Task task = unclaimed.get(0);
                    ClaimResult result = taskManager.claimTask(task.getId(), teammateName);
                    if (result.isSuccess()) {
                        return IdlePollResult.claimedTask(result.getTask());
                    }
                    // 认领失败（被其他 agent 抢先），继续轮询
                }
            } catch (Exception e) {
                System.err.println("[IdlePoller] Error scanning tasks: " + e.getMessage());
            }

            // 3. 等待下一轮
            if (i < maxPolls - 1) {  // 最后一轮不需要 sleep
                try {
                    Thread.sleep(POLL_INTERVAL_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return IdlePollResult.timeout();
                }
            }
        }

        return IdlePollResult.timeout();
    }
}
