package com.pjh.server.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pjh.server.common.Constants;
import com.pjh.server.dto.LoginDTO;
import com.pjh.server.dto.RegisterDTO;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.AuthService;
import com.pjh.server.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;

    private final Cache<String, Integer> loginFailCache = Caffeine.newBuilder()
            .expireAfterWrite(Constants.LOGIN_LOCK_MINUTES, TimeUnit.MINUTES)
            .build();

    @Override
    public LoginVO login(LoginDTO dto) {
        String cacheKey = "login_fail:" + dto.getUsername();
        Integer failCount = loginFailCache.getIfPresent(cacheKey);
        if (failCount != null && failCount >= Constants.LOGIN_MAX_RETRY) {
            throw new BusinessException("账号已被锁定，请 " + Constants.LOGIN_LOCK_MINUTES + " 分钟后再试");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );

        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            int current = (failCount == null) ? 1 : failCount + 1;
            loginFailCache.put(cacheKey, current);
            int remaining = Constants.LOGIN_MAX_RETRY - current;
            if (remaining <= 0) {
                throw new BusinessException("账号已被锁定，请 " + Constants.LOGIN_LOCK_MINUTES + " 分钟后再试");
            }
            throw new BusinessException("用户名或密码错误，还剩 " + remaining + " 次机会");
        }

        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        loginFailCache.invalidate(cacheKey);

        Long companyId = user.getCompanyId();
        String companyName = null;
        String companyCode = null;
        String industry = null;
        String taxpayerType = null;
        if (companyId != null) {
            Company company = companyMapper.selectById(companyId);
            if (company != null) {
                if (company.getStatus() != null && company.getStatus() == 0) {
                    throw new BusinessException("该企业已被禁用，请联系管理员");
                }
                companyName = company.getName();
                companyCode = company.getCompanyCode();
                industry = company.getIndustry();
                taxpayerType = company.getTaxpayerType();
            }
        }

        StpUtil.login(user.getId(),
                SaLoginConfig.setExtra(Constants.JWT_USER_ID_KEY, user.getId())
                        .setExtra(Constants.JWT_ROLE_KEY, user.getRole())
                        .setExtra(Constants.JWT_COMPANY_ID_KEY, companyId)
        );

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setRole(user.getRole());
        vo.setRealName(user.getRealName());
        vo.setCompanyName(companyName);
        vo.setCompanyCode(companyCode);
        vo.setIndustry(industry);
        vo.setTaxpayerType(taxpayerType);
        return vo;
    }

    @Override
    public void register(RegisterDTO dto) {
        if (!dto.getPassword().matches(Constants.PASSWORD_PATTERN)) {
            throw new BusinessException("密码至少 8 位，须包含大写字母、小写字母和数字");
        }

        Long existCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (existCount > 0) {
            throw new BusinessException("用户名已存在");
        }

        Company company = companyMapper.selectOne(
                new LambdaQueryWrapper<Company>().eq(Company::getCompanyCode, dto.getCompanyCode())
        );
        if (company == null) {
            throw new BusinessException("企业码无效，请检查后重试");
        }

        User user = new User();
        user.setCompanyId(company.getId());
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setRole(Constants.ROLE_STAFF);
        user.setRealName(dto.getRealName());
        user.setStatus(1);
        userMapper.insert(user);
    }
}
