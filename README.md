# agent_demo
Learning Project--learn-Claude code

## Context Compact 配置

新增以下环境变量（可写在 `.env`）：

- `COMPACT_TOKEN_THRESHOLD`：Layer2 触发压缩的 token 阈值，默认 `50000`
- `COMPACT_KEEP_RECENT`：Layer1 保留最近 `tool_result` 条数，默认 `3`
- `TRANSCRIPT_DIR`：压缩前历史对话落盘目录，默认 `.transcripts`

## 三层压缩使用说明

- Layer1（微压缩）：当 `tool_result` 过多时，仅保留最近 N 条（N= `COMPACT_KEEP_RECENT`），更早结果替换为占位信息。
- Layer2（自动压缩）：当上下文 token 估算超过阈值（`COMPACT_TOKEN_THRESHOLD`）时，触发摘要压缩，并把原始上下文写入 `TRANSCRIPT_DIR`。
- Layer3（显式压缩）：LLM 调用 `compact` 工具时，执行与 Layer2 一致的压缩与落盘逻辑。

## .transcripts 目录作用

`.transcripts`（或 `TRANSCRIPT_DIR` 指定目录）用于保存压缩前上下文快照，便于：

- 回溯压缩前细节
- 调试压缩策略效果
- 在必要时进行人工恢复与审计
