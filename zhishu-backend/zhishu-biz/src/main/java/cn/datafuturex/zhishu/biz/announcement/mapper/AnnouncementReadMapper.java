package cn.datafuturex.zhishu.biz.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.zhishu.biz.announcement.entity.AnnouncementReadEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公告已读记录 Mapper
 */
@Mapper
public interface AnnouncementReadMapper extends BaseMapper<AnnouncementReadEntity> {
}
