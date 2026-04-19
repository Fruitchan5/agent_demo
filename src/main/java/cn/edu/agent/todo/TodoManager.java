package cn.edu.agent.todo;

import cn.edu.agent.pojo.TodoItem;
import cn.edu.agent.pojo.TodoItem.TodoStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 待办管理器：带状态的多步任务进度，并强制同一时间至多一个 {@link TodoStatus#IN_PROGRESS}。
 */
public class TodoManager {

    private final Map<String, TodoItem> todos = new ConcurrentHashMap<>();
    private int idCounter = 1;

    public synchronized String add(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "❌ 错误: 添加待办时必须指定标题（title）";
        }
        String id = String.valueOf(idCounter++);
        TodoItem todo = new TodoItem(id, title.trim());
        todos.put(id, todo);
        assertAtMostOneInProgress();
        return formatItemResult("✅ 已添加待办事项", todo);
    }

    /**
     * 更新待办状态或标题；若会导致多个 IN_PROGRESS，则抛错。
     */
    public synchronized String update(String id, String statusStr, String newTitle) throws IllegalStateException {
        if (id == null || id.trim().isEmpty()) {
            return "❌ 错误: 更新待办时必须指定 ID（id）";
        }
        TodoItem todo = todos.get(id);
        if (todo == null) {
            return "❌ 错误: 找不到 ID 为 " + id + " 的待办事项";
        }
        if (newTitle != null && !newTitle.trim().isEmpty()) {
            todo.setTitle(newTitle.trim());
        }
        if (statusStr != null && !statusStr.trim().isEmpty()) {
            TodoStatus newStatus = parseStatus(statusStr);
            enforceSingleInProgressBeforeSet(todo, newStatus);
            todo.setStatus(newStatus);
        }
        assertAtMostOneInProgress();
        return formatItemResult("✅ 已更新待办事项", todo);
    }

    public synchronized String listRendered() {
        if (todos.isEmpty()) {
            return "📋 待办列表为空";
        }
        StringBuilder result = new StringBuilder();
        result.append("📋 待办列表（共 ").append(todos.size()).append(" 项）\n");
        result.append("─────────────────\n");
        List<TodoItem> sorted = todos.values().stream()
                .sorted(Comparator.comparing(TodoItem::getCreatedAt))
                .collect(Collectors.toList());
        for (TodoItem todo : sorted) {
            String icon = switch (todo.getStatus()) {
                case PENDING -> "⏳";
                case IN_PROGRESS -> "🔄";
                case COMPLETED -> "✅";
            };
            result.append(String.format("%s [%s] %s (%s)\n",
                    icon,
                    todo.getId(),
                    todo.getTitle(),
                    statusLabel(todo.getStatus())));
        }
        result.append("─────────────────");
        return result.toString();
    }

    public synchronized String delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            return "❌ 错误: 删除待办时必须指定 ID（id）";
        }
        TodoItem removed = todos.remove(id);
        if (removed == null) {
            return "❌ 错误: 找不到 ID 为 " + id + " 的待办事项";
        }
        return String.format("✅ 已删除待办事项\nID: %s\n标题: %s",
                removed.getId(), removed.getTitle());
    }

    public Collection<TodoItem> getAllTodos() {
        return Collections.unmodifiableCollection(todos.values());
    }

    public void clearAll() {
        todos.clear();
        idCounter = 1;
    }

    public int getTodoCount() {
        return todos.size();
    }

    private void enforceSingleInProgressBeforeSet(TodoItem target, TodoStatus newStatus) {
        if (newStatus != TodoStatus.IN_PROGRESS) {
            return;
        }
        for (TodoItem other : todos.values()) {
            if (other.getStatus() == TodoStatus.IN_PROGRESS && !other.getId().equals(target.getId())) {
                throw new IllegalStateException(
                        "仅允许一个任务处于 in_progress：请先将进行中项 ["
                                + other.getId() + "] "
                                + other.getTitle()
                                + " 设为 done/pending 后再开始其他任务。");
            }
        }
    }

    private void assertAtMostOneInProgress() {
        long n = todos.values().stream().filter(t -> t.getStatus() == TodoStatus.IN_PROGRESS).count();
        if (n > 1) {
            throw new IllegalStateException("非法状态：存在多个 in_progress 任务。");
        }
    }

    private static TodoStatus parseStatus(String statusStr) {
        try {
            return TodoStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "无效的状态值: " + statusStr + "。有效值：PENDING, IN_PROGRESS, COMPLETED（语义同 pending / in_progress / done）");
        }
    }

    private static String statusLabel(TodoStatus s) {
        return switch (s) {
            case PENDING -> "pending";
            case IN_PROGRESS -> "in_progress";
            case COMPLETED -> "done";
        };
    }

    private static String formatItemResult(String header, TodoItem todo) {
        return String.format("%s\nID: %s\n标题: %s\n状态: %s (%s)\n创建时间: %s",
                header,
                todo.getId(),
                todo.getTitle(),
                todo.getStatus(),
                statusLabel(todo.getStatus()),
                todo.getCreatedAt());
    }
}
