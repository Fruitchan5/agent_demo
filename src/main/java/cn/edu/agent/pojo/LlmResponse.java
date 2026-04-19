package cn.edu.agent.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmResponse {
    private String id;
    private List<ContentBlock> content;

    @JsonProperty("stop_reason")
    private String stopReason; // "end_turn" 或 "tool_use"
}
