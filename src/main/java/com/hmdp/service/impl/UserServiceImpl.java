package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private IUserService iUserService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {

        //校验手机号格式是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }

        //符合 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存到缓存中 设置2分钟有效时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL,TimeUnit.MINUTES);
        log.info("短语验证码发送成功----"+code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        //验证手机号格式
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机格式错误");
        }

        //验证验证码是否正确
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if (Objects.isNull(cacheCode) || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }

        //查询该手机号的 user
        LambdaQueryWrapper<User> userLambdaQueryWrapper = new LambdaQueryWrapper<>();
        userLambdaQueryWrapper.eq(User::getPhone,loginForm.getPhone());
        User user = iUserService.getOne(userLambdaQueryWrapper);
        if (Objects.isNull(user)) {
            user = createUserWithPhone(loginForm.getPhone());
        }

        //使用 UUID 生成一个 token
        String token = UUID.randomUUID().toString();

        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user,userDTO);

        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("id",String.valueOf(userDTO.getId()));
        hashMap.put("nickName",String.valueOf(userDTO.getNickName()));
        hashMap.put("icon",String.valueOf(userDTO.getIcon()));


        //将用户写入缓存中 token 为键
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token,hashMap);
        //设置 token 有效期 30分钟
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,30,TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User().setPhone(phone).setNickName("user_" + RandomUtil.randomString(10));
        iUserService.save(user);
        return user;
    }
}
