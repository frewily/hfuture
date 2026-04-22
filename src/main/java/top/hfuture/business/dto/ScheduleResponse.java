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

    /** 星期几（1=周一，7=周日） */
    private Integer dayOfWeek;

    /** 节次范围，如 "1-2" */
    private String section;

    /** 上课周次信息，如 "1~18" */
    private String weekInfo;

    /** 校区名称 */
    private String campusName;
}
