package top.hfuture.business.controller;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.hfuture.business.dto.ScheduleResponse;
import top.hfuture.business.model.CourseTableResponse;
import top.hfuture.business.model.SessionInfo;
import top.hfuture.business.service.CourseTableFacadeService;
import top.hfuture.common.dto.Result;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
public class ScheduleController {
    
    private final CourseTableFacadeService courseTableFacadeService;
    private final StringRedisTemplate redisTemplate;
    
    @GetMapping("/current")
    public Result<List<ScheduleResponse>> getCurrentSchedule(HttpServletRequest request) {
        try {
            String studentId = (String) request.getAttribute("studentId");
            log.info("获取当前课表，studentId: {}", studentId);
            
            String sessionKey = "hfut:session:" + studentId;
            String json = redisTemplate.opsForValue().get(sessionKey);
            
            if (json == null) {
                return Result.error(401, "会话已过期，请重新登录");
            }
            
            SessionInfo sessionInfo = JSON.parseObject(json, SessionInfo.class);
            
            List<CourseTableResponse> courses = courseTableFacadeService.getCourseTableFromSession(sessionInfo);
            
            List<ScheduleResponse> response = convertToScheduleResponse(courses);
            
            log.info("获取课表成功，共 {} 门课程", response.size());
            return Result.success("获取课表成功", response);
        } catch (Exception e) {
            log.error("获取课表失败", e);
            return Result.error(500, "获取课表失败: " + e.getMessage());
        }
    }
    
    private List<ScheduleResponse> convertToScheduleResponse(List<CourseTableResponse> courses) {
        List<ScheduleResponse> response = new ArrayList<>();
        
        if (courses == null || courses.isEmpty()) {
            return response;
        }
        
        for (CourseTableResponse course : courses) {
            log.debug("处理课程: {}, scheduleDetails: {}", course.getCourseName(), course.getScheduleDetails());
            if (course.getScheduleDetails() != null && !course.getScheduleDetails().isEmpty()) {
                for (CourseTableResponse.ScheduleDetail detail : course.getScheduleDetails()) {
                    ScheduleResponse schedule = ScheduleResponse.builder()
                            .courseName(course.getCourseName())
                            .teacher(course.getTeacherName())
                            .classroom(detail.getRoomName() != null ? detail.getRoomName() : "")
                            .dayOfWeek(detail.getWeekDay() != null ? detail.getWeekDay() : 0)
                            .section(formatSection(detail.getStartUnit(), detail.getEndUnit()))
                            .weekInfo(detail.getWeekInfo() != null ? detail.getWeekInfo() : "")
                            .campusName(detail.getCampusName() != null ? detail.getCampusName() : "")
                            .build();
                    response.add(schedule);
                }
            } else {
                log.warn("课程 {} 没有 scheduleDetails", course.getCourseName());
            }
        }
        
        return response;
    }
    
    private String formatSection(Integer startUnit, Integer endUnit) {
        if (startUnit == null || endUnit == null) {
            return "";
        }
        if (startUnit.equals(endUnit)) {
            return String.valueOf(startUnit);
        }
        return startUnit + "-" + endUnit;
    }
}
