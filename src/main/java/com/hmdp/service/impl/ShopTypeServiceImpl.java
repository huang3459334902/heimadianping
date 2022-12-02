package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result shopTypeList() {

        //查询缓存中的数据
        List<String> range = stringRedisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);
        //判断非空
        if (range.size() > 0) {
            List<ShopType> shopTypeList = new ArrayList<>();
            range.stream().map(item -> {
                ShopType shopType = JSONUtil.toBean(item, ShopType.class);
                shopTypeList.add(shopType);
                return shopTypeList;
            }).collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }

        //走数据库
        LambdaQueryWrapper<ShopType> shopTypeLambdaQueryWrapper = new LambdaQueryWrapper<>();
        shopTypeLambdaQueryWrapper.orderByAsc(ShopType::getSort);
        List<ShopType> shopTypeList = list(shopTypeLambdaQueryWrapper);

        if (shopTypeList.size() <= 0) {
            return Result.fail("店铺不存在");
        }

        //把数据缓存到redis中
        shopTypeList.stream().map(item -> {
            stringRedisTemplate.opsForList().leftPush(CACHE_SHOPTYPE_KEY, JSONUtil.toJsonStr(item));
            return null;
        }).collect(Collectors.toList());

        return Result.ok(shopTypeList);
    }
}
