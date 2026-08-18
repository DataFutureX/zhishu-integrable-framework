package cn.datafuturex.yunqi.biz.announcement.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import cn.datafuturex.yunqi.biz.announcement.vo.AnnouncementVO;

/**
 * 公告 SSE 实时推送服务
 */
public interface AnnouncementSseService {

    /**
     * 订阅公告 SSE 流
     *
     * @return SSE 连接
     */
    SseEmitter subscribe();

    /**
     * 推送新公告事件
     *
     * @param announcement 公告信息
     */
    void pushAnnouncement(AnnouncementVO announcement);
}
