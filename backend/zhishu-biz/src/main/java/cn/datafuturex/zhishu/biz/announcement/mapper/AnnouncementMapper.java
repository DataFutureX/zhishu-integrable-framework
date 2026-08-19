package cn.datafuturex.zhishu.biz.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.zhishu.biz.announcement.entity.AnnouncementEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统公告 Mapper
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<AnnouncementEntity> {
}
