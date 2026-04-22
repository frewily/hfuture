package top.hfuture.business.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import top.hfuture.business.model.CourseTableResponse;
import top.hfuture.business.model.SessionInfo;
import top.hfuture.config.HfutProperties;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CourseTableService {

    private final OkHttpClient okHttpClient;
    private final OkHttpClient noRedirectClient;
    private final HfutProperties hfutProperties;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern DATA_ID_PATTERN = Pattern.compile("/info/(\\d+)");
    private static final Pattern BIZ_TYPE_ID_PATTERN = Pattern.compile("bizTypeId\\s*:\\s*(\\d+)");
    private static final Pattern SEMESTER_ID_PATTERN = Pattern.compile("semesterId\\s*[:=]\\s*['\"]?(\\d+)['\"]?");

    @Autowired
    public CourseTableService(
            OkHttpClient okHttpClient,
            @Qualifier("noRedirectClient") OkHttpClient noRedirectClient,
            HfutProperties hfutProperties) {
        this.okHttpClient = okHttpClient;
        this.noRedirectClient = noRedirectClient;
        this.hfutProperties = hfutProperties;
    }

    public Long getDataId(SessionInfo sessionInfo) throws IOException {
        log.info("开始获取学生 dataId...");

        Request request = new Request.Builder()
                .url(hfutProperties.getEams().getCourseTableUrl())
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response response = noRedirectClient.newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_MOVED_TEMP || 
                response.code() == HttpURLConnection.HTTP_MOVED_PERM) {
                
                String location = response.header("Location");
                log.debug("获取 dataId 重定向 Location: {}", location);
                if (StrUtil.isNotEmpty(location)) {
                    Matcher matcher = DATA_ID_PATTERN.matcher(location);
                    if (matcher.find()) {
                        Long dataId = Long.parseLong(matcher.group(1));
                        sessionInfo.setDataId(dataId);
                        log.info("dataId 获取成功: {}", dataId);
                        return dataId;
                    }
                }
            }

            log.error("获取 dataId 失败，状态码: {}", response.code());
            return null;
        }
    }

    public boolean getEnvironmentVariables(SessionInfo sessionInfo) throws IOException {
        log.info("开始获取环境变量...");

        String url = hfutProperties.getEams().getCourseTableUrl() + "/info/" + sessionInfo.getDataId();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("获取环境变量失败，状态码: {}", response.code());
                return false;
            }

            String html = response.body().string();
            log.debug("HTML 长度: {}", html.length());

            Matcher bizMatcher = BIZ_TYPE_ID_PATTERN.matcher(html);
            if (bizMatcher.find()) {
                Integer bizTypeId = Integer.parseInt(bizMatcher.group(1));
                sessionInfo.setBizTypeId(bizTypeId);
                log.info("bizTypeId: {}", bizTypeId);
            } else {
                log.warn("未找到 bizTypeId");
            }

            Matcher semesterMatcher = SEMESTER_ID_PATTERN.matcher(html);
            if (semesterMatcher.find()) {
                Integer semesterId = Integer.parseInt(semesterMatcher.group(1));
                sessionInfo.setSemesterId(semesterId);
                log.info("semesterId: {}", semesterId);
            } else {
                log.warn("未找到 semesterId，尝试其他匹配方式...");
                Pattern[] patterns = {
                    Pattern.compile("<option\\s+selected[^>]*value=\"(\\d+)\""),
                    Pattern.compile("\"semesterId\"\\s*:\\s*(\\d+)"),
                    Pattern.compile("semesterId\\s*=\\s*(\\d+)"),
                    Pattern.compile("semester\\.id\\s*=\\s*(\\d+)"),
                    Pattern.compile("currentSemesterId\\s*[=:]\\s*(\\d+)")
                };
                boolean found = false;
                for (Pattern pattern : patterns) {
                    Matcher m = pattern.matcher(html);
                    if (m.find()) {
                        Integer semesterId = Integer.parseInt(m.group(1));
                        sessionInfo.setSemesterId(semesterId);
                        log.info("semesterId (pattern): {}", semesterId);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    int idx = html.indexOf("semester");
                    if (idx > 0) {
                        log.debug("semester 附近内容: {}", html.substring(Math.max(0, idx - 50), Math.min(html.length(), idx + 200)));
                    }
                    idx = html.indexOf("学期");
                    if (idx > 0) {
                        log.debug("学期 附近内容: {}", html.substring(Math.max(0, idx - 100), Math.min(html.length(), idx + 100)));
                    }
                }
            }

            if (sessionInfo.getBizTypeId() == null || sessionInfo.getSemesterId() == null) {
                log.error("环境变量获取不完整: bizTypeId={}, semesterId={}", sessionInfo.getBizTypeId(), sessionInfo.getSemesterId());
                return false;
            }

            // 课表页不含学生姓名/专业/学院，从 /home 页单独获取
            fetchStudentInfoFromHome(sessionInfo);

            return true;
        }
    }

    /**
     * 从 EAMS REST API 获取学生姓名、专业、学院。
     * 依次尝试多个已知端点，成功即停止。失败时只打 warn，不影响登录流程。
     */
    private void fetchStudentInfoFromHome(SessionInfo sessionInfo) {
        String baseUrl = hfutProperties.getEams().getBaseUrl();

        // 按可能性排序的候选端点；每个 String[] 为 {url, 描述}
        String[][] endpoints = {
            {baseUrl + "/for-std/student-info",             "student-info"},  // 学籍信息页（已确认有效）
            {hfutProperties.getEams().getStudentInfoUrl(),  "home"},           // 首页兜底
        };

        for (String[] endpoint : endpoints) {
            if (endpoint[0] == null) continue;
            try {
                if (tryFetchStudentInfo(sessionInfo, endpoint[0], endpoint[1])) return;
            } catch (Exception e) {
                log.debug("端点 [{}] 异常: {}", endpoint[1], e.getMessage());
            }
        }
        log.warn("所有端点均未能获取到学生信息");
    }

    /**
     * 请求指定 URL，尝试解析学生信息。返回 true 表示至少获取到了学生姓名。
     */
    private boolean tryFetchStudentInfo(SessionInfo sessionInfo, String url, String desc) throws IOException {
        log.debug("尝试端点 [{}]: {}", desc, url);
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            log.debug("端点 [{}] 状态码: {}", desc, response.code());
            if (!response.isSuccessful()) return false;

            String body = response.body().string();
            log.debug("端点 [{}] 响应长度: {}", desc, body.length());

            // 如果是 JSON，直接解析
            String contentType = response.header("Content-Type", "");
            if (contentType.contains("json") || body.trim().startsWith("{") || body.trim().startsWith("[")) {
                if (parseStudentInfoFromJson(sessionInfo, body, desc)) return true;
            }

            // 否则当作 HTML/JS 解析
            if (parseStudentInfoFromHtml(sessionInfo, body, desc)) return true;
        }
        return false;
    }

    /** 从 JSON 响应中提取学生信息 */
    private boolean parseStudentInfoFromJson(SessionInfo sessionInfo, String body, String desc) {
        try {
            // 顶层可能是数组
            if (body.trim().startsWith("[")) {
                JSONArray arr = JSON.parseArray(body);
                if (!arr.isEmpty()) {
                    return parseStudentInfoFromJsonObject(sessionInfo, arr.getJSONObject(0), desc);
                }
            } else {
                JSONObject obj = JSON.parseObject(body);
                // 有时包在 data 字段里
                JSONObject data = obj.getJSONObject("data");
                if (data != null) obj = data;
                return parseStudentInfoFromJsonObject(sessionInfo, obj, desc);
            }
        } catch (Exception e) {
            log.debug("JSON 解析失败 [{}]: {}", desc, e.getMessage());
        }
        return false;
    }

    private boolean parseStudentInfoFromJsonObject(SessionInfo sessionInfo, JSONObject obj, String desc) {
        if (obj == null) return false;
        // 尝试常见的姓名字段
        String name = firstNonNull(obj.getString("nameZh"), obj.getString("studentName"),
                                   obj.getString("realName"), obj.getString("xm"), obj.getString("name"));
        if (name != null && !name.isBlank()) {
            sessionInfo.setStudentName(name.trim());
            log.info("学生姓名 [JSON/{}]: {}", desc, name.trim());
        }
        String major = firstNonNull(obj.getString("majorName"), obj.getString("major"),
                                    obj.getString("zymc"));
        if (major != null && !major.isBlank()) {
            sessionInfo.setMajor(major.trim());
            log.info("专业 [JSON/{}]: {}", desc, major.trim());
        }
        String college = firstNonNull(obj.getString("collegeName"), obj.getString("college"),
                                      obj.getString("department"), obj.getString("departmentName"));
        if (college != null && !college.isBlank()) {
            sessionInfo.setCollege(college.trim());
            log.info("学院 [JSON/{}]: {}", desc, college.trim());
        }
        return sessionInfo.getStudentName() != null;
    }

    /** 从 HTML/JS 响应中提取学生信息 */
    private boolean parseStudentInfoFromHtml(SessionInfo sessionInfo, String html, String desc) {
        // 1. 学籍信息页结构（student-info 页已确认）：
        //    <strong>中文姓名</strong></span>
        //    <span>付诺</span>
        Matcher nameMatcher = Pattern.compile(
                "<strong>中文姓名</strong></span>\\s*<span>([^<]+)</span>").matcher(html);
        if (nameMatcher.find()) {
            sessionInfo.setStudentName(nameMatcher.group(1).trim());
            log.info("学生姓名 [label/{}]: {}", desc, sessionInfo.getStudentName());
        }

        // 专业：<dt title="专业">专业</dt> <dd>软件工程</dd>
        Matcher majorMatcher = Pattern.compile(
                "<dt[^>]*>专业</dt>\\s*<dd>([^<]+)</dd>").matcher(html);
        if (majorMatcher.find()) {
            sessionInfo.setMajor(majorMatcher.group(1).trim());
            log.info("专业 [dt-dd/{}]: {}", desc, sessionInfo.getMajor());
        }

        // 学院：<dt title="专业院系">专业院系</dt> <dd>软件学院</dd>
        //       备选：管理部门
        for (String label : new String[]{"专业院系", "管理部门", "学院", "院系"}) {
            Matcher collegeMatcher = Pattern.compile(
                    "<dt[^>]*>" + label + "</dt>\\s*<dd>([^<]+)</dd>").matcher(html);
            if (collegeMatcher.find()) {
                String val = collegeMatcher.group(1).trim();
                if (!val.isEmpty()) {
                    sessionInfo.setCollege(val);
                    log.info("学院 [dt-dd/{}/{}]: {}", desc, label, val);
                    break;
                }
            }
        }

        if (sessionInfo.getStudentName() != null) return true;

        // 2. 首页 person-name hidden input：$(".person-name").val() 对应的元素
        Matcher personNameMatcher = Pattern.compile(
                "<input[^>]+class=[\"'][^\"']*person-name[^\"']*[\"'][^>]*value=[\"']([^\"']+)[\"']|" +
                "<input[^>]+value=[\"']([^\"']+)[\"'][^>]+class=[\"'][^\"']*person-name[^\"']*[\"']"
        ).matcher(html);
        if (personNameMatcher.find()) {
            String name = personNameMatcher.group(1) != null
                    ? personNameMatcher.group(1) : personNameMatcher.group(2);
            if (name != null && !name.isBlank()) {
                sessionInfo.setStudentName(name.trim());
                log.info("学生姓名 [person-name/{}]: {}", desc, name.trim());
                return true;
            }
        }

        // 3. JS 变量兜底
        String[][] globalPatterns = {
            {"\"studentName\"\\s*:\\s*\"([^\"]+)\"",                  "studentName"},
            {"\"realName\"\\s*:\\s*\"([^\"]+)\"",                     "realName"},
            {"\"nameZh\"\\s*:\\s*\"([\\u4e00-\\u9fff]{2,10})\"",     "nameZh"},
        };
        for (String[] p : globalPatterns) {
            Matcher m = Pattern.compile(p[0]).matcher(html);
            if (m.find()) {
                String candidate = m.group(1);
                if (!candidate.contains("系统") && !candidate.contains("管理")) {
                    sessionInfo.setStudentName(candidate.trim());
                    log.info("学生姓名 [JS/{}/{}]: {}", desc, p[1], candidate.trim());
                    return true;
                }
            }
        }

        return sessionInfo.getStudentName() != null;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    public List<CourseTableResponse> getCourseTable(SessionInfo sessionInfo) throws IOException {
        log.info("开始获取课程数据...");

        String url = hfutProperties.getEams().getGetDataUrl() + 
                "?bizTypeId=" + sessionInfo.getBizTypeId() +
                "&semesterId=" + sessionInfo.getSemesterId() +
                "&dataId=" + sessionInfo.getDataId();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", sessionInfo.getCookieHeader())
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("获取课程数据失败，状态码: {}", response.code());
                return null;
            }

            String body = response.body().string();
            JSONObject json = JSON.parseObject(body);
            
            JSONArray lessonIds = json.getJSONArray("lessonIds");
            Integer currentWeek = json.getInteger("currentWeek");
            JSONArray lessons = json.getJSONArray("lessons");
            
            log.info("获取到 {} 门课程，当前第 {} 周", lessonIds.size(), currentWeek);
            
            if (lessons == null || lessons.isEmpty()) {
                log.warn("lessons 数组为空，无法获取时间安排信息");
                return new ArrayList<>();
            }
            
            List<CourseTableResponse> courses = new ArrayList<>();
            
            for (int i = 0; i < lessons.size(); i++) {
                JSONObject lesson = lessons.getJSONObject(i);
                CourseTableResponse course = parseLesson(lesson);
                if (course != null) {
                    courses.add(course);
                }
            }

            log.info("成功解析 {} 门课程", courses.size());
            return courses;
        }
    }

    private CourseTableResponse parseLesson(JSONObject lesson) {
        CourseTableResponse course = new CourseTableResponse();
        
        course.setLessonId(lesson.getLong("id"));
        course.setCourseCode(lesson.getString("code"));
        
        String courseName = lesson.getString("nameZh");
        if (courseName == null || courseName.isEmpty()) {
            JSONObject courseExt = lesson.getJSONObject("courseExt");
            if (courseExt != null) {
                courseName = courseExt.getString("nameZh");
            }
        }
        course.setCourseName(courseName);
        
        JSONObject courseExt = lesson.getJSONObject("courseExt");
        if (courseExt != null) {
            JSONObject courseType = courseExt.getJSONObject("courseType");
            course.setCourseTypeName(courseType != null ? courseType.getString("nameZh") : null);
        }
        
        JSONObject requiredPeriodInfo = lesson.getJSONObject("requiredPeriodInfo");
        if (requiredPeriodInfo != null) {
            course.setActualPeriods(requiredPeriodInfo.getInteger("total"));
        }
        
        JSONArray teachers = lesson.getJSONArray("teacherAssignmentList");
        if (teachers != null && !teachers.isEmpty()) {
            JSONObject teacher = teachers.getJSONObject(0);
            JSONObject person = teacher.getJSONObject("person");
            if (person != null) {
                course.setTeacherName(person.getString("nameZh"));
            } else {
                course.setTeacherName(teacher.getString("nameZh"));
            }
        }

        JSONObject scheduleText = lesson.getJSONObject("scheduleText");
        if (scheduleText != null) {
            List<CourseTableResponse.ScheduleDetail> details = parseScheduleText(scheduleText);
            if (!details.isEmpty()) {
                course.setScheduleDetails(details);
            }
        }

        return course;
    }

    private List<CourseTableResponse.ScheduleDetail> parseScheduleText(JSONObject scheduleText) {
        List<CourseTableResponse.ScheduleDetail> details = new ArrayList<>();
        
        JSONObject dateTimePlaceText = scheduleText.getJSONObject("dateTimePlaceText");
        if (dateTimePlaceText == null) {
            return details;
        }
        
        String textZh = dateTimePlaceText.getString("textZh");
        if (textZh == null || textZh.isEmpty()) {
            return details;
        }
        
        String[] items = textZh.split(";\\s*");
        
        for (String item : items) {
            item = item.trim();
            if (item.isEmpty()) continue;
            
            CourseTableResponse.ScheduleDetail detail = parseScheduleItem(item);
            if (detail != null) {
                details.add(detail);
            }
        }
        
        return details;
    }

    private CourseTableResponse.ScheduleDetail parseScheduleItem(String item) {
        CourseTableResponse.ScheduleDetail detail = new CourseTableResponse.ScheduleDetail();
        
        Pattern weekPattern = Pattern.compile("(\\d+)~(\\d+)周");
        Matcher weekMatcher = weekPattern.matcher(item);
        if (weekMatcher.find()) {
            detail.setWeekInfo(weekMatcher.group(1) + "~" + weekMatcher.group(2));
        }
        
        Pattern dayPattern = Pattern.compile("周([一二三四五六日])");
        Matcher dayMatcher = dayPattern.matcher(item);
        if (dayMatcher.find()) {
            String dayStr = dayMatcher.group(1);
            int dayOfWeek = convertDayOfWeek(dayStr);
            detail.setWeekDay(dayOfWeek);
        }
        
        Pattern sectionPattern = Pattern.compile("第([一二三四五六七八九十]+)节~第([一二三四五六七八九十]+)节");
        Matcher sectionMatcher = sectionPattern.matcher(item);
        if (sectionMatcher.find()) {
            int startUnit = convertSectionToNumber(sectionMatcher.group(1));
            int endUnit = convertSectionToNumber(sectionMatcher.group(2));
            detail.setStartUnit(startUnit);
            detail.setEndUnit(endUnit);
        }
        
        Pattern roomPattern = Pattern.compile("(\\S+\\s+\\S+\\(\\d+\\))");
        Matcher roomMatcher = roomPattern.matcher(item);
        if (roomMatcher.find()) {
            detail.setRoomName(roomMatcher.group(1).trim());
        }
        
        Pattern campusPattern = Pattern.compile("(翡翠湖校区|屯溪路校区|宣城校区|六安路校区)");
        Matcher campusMatcher = campusPattern.matcher(item);
        if (campusMatcher.find()) {
            detail.setCampusName(campusMatcher.group(1));
        }
        
        return detail;
    }

    private int convertDayOfWeek(String dayStr) {
        switch (dayStr) {
            case "一": return 1;
            case "二": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "日": return 7;
            default: return 0;
        }
    }

    private int convertSectionToNumber(String section) {
        switch (section) {
            case "一": return 1;
            case "二": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            case "八": return 8;
            case "九": return 9;
            case "十": return 10;
            default: return 0;
        }
    }
}
