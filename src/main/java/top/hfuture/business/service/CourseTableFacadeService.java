package top.hfuture.business.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.hfuture.business.model.CourseTableResponse;
import top.hfuture.business.model.SessionInfo;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseTableFacadeService {

    private final CasAuthService casAuthService;
    private final SsoAuthService ssoAuthService;
    private final CourseTableService courseTableService;
    private final SessionManagerService sessionManagerService;

    public List<CourseTableResponse> getCourseTable(String username, String password, String captcha) throws IOException {
        log.info("开始获取课表，用户名: {}", username);

        SessionInfo sessionInfo = sessionManagerService.getSession(username);
        
        if (sessionInfo == null || !isValidSession(sessionInfo)) {
            log.info("会话不存在或已失效，开始重新登录...");
            sessionInfo = login(username, password, captcha);
            if (sessionInfo == null) {
                throw new RuntimeException("登录失败");
            }
        }

        return fetchCourseTable(sessionInfo);
    }

    public SessionInfo login(String username, String password, String captcha) throws IOException {
        log.info("开始登录流程...");

        SessionInfo sessionInfo = casAuthService.initCasSession();
        
        casAuthService.getVercodeAndFlavoring(sessionInfo);
        
        boolean loginSuccess = casAuthService.casLogin(sessionInfo, username, password, captcha);
        if (!loginSuccess) {
            log.error("CAS 登录失败");
            return null;
        }

        String ticket = ssoAuthService.getTicket(sessionInfo);
        if (ticket == null) {
            log.error("获取 Ticket 失败");
            return null;
        }

        boolean activated = ssoAuthService.activateSession(sessionInfo, ticket);
        if (!activated) {
            log.error("激活教务 SESSION 失败");
            return null;
        }

        Long dataId = courseTableService.getDataId(sessionInfo);
        if (dataId == null) {
            log.error("获取 dataId 失败");
            return null;
        }

        boolean envSuccess = courseTableService.getEnvironmentVariables(sessionInfo);
        if (!envSuccess) {
            log.error("获取环境变量失败");
            return null;
        }

        sessionManagerService.saveSession(sessionInfo);
        
        log.info("登录流程完成");
        return sessionInfo;
    }

    public SessionInfo login(String username, String password, String captcha, SessionInfo sessionInfo) throws IOException {
        log.info("开始登录流程（带会话）...");

        casAuthService.getVercodeAndFlavoring(sessionInfo);
        
        boolean loginSuccess = casAuthService.casLogin(sessionInfo, username, password, captcha);
        if (!loginSuccess) {
            log.error("CAS 登录失败");
            return null;
        }

        String ticket = ssoAuthService.getTicket(sessionInfo);
        if (ticket == null) {
            log.error("获取 Ticket 失败");
            return null;
        }

        boolean activated = ssoAuthService.activateSession(sessionInfo, ticket);
        if (!activated) {
            log.error("激活教务 SESSION 失败");
            return null;
        }

        Long dataId = courseTableService.getDataId(sessionInfo);
        if (dataId == null) {
            log.error("获取 dataId 失败");
            return null;
        }

        boolean envSuccess = courseTableService.getEnvironmentVariables(sessionInfo);
        if (!envSuccess) {
            log.error("获取环境变量失败");
            return null;
        }

        sessionManagerService.saveSession(sessionInfo);
        
        log.info("登录流程完成");
        return sessionInfo;
    }

    private List<CourseTableResponse> fetchCourseTable(SessionInfo sessionInfo) throws IOException {
        List<CourseTableResponse> courses = courseTableService.getCourseTable(sessionInfo);
        if (courses == null) {
            throw new RuntimeException("获取课表失败");
        }

        return courses;
    }

    private boolean isValidSession(SessionInfo sessionInfo) {
        return sessionInfo.getSession() != null && 
               sessionInfo.getDataId() != null &&
               sessionInfo.getBizTypeId() != null &&
               sessionInfo.getSemesterId() != null;
    }
    
    public List<CourseTableResponse> getCourseTableFromSession(SessionInfo sessionInfo) throws IOException {
        log.info("从已有会话获取课表...");
        
        if (!isValidSession(sessionInfo)) {
            throw new RuntimeException("会话无效，请重新登录");
        }
        
        return fetchCourseTable(sessionInfo);
    }
}
