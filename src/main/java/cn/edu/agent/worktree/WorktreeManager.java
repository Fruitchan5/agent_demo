package cn.edu.agent.worktree;

import java.nio.file.Path;
import java.util.List;

/**
 * Worktree管理器接口
 * 提供git worktree的创建、执行、删除等核心功能
 */
public interface WorktreeManager {

    /**
     * 创建worktree并可选绑定到任务
     * @param name worktree名称（1-40字符，字母数字._-）
     * @param taskId 可选的任务ID，传入则自动绑定
     * @param baseRef 基于的git引用（默认HEAD）
     * @return worktree信息
     */
    WorktreeInfo create(String name, Integer taskId, String baseRef);

    /**
     * 获取worktree信息
     * @param name worktree名称
     * @return worktree信息，不存在返回null
     */
    WorktreeInfo get(String name);

    /**
     * 列出所有worktree
     * @return worktree列表
     */
    List<WorktreeInfo> listAll();

    /**
     * 删除worktree
     * @param name worktree名称
     * @param force 是否强制删除（忽略未提交更改）
     * @param completeTask 是否同时完成绑定的任务
     */
    void remove(String name, boolean force, boolean completeTask);

    /**
     * 保留worktree（标记为kept状态，不自动清理）
     * @param name worktree名称
     */
    void keep(String name);

    /**
     * 在worktree中执行命令
     * @param name worktree名称
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    CommandResult execute(String name, String command);

    /**
     * 获取worktree的git状态
     * @param name worktree名称
     * @return git status输出
     */
    String status(String name);

    /**
     * 绑定任务到worktree
     * @param name worktree名称
     * @param taskId 任务ID
     */
    void bindTask(String name, int taskId);

    /**
     * 解绑worktree的任务
     * @param name worktree名称
     */
    void unbindTask(String name);

    /**
     * 获取最近的事件
     * @param limit 返回数量限制
     * @return 事件JSON字符串
     */
    String getRecentEvents(int limit);
}
