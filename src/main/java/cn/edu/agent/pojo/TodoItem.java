package cn.edu.agent.pojo;

import lombok.Data;
import java.time.Instant;

/**
 * 待办事项数据对象
 * 用于 TodoTool 管理待办列表
 */
@Data // 👈 这一行已经包含了 Getter, Setter, 标准 toString, equals, hashCode
public class TodoItem {

    /** 待办 ID */
    private String id;

    /** 待办标题 */
    private String title;

    /** 待办状态 */
    private TodoStatus status;

    /** 创建时间 */
    private Instant createdAt;

    /**
     * 待办状态枚举
     */
    public enum TodoStatus {
        PENDING, IN_PROGRESS, COMPLETED
    }

    /**
     * 构造函数 - 创建新的待办事项
     * (保留这个是因为你需要注入默认状态和当前时间)
     */
    public TodoItem(String id, String title) {
        this.id = id;
        this.title = title;
        this.status = TodoStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * 自定义详细输出
     * (保留这个是因为它不是标准的 toString 格式)
     */
    public String toDetailedString() {
        return String.format("ID: %s\n标题: %s\n状态: %s\n创建时间: %s",
                id, title, status, createdAt);
    }


}