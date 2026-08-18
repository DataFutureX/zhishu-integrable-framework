package cn.datafuturex.yunqi.biz.systemconfig.service.impl;

import cn.datafuturex.yunqi.biz.systemconfig.dto.SystemConfigUpdateDTO;
import cn.datafuturex.yunqi.biz.systemconfig.entity.SystemConfigEntity;
import cn.datafuturex.yunqi.biz.systemconfig.mapper.SystemConfigMapper;
import cn.datafuturex.yunqi.biz.systemconfig.service.SystemConfigService;
import cn.datafuturex.yunqi.biz.systemconfig.vo.SystemConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 系统配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final Long CONFIG_ID = 1L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "svg", "ico", "webp");
    private static final long MAX_ICON_SIZE = 2 * 1024 * 1024L;

    private final SystemConfigMapper systemConfigMapper;

    @Value("${yunqi.upload.path:uploads}")
    private String uploadPath;

    @Override
    public SystemConfigVO getConfig() {
        return toVO(getOrInitConfig());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemConfigVO update(SystemConfigUpdateDTO dto) {
        SystemConfigEntity entity = getOrInitConfig();
        entity.setSystemName(dto.systemName());
        entity.setEnglishTitle(dto.englishTitle());
        entity.setSystemIcon(dto.systemIcon());
        entity.setCopyright(dto.copyright());
        entity.setSystemIntroduction(dto.systemIntroduction());
        entity.setProjectSite(dto.projectSite());
        entity.setLoginRetryLimitEnabled(dto.loginRetryLimitEnabled());
        entity.setLoginMaxRetryAttempts(dto.loginMaxRetryAttempts());
        entity.setLoginLockMinutes(dto.loginLockMinutes());
        entity.setUpdateTime(LocalDateTime.now());
        systemConfigMapper.updateById(entity);
        log.info("更新系统配置成功");
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadIcon(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择要上传的图标文件");
        }
        if (file.getSize() > MAX_ICON_SIZE) {
            throw new RuntimeException("图标文件大小不能超过 2MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("不支持的图标格式，仅支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        try {
            Path iconDir = Paths.get(uploadPath, "system");
            Files.createDirectories(iconDir);

            String filename = "icon-" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;
            Path targetPath = iconDir.resolve(filename);
            file.transferTo(targetPath.toFile());

            String iconUrl = "/uploads/system/" + filename;
            SystemConfigEntity entity = getOrInitConfig();
            entity.setSystemIcon(iconUrl);
            entity.setUpdateTime(LocalDateTime.now());
            systemConfigMapper.updateById(entity);

            log.info("上传系统图标成功: {}", iconUrl);
            return iconUrl;
        } catch (IOException e) {
            log.error("上传系统图标失败", e);
            throw new RuntimeException("上传系统图标失败: " + e.getMessage());
        }
    }

    private SystemConfigEntity getOrInitConfig() {
        SystemConfigEntity entity = systemConfigMapper.selectById(CONFIG_ID);
        if (entity != null) {
            return entity;
        }

        entity = new SystemConfigEntity();
        entity.setId(CONFIG_ID);
        entity.setSystemName("云起应用平台");
        entity.setEnglishTitle("YunQi Application Platform");
        entity.setCopyright("© 2026 云起应用平台 · MIT 开源");
        entity.setSystemIntroduction("一套面向企业数字化应用建设的模块化开发基础平台，通过统一技术架构、业务组件、AI能力和行业扩展能力，帮助企业快速构建智能化应用系统。");
        entity.setLoginRetryLimitEnabled(false);
        entity.setLoginMaxRetryAttempts(5);
        entity.setLoginLockMinutes(3);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        systemConfigMapper.insert(entity);
        return entity;
    }

    private SystemConfigVO toVO(SystemConfigEntity entity) {
        return new SystemConfigVO(
                entity.getId(),
                entity.getSystemName(),
                entity.getEnglishTitle(),
                entity.getSystemIcon(),
                entity.getCopyright(),
                entity.getSystemIntroduction(),
                entity.getProjectSite(),
                entity.getLoginRetryLimitEnabled() != null ? entity.getLoginRetryLimitEnabled() : false,
                entity.getLoginMaxRetryAttempts() != null ? entity.getLoginMaxRetryAttempts() : 5,
                entity.getLoginLockMinutes() != null ? entity.getLoginLockMinutes() : 3,
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
