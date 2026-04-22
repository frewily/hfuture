package top.hfuture.business.service;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.hfuture.business.model.SessionInfo;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManagerService {

    private final StringRedisTemplate redisTemplate;
    
    private static final String SESSION_KEY_PREFIX = "hfut:session:";
    private static final long SESSION_TIMEOUT_HOURS = 3;

    public void saveSession(SessionInfo sessionInfo) {
        String key = SESSION_KEY_PREFIX + sessionInfo.getStudentNo();
        String value = JSON.toJSONString(sessionInfo);
        redisTemplate.opsForValue().set(key, value, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        log.info("会话已保存到 Redis，学号: {}", sessionInfo.getStudentNo());
    }

    public SessionInfo getSession(String studentNo) {
        String key = SESSION_KEY_PREFIX + studentNo;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.warn("未找到学号 {} 的会话", studentNo);
            return null;
        }
        return JSON.parseObject(value, SessionInfo.class);
    }

    public void deleteSession(String studentNo) {
        String key = SESSION_KEY_PREFIX + studentNo;
        redisTemplate.delete(key);
        log.info("会话已删除，学号: {}", studentNo);
    }

    public boolean hasSession(String studentNo) {
        String key = SESSION_KEY_PREFIX + studentNo;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void refreshSession(String studentNo) {
        String key = SESSION_KEY_PREFIX + studentNo;
        redisTemplate.expire(key, SESSION_TIMEOUT_HOURS, TimeUnit.HOURS);
        log.info("会话已刷新，学号: {}", studentNo);
    }
}
