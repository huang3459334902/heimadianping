package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }

        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束了");
        }

        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        // intern() 如果池中已经包含了一个等于这个String对象的字符串 则返回池中的字符串
//        synchronized (UserHolder.getUser().getId().toString().intern()) {

        //创建锁
        SimpleRedisLock lock = new SimpleRedisLock("order:" + voucherId, stringRedisTemplate);
        //获取锁
        boolean b = lock.tryLock(500);
        if (!b) {
            return Result.fail("别作弊了");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
//        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        Long id = UserHolder.getUser().getId();

        int count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("已有优惠卷,不能重复领取");
        }

        //扣减库存
        boolean flog = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                // 当当前 stock > 0 时 才执行
                .gt("stock", 0)
                .update();

        if (!flog) {
            return Result.fail("库存不足");
        }

        //随机生成id
        long order = redisIdWorker.nextId("order");

        VoucherOrder voucherOrder = new VoucherOrder().setId(order)
                .setUserId(UserHolder.getUser().getId())
                .setVoucherId(voucherId);

        save(voucherOrder);

        return Result.ok(order);

    }
}
