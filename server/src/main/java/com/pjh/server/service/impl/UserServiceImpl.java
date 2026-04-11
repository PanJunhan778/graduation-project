package com.pjh.server.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.common.Constants;
import com.pjh.server.dto.ResetPasswordDTO;
import com.pjh.server.dto.StaffCreateDTO;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.UserService;
import com.pjh.server.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private Long getCurrentCompanyId() {
        Object companyId = StpUtil.getExtra(Constants.JWT_COMPANY_ID_KEY);
        if (companyId == null) {
            throw new BusinessException("无法获取当前公司信息");
        }
        return Long.parseLong(companyId.toString());
    }

    @Override
    public IPage<UserVO> listUsers(int page, int size, String keyword) {
        Long companyId = getCurrentCompanyId();

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getCompanyId, companyId)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(User::getUsername, keyword)
                                .or()
                                .like(User::getRealName, keyword))
                .orderByDesc(User::getCreatedTime);

        IPage<User> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);

        return userPage.convert(user -> {
            UserVO vo = new UserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            vo.setRole(user.getRole());
            vo.setStatus(user.getStatus());
            vo.setCreatedTime(user.getCreatedTime());
            return vo;
        });
    }

    @Override
    @Transactional
    public void createStaff(StaffCreateDTO dto) {
        if (!dto.getPassword().matches(Constants.PASSWORD_PATTERN)) {
            throw new BusinessException("密码至少 8 位，须包含大写字母、小写字母和数字");
        }

        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException("用户名已存在");
        }

        Long companyId = getCurrentCompanyId();

        User staff = new User();
        staff.setCompanyId(companyId);
        staff.setUsername(dto.getUsername());
        staff.setPassword(BCrypt.hashpw(dto.getPassword()));
        staff.setRole(Constants.ROLE_STAFF);
        staff.setRealName(dto.getRealName());
        staff.setStatus(1);
        userMapper.insert(staff);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long id, Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException("状态值无效");
        }

        Long companyId = getCurrentCompanyId();
        User user = userMapper.selectById(id);

        if (user == null || !companyId.equals(user.getCompanyId())) {
            throw new BusinessException("用户不存在");
        }
        if (!Constants.ROLE_STAFF.equals(user.getRole())) {
            throw new BusinessException("只能操作员工账号");
        }

        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetPasswordDTO dto) {
        if (!dto.getNewPassword().matches(Constants.PASSWORD_PATTERN)) {
            throw new BusinessException("密码至少 8 位，须包含大写字母、小写字母和数字");
        }

        Long companyId = getCurrentCompanyId();
        User user = userMapper.selectById(id);

        if (user == null || !companyId.equals(user.getCompanyId())) {
            throw new BusinessException("用户不存在");
        }
        if (!Constants.ROLE_STAFF.equals(user.getRole())) {
            throw new BusinessException("只能操作员工账号");
        }

        user.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        userMapper.updateById(user);
    }
}
