package cn.edu.agent.background;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * 守护线程执行器（默认实现）
 */
public class DaemonThreadExecutor implements TaskExecutor {
    private final Set<Thread> activeThreads = ConcurrentHashMap.newKeySet();
    private final int maxTasks;

    public DaemonThreadExecutor(int maxTasks) {
        this.maxTasks = maxTasks;
    }

    @Override
    public void execute(Runnable task, String taskId) {
        if (activeThreads.size() >= maxTasks) {
            throw new RejectedExecutionException(
                "Too many background tasks: " + activeThreads.size() + "/" + maxTasks);
        }

        Thread t = new Thread(() -> {
            try {
                task.run();
            } finally {
                activeThreads.remove(Thread.currentThread());
            }
        }, "bg-" + taskId);

        t.setDaemon(true);
        activeThreads.add(t);
        t.start();
    }

    @Override
    public void shutdown() {
        for (Thread t : activeThreads) {
            t.interrupt();
        }
    }
}
