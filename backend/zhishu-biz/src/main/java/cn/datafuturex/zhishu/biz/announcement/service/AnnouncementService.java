package cn.datafuturex.zhishu.biz.announcement.service;
import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.biz.announcement.dto.AnnouncementCreateDTO;
import cn.datafuturex.zhishu.biz.announcement.dto.AnnouncementQueryDTO;
import cn.datafuturex.zhishu.biz.announcement.dto.AnnouncementUpdateDTO;
import cn.datafuturex.zhishu.biz.announcement.entity.AnnouncementEntity;
import cn.datafuturex.zhishu.biz.announcement.vo.AnnouncementVO;
import java.util.List;
import java.util.Optional;
/**
 * 系统公告服务
 */
public interface AnnouncementService {
    /**
     * 管理员分页查询（含草稿/已发布/已撤回）
     */
    PageResult<AnnouncementVO> pageQueryForAdmin(AnnouncementQueryDTO query);
    /**
     * 用户分页查询已发布公告
     */
    PageResult<AnnouncementVO> pageQueryPublished(AnnouncementQueryDTO query);
    /**
     * 铃铛下拉：最近已发布公告（默认 10 条）
     */
    List<AnnouncementVO> listRecentPublished(int limit);
    /**
     * 当前用户未读公告数量
     */
    long countUnread();
    Optional<AnnouncementVO> findById(Long id);
    AnnouncementEntity create(AnnouncementCreateDTO dto);
    AnnouncementEntity update(AnnouncementUpdateDTO dto);
    void delete(Long id);
    AnnouncementEntity publish(Long id);
    AnnouncementEntity revoke(Long id);
    void markAsRead(Long id);
    int markAllAsRead();
}
