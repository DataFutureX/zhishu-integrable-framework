package cn.datafuturex.zhishu.biz.announcement.service.impl;

import cn.datafuturex.zhishu.biz.announcement.service.AnnouncementSseService;
import cn.datafuturex.zhishu.biz.announcement.vo.AnnouncementVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 公告 SSE 广播实现
 */
@Slf4j
@Service
public class AnnouncementSseServiceImpl implements AnnouncementSseService {

    private static final long SSE_TIMEOUT_MS = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(ex -> removeEmitter(emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            removeEmitter(emitter);
            log.warn("公告 SSE 连接初始化失败", e);
        }

        log.debug("公告 SSE 订阅连接建立，当前连接数: {}", emitters.size());
        return emitter;
    }

    @Override
    public void pushAnnouncement(AnnouncementVO announcement) {
        if (emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("announcement").data(announcement));
            } catch (Exception e) {
                removeEmitter(emitter);
                log.debug("公告 SSE 推送失败，移除失效连接", e);
            }
        }
        log.info("公告事件已推送: id={}, title={}", announcement.id(), announcement.title());
    }

    private void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
        log.debug("公告 SSE 连接已移除，当前连接数: {}", emitters.size());
    }
}
