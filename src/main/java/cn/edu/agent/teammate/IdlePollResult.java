package cn.edu.agent.teammate;

import cn.edu.agent.task.Task;

import java.util.List;

/**
 * 空闲轮询结果
 */
public class IdlePollResult {
    private final IdlePollType type;
    private final List<Message> messages;
    private final Task claimedTask;

    private IdlePollResult(IdlePollType type, List<Message> messages, Task claimedTask) {
        this.type = type;
        this.messages = messages;
        this.claimedTask = claimedTask;
    }

    public IdlePollType getType() {
        return type;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public Task getClaimedTask() {
        return claimedTask;
    }

    public static IdlePollResult foundMessages(List<Message> messages) {
        return new IdlePollResult(IdlePollType.MESSAGES, messages, null);
    }

    public static IdlePollResult claimedTask(Task task) {
        return new IdlePollResult(IdlePollType.TASK_CLAIMED, null, task);
    }

    public static IdlePollResult timeout() {
        return new IdlePollResult(IdlePollType.TIMEOUT, null, null);
    }
}
