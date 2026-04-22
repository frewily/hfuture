package top.hfuture.business.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {
    
    private String courseName;
    
    private String teacher;
    
    private String classroom;
    
    private Integer dayOfWeek;
    
    private String section;
}
