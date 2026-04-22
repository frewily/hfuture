package top.hfuture.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.hfuture.business.entity.Student;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
