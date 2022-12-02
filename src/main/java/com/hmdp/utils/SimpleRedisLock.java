package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID() + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean tryLock(long timeoutSec) {

        String id = ID_PREFIX + Thread.currentThread().getId();

        // setIfAbsent 如果不存在 就执行
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, id, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //调用lua脚本 解决分布式锁 删除锁的原子性问题
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX+name),ID_PREFIX + Thread.currentThread().getId());

    }

    /*@Override
    public void unlock() {

        // 线程标识
        String id = ID_PREFIX + Thread.currentThread().getId();

        String s = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        //当缓存中的缓存 s 与 id 不一致 则不删除锁
        if (id.equals(s)) {
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }

    }*/
}
