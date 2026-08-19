package cn.datafuturex.zhishu.modules.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.modules.dto.UnitCreateDTO;
import cn.datafuturex.zhishu.modules.dto.UnitQueryDTO;
import cn.datafuturex.zhishu.modules.dto.UnitUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.UnitEntity;
import cn.datafuturex.zhishu.modules.mapper.UnitMapper;
import cn.datafuturex.zhishu.modules.service.UnitService;
import cn.datafuturex.zhishu.modules.vo.UnitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 单位管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitServiceImpl implements UnitService {

    private final UnitMapper unitMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnitEntity create(UnitCreateDTO dto) {
        validateParentExists(dto.parentId());

        String unitCode = resolveUnitCode(dto.unitCode());
        LambdaQueryWrapper<UnitEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnitEntity::getUnitCode, unitCode);
        if (unitMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("单位编码已存在: " + unitCode);
        }

        UnitEntity entity = new UnitEntity();
        entity.setParentId(dto.parentId());
        entity.setUnitCode(unitCode);
        entity.setUnitName(dto.unitName());
        entity.setUnitType(dto.unitType());
        entity.setRegion(dto.region());
        entity.setAddress(dto.address());
        entity.setContactPerson(dto.contactPerson());
        entity.setContactPhone(dto.contactPhone());
        entity.setSort(dto.sort());
        entity.setStatus(dto.status());
        entity.setRemark(dto.remark());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        unitMapper.insert(entity);
        log.info("创建单位成功: unitCode={}", entity.getUnitCode());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnitEntity update(UnitUpdateDTO dto) {
        UnitEntity entity = unitMapper.selectById(dto.id());
        if (entity == null) {
            throw new RuntimeException("单位不存在: id=" + dto.id());
        }

        if (dto.parentId() != null) {
            if (dto.parentId().equals(dto.id())) {
                throw new RuntimeException("父单位不能是自身");
            }
            validateParentExists(dto.parentId());
            validateNotDescendant(dto.id(), dto.parentId());
            entity.setParentId(dto.parentId());
        }

        if (StringUtils.hasText(dto.unitCode()) && !dto.unitCode().equals(entity.getUnitCode())) {
            LambdaQueryWrapper<UnitEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnitEntity::getUnitCode, dto.unitCode())
                    .ne(UnitEntity::getId, dto.id());
            if (unitMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("单位编码已存在: " + dto.unitCode());
            }
            entity.setUnitCode(dto.unitCode());
        }

        if (StringUtils.hasText(dto.unitName())) {
            entity.setUnitName(dto.unitName());
        }
        if (dto.unitType() != null) {
            entity.setUnitType(dto.unitType());
        }
        if (dto.region() != null) {
            entity.setRegion(dto.region());
        }
        if (dto.address() != null) {
            entity.setAddress(dto.address());
        }
        if (dto.contactPerson() != null) {
            entity.setContactPerson(dto.contactPerson());
        }
        if (dto.contactPhone() != null) {
            entity.setContactPhone(dto.contactPhone());
        }
        if (dto.sort() != null) {
            entity.setSort(dto.sort());
        }
        if (dto.status() != null) {
            entity.setStatus(dto.status());
        }
        if (dto.remark() != null) {
            entity.setRemark(dto.remark());
        }

        entity.setUpdateTime(LocalDateTime.now());
        unitMapper.updateById(entity);
        log.info("更新单位成功: id={}, unitCode={}", entity.getId(), entity.getUnitCode());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UnitEntity entity = unitMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("单位不存在: id=" + id);
        }

        LambdaQueryWrapper<UnitEntity> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(UnitEntity::getParentId, id);
        if (unitMapper.selectCount(childWrapper) > 0) {
            throw new RuntimeException("存在下级单位，无法删除");
        }

        unitMapper.deleteById(id);
        log.info("删除单位成功: id={}, unitCode={}", id, entity.getUnitCode());
    }

    @Override
    public Optional<UnitVO> findById(Long id) {
        UnitEntity entity = unitMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Map<Long, String> nameMap = loadUnitNameMap();
        return Optional.of(toVO(entity, nameMap));
    }

    @Override
    public PageResult<UnitVO> pageQuery(UnitQueryDTO query) {
        Page<UnitEntity> page = new Page<>(query.pageNum(), query.pageSize());
        LambdaQueryWrapper<UnitEntity> wrapper = buildQueryWrapper(query);
        wrapper.orderByAsc(UnitEntity::getSort).orderByDesc(UnitEntity::getCreateTime);

        Page<UnitEntity> resultPage = unitMapper.selectPage(page, wrapper);
        Map<Long, String> nameMap = loadUnitNameMap();
        List<UnitVO> records = resultPage.getRecords().stream()
                .map(entity -> toVO(entity, nameMap))
                .toList();

        PageResult<UnitVO> pageResult = new PageResult<>();
        pageResult.setCurrent(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPages(resultPage.getPages());
        pageResult.setRecords(records);
        return pageResult;
    }

    @Override
    public List<UnitVO> listTree(Integer status) {
        LambdaQueryWrapper<UnitEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(UnitEntity::getStatus, status);
        }
        wrapper.orderByAsc(UnitEntity::getSort).orderByAsc(UnitEntity::getId);
        List<UnitEntity> entities = unitMapper.selectList(wrapper);
        Map<Long, String> nameMap = entities.stream()
                .collect(Collectors.toMap(UnitEntity::getId, UnitEntity::getUnitName));
        List<UnitVO> nodes = entities.stream()
                .map(entity -> toVO(entity, nameMap))
                .toList();
        return buildTree(nodes);
    }

    @Override
    public List<UnitVO> listAllEnabled() {
        LambdaQueryWrapper<UnitEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UnitEntity::getStatus, 1)
                .orderByAsc(UnitEntity::getSort)
                .orderByAsc(UnitEntity::getId);
        Map<Long, String> nameMap = loadUnitNameMap();
        return unitMapper.selectList(wrapper).stream()
                .map(entity -> toVO(entity, nameMap))
                .toList();
    }

    private LambdaQueryWrapper<UnitEntity> buildQueryWrapper(UnitQueryDTO query) {
        LambdaQueryWrapper<UnitEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.unitCode())) {
            wrapper.like(UnitEntity::getUnitCode, query.unitCode());
        }
        if (StringUtils.hasText(query.unitName())) {
            wrapper.like(UnitEntity::getUnitName, query.unitName());
        }
        if (StringUtils.hasText(query.unitType())) {
            wrapper.eq(UnitEntity::getUnitType, query.unitType());
        }
        if (query.status() != null) {
            wrapper.eq(UnitEntity::getStatus, query.status());
        }
        if (query.parentId() != null) {
            wrapper.eq(UnitEntity::getParentId, query.parentId());
        }
        return wrapper;
    }

    private void validateParentExists(Long parentId) {
        if (parentId != null && parentId > 0 && unitMapper.selectById(parentId) == null) {
            throw new RuntimeException("父单位不存在: id=" + parentId);
        }
    }

    private void validateNotDescendant(Long unitId, Long newParentId) {
        if (newParentId == null || newParentId <= 0) {
            return;
        }
        Long currentId = newParentId;
        while (currentId != null && currentId > 0) {
            if (currentId.equals(unitId)) {
                throw new RuntimeException("父单位不能是当前单位的下级单位");
            }
            UnitEntity parent = unitMapper.selectById(currentId);
            currentId = parent != null ? parent.getParentId() : null;
        }
    }

    private Map<Long, String> loadUnitNameMap() {
        return unitMapper.selectList(null).stream()
                .collect(Collectors.toMap(UnitEntity::getId, UnitEntity::getUnitName));
    }

    private UnitVO toVO(UnitEntity entity, Map<Long, String> nameMap) {
        UnitVO vo = new UnitVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        if (entity.getParentId() != null && entity.getParentId() > 0) {
            vo.setParentName(nameMap.get(entity.getParentId()));
        }
        vo.setUnitCode(entity.getUnitCode());
        vo.setUnitName(entity.getUnitName());
        vo.setUnitType(entity.getUnitType());
        vo.setRegion(entity.getRegion());
        vo.setAddress(entity.getAddress());
        vo.setContactPerson(entity.getContactPerson());
        vo.setContactPhone(entity.getContactPhone());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private List<UnitVO> buildTree(List<UnitVO> nodes) {
        Map<Long, UnitVO> nodeMap = nodes.stream()
                .collect(Collectors.toMap(UnitVO::getId, node -> node, (a, b) -> a, LinkedHashMap::new));
        List<UnitVO> roots = new ArrayList<>();
        for (UnitVO node : nodes) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId <= 0) {
                roots.add(node);
                continue;
            }
            UnitVO parent = nodeMap.get(parentId);
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private String resolveUnitCode(String unitCode) {
        if (StringUtils.hasText(unitCode)) {
            return unitCode.trim();
        }
        return "UNIT-" + IdUtil.getSnowflakeNextIdStr();
    }
}
