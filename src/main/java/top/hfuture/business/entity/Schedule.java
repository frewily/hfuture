package top.hfuture.business.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.hfuture.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_schedule")
public class Schedule extends BaseEntity {

    private Long studentId;
    
    private Long courseId;
    
    private Integer weekDay;
    
    private Integer startUnit;
    
    private Integer endUnit;
    
    private String roomName;
    
    private String campusName;
    
    private Integer semesterId;
    
    private String weekInfo;
}
