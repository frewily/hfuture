package top.hfuture.business.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.hfuture.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_student")
public class Student extends BaseEntity {

    private String studentNo;
    
    private String name;
    
    private Long dataId;
    
    private Integer bizTypeId;
    
    private String sessionKey;
}
