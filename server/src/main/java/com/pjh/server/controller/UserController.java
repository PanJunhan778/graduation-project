package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.ResetPasswordDTO;
import com.pjh.server.dto.StaffCreateDTO;
import com.pjh.server.service.UserService;
import com.pjh.server.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@SaCheckRole("owner")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/list")
    public Result<IPage<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(userService.listUsers(page, size, keyword));
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid StaffCreateDTO dto) {
        userService.createStaff(dto);
        return Result.success("员工账号创建成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return Result.fail("状态值不能为空");
        }
        userService.updateUserStatus(id, status);
        return Result.success("状态更新成功", null);
    }

    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody @Valid ResetPasswordDTO dto) {
        userService.resetPassword(id, dto);
        return Result.success("密码重置成功", null);
    }
}
