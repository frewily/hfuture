package top.hfuture.business.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.hfuture.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_course")
public class Course extends BaseEntity {

    private Long lessonId;
    
    private String courseCode;
    
    private String courseName;
    
    private String courseTypeName;
    
    private String teacherName;
    
    private Integer actualPeriods;
    
    private String suggestScheduleWeekInfo;
    
    private Integer semesterId;
}
