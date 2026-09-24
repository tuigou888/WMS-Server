package com.wms.service;

import com.wms.common.BusinessException;
import com.wms.model.entity.UserAccount;
import com.wms.model.entity.UserWarehouseAccess;
import com.wms.repository.UserWarehouseAccessRepository;
import com.wms.repository.WarehouseRepository;
import com.wms.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class WarehouseAccessService {
    private final UserWarehouseAccessRepository access;
    private final WarehouseRepository warehouses;

    public WarehouseAccessService(UserWarehouseAccessRepository access, WarehouseRepository warehouses) {
        this.access = access;
        this.warehouses = warehouses;
    }

    public boolean isWarehouseScoped() {
        var user = SecurityUtils.currentUser();
        return user != null && "WAREHOUSE".equals(user.role());
    }

    public List<Long> currentWarehouseIds() {
        var user = SecurityUtils.currentUser();
        if (user == null || !"WAREHOUSE".equals(user.role())) return List.of();
        List<Long> ids = access.findWarehouseIdsByUserId(userId(user.username()));
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    public boolean canAccess(Long warehouseId) {
        if (!isWarehouseScoped()) return true;
        return access.existsByUsernameAndWarehouseId(SecurityUtils.username(), warehouseId);
    }

    public void require(Long warehouseId) {
        if (!canAccess(warehouseId)) throw new AccessDeniedException("无权访问该仓库");
    }

    @Transactional
    public void replaceAssignments(UserAccount user, List<Long> requestedIds) {
        List<Long> ids = requestedIds == null ? List.of() : requestedIds.stream().filter(Objects::nonNull).distinct().toList();
        if ("WAREHOUSE".equals(user.getRole()) && ids.isEmpty()) throw new BusinessException("仓库操作员至少需要分配一个仓库");
        if (warehouses.findAllById(ids).size() != ids.size()) throw new BusinessException("仓库授权包含不存在的仓库");
        access.deleteByUser_Id(user.getId());
        access.saveAll(ids.stream().map(id -> new UserWarehouseAccess(user, warehouses.getReferenceById(id))).toList());
    }

    public List<Long> assignedWarehouseIds(Long userId) {
        return access.findWarehouseIdsByUserId(userId);
    }

    private Long userId(String username) {
        return accessUserId(username);
    }

    private Long accessUserId(String username) {
        return access.findUserIdByUsername(username).orElseThrow(() -> new AccessDeniedException("用户不存在"));
    }
}
