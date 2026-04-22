-- 创建数据库
CREATE DATABASE IF NOT EXISTS hfuture_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE hfuture_db;

-- 学生表
CREATE TABLE IF NOT EXISTS t_student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    student_no VARCHAR(50) NOT NULL COMMENT '学号',
    name VARCHAR(100) COMMENT '姓名',
    data_id BIGINT COMMENT '教务系统内部ID',
    biz_type_id INT COMMENT '业务类型ID',
    session_key VARCHAR(255) COMMENT '会话标识',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    UNIQUE KEY uk_student_no (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生信息表';

-- 课程表
CREATE TABLE IF NOT EXISTS t_course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    lesson_id BIGINT NOT NULL COMMENT '课程ID',
    course_code VARCHAR(100) COMMENT '课程代码',
    course_name VARCHAR(200) NOT NULL COMMENT '课程名称',
    course_type_name VARCHAR(100) COMMENT '课程类型',
    teacher_name VARCHAR(100) COMMENT '教师姓名',
    actual_periods INT COMMENT '实际课时',
    suggest_schedule_week_info VARCHAR(100) COMMENT '建议上课周次',
    semester_id INT COMMENT '学期ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    UNIQUE KEY uk_lesson_semester (lesson_id, semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程信息表';

-- 课表安排表
CREATE TABLE IF NOT EXISTS t_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    student_id BIGINT NOT NULL COMMENT '学生ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',
    week_day TINYINT COMMENT '星期几(1-7)',
    start_unit TINYINT COMMENT '开始节次',
    end_unit TINYINT COMMENT '结束节次',
    room_name VARCHAR(200) COMMENT '教室名称',
    campus_name VARCHAR(100) COMMENT '校区名称',
    semester_id INT COMMENT '学期ID',
    week_info VARCHAR(100) COMMENT '上课周次信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    KEY idx_student_semester (student_id, semester_id),
    KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课表安排表';
