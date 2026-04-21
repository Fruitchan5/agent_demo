package cn.edu.agent.pojo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubAgentResult {
    /** 子 Agent 返回给父 Agent 的摘要文本 */
    private String summary;
    /** 实际消耗的迭代轮次 */
    private int iterationsUsed;
    /** 是否因达到上限而截断，正常结束为 false */
    private boolean truncated;
    /** 是否执行中遇到异常 */
    private boolean hasError;
    /** 错误信息，hasError=true 时有值 */
    private String errorMessage;

    public static SubAgentResult success(String summary, int iterations) {
        return SubAgentResult.builder()
                .summary(summary)
                .iterationsUsed(iterations)
                .truncated(false)
                .hasError(false)
                .errorMessage(null)
                .build();
    }

    public static SubAgentResult truncated(String summary, int iterations) {
        return SubAgentResult.builder()
                .summary(summary)
                .iterationsUsed(iterations)
                .truncated(true)
                .hasError(false)
                .errorMessage(null)
                .build();
    }

    public static SubAgentResult error(String errorMessage, int iterations) {
        return SubAgentResult.builder()
                .summary(null)
                .iterationsUsed(iterations)
                .truncated(false)
                .hasError(true)
                .errorMessage(errorMessage)
                .build();
    }
}

