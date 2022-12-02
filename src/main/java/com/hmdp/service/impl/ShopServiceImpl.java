package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result selectById(Long id) {

//        缓存穿透解决方案
//        Result shop = selectByIdPenetrate(id);

        //缓存击穿
        Result shop = selectByIdBreakdown(id);
        if (Objects.isNull(shop)) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    public Result selectByIdBreakdown(Long id) {

        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(s)) {
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return Result.ok(shop);
        }

        if (s != null) {
            return Result.fail("店铺不存在");
        }

        String lockKey = "lock:shop:"+id;
        Shop shop = null;

        try {
            //添加锁
            Boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //不为true则线程休眠 然后递归执行该方法
                Thread.sleep(50);
                return selectByIdBreakdown(id);
            }

            //把店铺数据写入缓存
            shop = getById(id);
            if (Objects.isNull(shop)) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",2, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }

            //写入缓存中 30分钟
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //关闭锁
            unlock(lockKey);
        }

        return Result.ok(shop);
    }

    public Result selectByIdPenetrate(Long id) {

        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(s)) {
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return Result.ok(shop);
        }

        if (s.equals("")) {
            return Result.fail("店铺不存在");
        }

        Shop shop = getById(id);
        if (Objects.isNull(shop)) {
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",2, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        //写入缓存中 30分钟
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
    }

    //添加锁
    private Boolean tryLock(String key) {
        Boolean flog = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flog);
    }

    //删除锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result update(Shop shop) {

        if (Objects.isNull(shop.getId())) {
            return Result.fail("id为空");
        }

        //更新数据库
        updateById(shop);

        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());

        return Result.ok();
    }
}
