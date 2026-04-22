package top.hfuture.business.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseTableResponse implements Serializable {

    private Long lessonId;
    
    private String courseCode;
    
    private String courseName;
    
    private String courseTypeName;
    
    private String teacherName;
    
    private Integer actualPeriods;
    
    private String suggestScheduleWeekInfo;
    
    private List<ScheduleDetail> scheduleDetails;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleDetail implements Serializable {
        private Integer weekDay;
        private Integer startUnit;
        private Integer endUnit;
        private String roomName;
        private String campusName;
        private String weekInfo;
    }
}
