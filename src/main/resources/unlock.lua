---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by hhh.
--- DateTime: 2022/12/2 21:49
---

---比较线程标识与锁中的标识是否一致
---KEYS[1] 锁的名称
---ARGV[1] 线程标识

if (redis.call('get',KEYS[1]) == ARGV[1]) then
    return redis.call('del',KEYS[1])
end
return 0