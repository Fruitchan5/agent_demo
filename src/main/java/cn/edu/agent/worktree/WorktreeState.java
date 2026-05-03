package cn.edu.agent.worktree;

public enum WorktreeState {
    ABSENT,   // 尚未创建
    ACTIVE,   // 活跃使用中
    REMOVED,  // 已删除
    KEPT      // 保留（不自动清理）
}
