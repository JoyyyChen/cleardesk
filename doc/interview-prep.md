# 八股复习清单（一个月冲刺版）

前提：全职投入、八股零基础、一个月内开始投。目标不是"成为专家"，而是**面试问到时答得出、答得对、答得出为什么**。

## 0. 先说学习方式，这比清单本身重要

**别这么做：**
- 只看视频/博客不开口。看的时候觉得"我懂了"，一开口就卡 —— 这是最普遍的失败方式。
- 按目录从第一页背到最后一页。一个月背不完，且前松后紧，最后高频题没背。
- 只背结论不背原因。面试官只要追问一句"为什么"，背的答案立刻露馅。

**要这么做：**
1. **主动回忆 + 出声讲**。每个知识点合上资料，用自己的话讲 60 秒，讲不顺就是没懂。
2. **面试是口试，不是笔试**。所以你的练习方式必须是"说"，不是"看"或"默写"。
3. **每个知识点准备三层**：是什么 → 为什么这么设计 → 有什么代价/坑。第三层是拉开差距的地方。
4. **每天留 30 分钟复习前三天的内容**。遗忘曲线是真的，不复习等于白学。
5. **遇到讲不清的，回到项目里找例子**。比如背"索引失效场景"时，直接去看 `searchUsers` 为什么用 `like` 会出问题 —— 有真实例子的记忆不会忘。

## 1. 范围（一个月只能覆盖到这里，别贪）

| 模块 | 优先级 | 时间占比 | 说明 |
|------|--------|----------|------|
| MySQL | **P0 最高** | 20% | 后端面试必问，且你项目直接相关 |
| Redis | **P0** | 15% | 你项目在用（Session），必须讲得出原理 |
| Java 集合 | **P0** | 12% | HashMap 是必考题 |
| Java 并发 | **P0** | 15% | 线程池、锁、JMM；后端岗必问 |
| Spring | **P0** | 13% | IoC/AOP/事务，你的项目就是 Spring Boot |
| JVM | P1 | 10% | 可以只覆盖内存模型、GC、类加载 |
| 计算机网络 | P1 | 8% | TCP/HTTP/HTTPS，问到概率高但深度有限 |
| 算法（力扣 Hot 100） | P1 并行 | 每天 1 小时 | 大厂笔试必考；中小厂要求低一些 |
| Linux / Git | P2 | 2% | 会常用命令即可，别专门背 |
| 消息队列、分布式事务、微服务 | ❌ 这个月不学 | 0% | 项目里没有，硬背会被追问穿 |

**砍掉的东西不要有负罪感。** 把 P0 答好，比 P0~P2 都答一半强得多。

## 2. 四周日程

**第 1 周：** MySQL + Java 集合（这两块先啃，因为最硬且最高频）
**第 2 周：** Redis + Java 并发
**第 3 周：** Spring + JVM
**第 4 周：** 计算机网络 + 全量复习 + 模拟面试

算法**每天 1 小时贯穿全程**，不要停。刷题顺序：数组/字符串 → 链表 → 哈希表 → 二叉树 → 动态规划入门。目标不是刷完 100 道，而是**能把做过的题讲出思路**。

每天节奏建议（6 小时）：3 小时新知识 + 1 小时复习旧知识 + 1 小时算法 + 1 小时项目/其他。

## 3. P0：MySQL

**必须能答：**

- 索引
  - 聚簇索引和非聚簇索引的区别？为什么 InnoDB 必须有主键？没有主键会怎样？
  - 为什么用 B+ 树而不用 B 树 / 哈希 / 红黑树？（要能说出磁盘 IO 次数和范围查询两个角度）
  - 回表是什么？覆盖索引怎么避免回表？
  - 最左前缀原则：联合索引 `(a,b,c)`，哪些查询用得上索引？
  - 索引失效的常见场景（**至少说出 5 个**：函数操作列、隐式类型转换、`like '%x'` 前导通配、`or` 连接非索引列、不符合最左前缀、`!=`/`not in`）
- 事务
  - ACID 各自靠什么保证？
  - 四种隔离级别分别解决什么问题？MySQL 默认是哪个？为什么用 RR 而不是 RC？
  - MVCC 怎么实现的？（版本链 + undo log + ReadView）RR 和 RC 生成 ReadView 的时机差别在哪？
  - 间隙锁是什么？RR 下怎么解决幻读？
- 锁
  - 共享锁/排他锁、表锁/行锁、乐观锁/悲观锁的区别
  - 死锁怎么产生、怎么排查、怎么避免？
- 日志
  - redo log、undo log、binlog 各自作用？为什么需要两阶段提交？
- 优化
  - `explain` 每个字段什么意思？看到 `type=ALL`、`Extra=Using filesort` 说明什么？
  - 慢查询怎么定位？
- SQL
  - 手写：分组取每组前 N、行转列、连续 N 天登录（这三道是高频笔试题）

**结合你项目的追问：**「`/user/search` 按 `username` 模糊查询，如果数据量到 100 万，你会怎么优化？」（回答要能提到：前缀匹配才能用索引、全文索引/ES、或者限制只能前缀搜）

## 4. P0：Redis

**必须能答：**

- 五种基本类型 + 各自典型场景；`ziplist`/`skiplist`/`hashtable` 等底层结构分别用于什么情况
- 为什么 Redis 快？（内存、单线程避免锁竞争、IO 多路复用、高效数据结构）
- **缓存三大问题**（必问，要能说出成因 + 方案 + 方案的副作用）：
  - 穿透：查不存在的数据 → 缓存空值 / 布隆过滤器
  - 击穿：热点 key 过期瞬间 → 互斥锁 / 逻辑过期
  - 雪崩：大量 key 同时过期 / Redis 挂了 → 随机过期时间 / 集群 / 降级
- 缓存与数据库一致性：先更新库还是先删缓存？延迟双删解决什么？为什么要删而不是更新？
- 过期删除策略（惰性 + 定期）和内存淘汰策略（LRU/LFU/random，至少说出 `allkeys-lru` 和 `volatile-lru` 的区别）
- 持久化：RDB 和 AOF 的区别、各自优缺点、混合持久化
- 分布式锁：`SETNX` 的问题（误删别人锁 → 要校验 value；锁过期业务没完 → 看门狗续期）；Redisson 怎么实现的
- 主从复制、哨兵、Cluster 各自解决什么问题？

**结合你项目的追问：**「你的 Session 存在 Redis 里，如果 Redis 挂了会怎样？」「`cleardesk:session` 这个命名空间的 key 什么时候过期？过期后用户会看到什么？」（这两个问题能答好，比背十条八股有用）

## 5. P0：Java 集合

**必须能答：**

- `HashMap`：底层结构（数组+链表+红黑树）、put 流程、扩容机制（为什么是 2 倍、为什么容量是 2 的幂）、为什么线程不安全（1.7 头插死循环 / 1.8 数据覆盖）
- `HashMap` vs `Hashtable` vs `ConcurrentHashMap`：`ConcurrentHashMap` 怎么保证线程安全（1.7 分段锁 / 1.8 CAS + synchronized 锁桶头）
- `hashCode` 和 `equals` 的契约；为什么重写 `equals` 必须重写 `hashCode`
- `ArrayList` vs `LinkedList`；`ArrayList` 扩容规则
- `HashMap` 遍历时修改为什么会 `ConcurrentModificationException`（fail-fast 机制）

## 6. P0：Java 并发

**必须能答：**

- 线程的 6 种状态与流转；`sleep` 和 `wait` 的区别（是否释放锁、是否需要 notify 唤醒）
- `synchronized`：修饰方法/代码块的锁对象分别是谁？锁升级过程（偏向锁→轻量级→重量级）
- `volatile`：保证可见性和有序性但**不保证原子性**（要能解释为什么 `i++` 不行）
- JMM、happens-before 原则
- CAS 是什么？ABA 问题及解决（版本号/`AtomicStampedReference`）
- **线程池（最高频，必须非常熟）**：
  - 7 个核心参数分别是什么
  - 任务提交后的完整执行流程（核心线程 → 队列 → 最大线程 → 拒绝策略，**顺序不能错**）
  - 4 种拒绝策略
  - 为什么阿里规范不推荐 `Executors` 创建线程池（`FixedThreadPool` 队列无界会 OOM、`CachedThreadPool` 线程数无界）
  - 线程数怎么定？（CPU 密集 N+1，IO 密集 2N 起步，要能说出依据）
- `ThreadLocal`：原理（每个 Thread 有自己的 ThreadLocalMap）、内存泄漏原因（key 弱引用 value 强引用）、为什么必须 `remove()`
  - **你项目里就有现成例子**：`UserHolder` 用的是 `ThreadLocal`，`AuthInterceptor.afterCompletion` 里必须 `remove()`。这题你比大多数人答得有底气。
- `AQS` 是什么？`ReentrantLock` 和 `synchronized` 的区别
- 死锁的四个必要条件

## 7. P0：Spring

**必须能答：**

- IoC 和 DI 是什么？解决了什么问题？
- Bean 的生命周期（至少说出：实例化 → 属性填充 → `Aware` 回调 → `BeanPostProcessor` 前置 → `@PostConstruct`/`InitializingBean` → 后置处理（AOP 代理在这里生成）→ 使用 → 销毁）
- 三级缓存解决什么问题？（循环依赖；**并且要能说出构造函数注入的循环依赖解决不了**）
  - **你项目里就有例子**：`AuthInterceptor` 和 `UserService` 循环依赖，用 `@Lazy` 解决的。这个能讲清就很好。
- AOP 原理：JDK 动态代理 vs CGLIB，什么时候用哪个
- **`@Transactional` 失效的场景（超高频，至少说出 4 个）**：
  - 方法不是 public
  - 同类内部方法直接调用（没走代理）
  - 异常被 catch 没抛出
  - 抛出的是受检异常（默认只回滚 `RuntimeException` 和 `Error`）
  - 多线程调用
  - 数据库引擎不支持事务
- 事务传播行为（重点 `REQUIRED` 和 `REQUIRES_NEW`）
- Spring MVC 请求处理流程（DispatcherServlet → HandlerMapping → HandlerAdapter → 拦截器 → 返回）
  - **结合你项目**：`AuthInterceptor` 是在哪个环节执行的？`preHandle` 返回 false 会怎样？
- `@Resource` 和 `@Autowired` 的区别
- Spring Boot 自动配置原理（`@SpringBootApplication` 里的三个注解分别做什么）

**你项目的隐藏考点**：`UserServiceImpl` 里 `userRegister` 目前**没有加 `@Transactional`**。想清楚：这个方法需要事务吗？只做一次 insert 的时候需不需要？如果以后注册要同时写两张表呢？这类"我自己项目里的取舍"是很好的面试素材。

## 8. P1：JVM

只覆盖高频，不要陷进去：

- 运行时内存区域（堆、栈、方法区/元空间、程序计数器、本地方法栈），哪些线程共享
- 对象创建过程、对象在内存中的布局（对象头存什么）
- 判断对象可回收：引用计数 vs 可达性分析；四种引用（强/软/弱/虚）
- GC 算法：标记清除/标记复制/标记整理，各自优缺点
- 分代收集：新生代为什么用复制算法？对象什么时候进老年代？
- 常见垃圾收集器（至少知道 CMS、G1 的特点和区别；ZGC 可以只提名字）
- 类加载过程（加载→验证→准备→解析→初始化）；双亲委派模型及为什么要打破（Tomcat/SPI）
- 常用排查工具：`jps`、`jstack`、`jmap`、`jstat` 各看什么；OOM 和 CPU 飙高怎么排查

## 9. P1：计算机网络

- OSI 七层 / TCP-IP 四层，每层有哪些协议
- **TCP 三次握手**：为什么是三次不是两次？（要能说出防止历史连接、同步双方序号）
- **四次挥手**：为什么是四次？`TIME_WAIT` 的作用和为什么是 2MSL？大量 `CLOSE_WAIT` 说明什么？
- TCP 如何保证可靠传输（序号、确认、重传、滑动窗口、流量控制、拥塞控制）
- TCP 和 UDP 区别及各自适用场景
- HTTP 常见状态码（重点 301/302、304、400/401/403/404、500/502/504）
  - **你项目相关**：现在所有错误都返回 HTTP 200 + body 里的业务错误码，这样设计有什么问题？（这是个能聊出深度的点）
- HTTP 1.0 / 1.1 / 2.0 / 3.0 的主要改进（重点：长连接、多路复用、队头阻塞）
- HTTP 和 HTTPS 的区别；TLS 握手流程（简化版即可）；对称加密 + 非对称加密怎么配合
- Cookie / Session / Token / JWT 的区别与适用场景
  - **你项目用 Session + Redis**，这题必须能主动讲出为什么、以及和 JWT 的取舍
- 从输入 URL 到页面展示发生了什么（综合题，能串起 DNS、TCP、HTTP、渲染）

## 10. P2：Linux / Git / 其他

- Linux：`top`/`ps`/`netstat`/`grep`/`tail -f`/`df -h`/`chmod`/`find` 够用即可
- Git：`rebase` vs `merge`、`reset` vs `revert`、冲突怎么解
- 设计模式：**单例（双重检查锁 + volatile）、工厂、策略、代理、模板方法**，能结合 Spring 里的实例讲
- 反问面试官的问题准备 3 个（比如团队技术栈、代码 review 流程、新人上手周期）

## 11. 明确不要花时间的

- 消息队列（Kafka/RocketMQ）原理 —— 项目里没有，硬背必被追问穿
- 分布式事务、分布式 ID、微服务治理、Service Mesh
- 中间件源码级原理（Netty 线程模型、Redis 源码）
- Kubernetes（会 `docker` + `docker compose` 足够支撑简历）
- 各种"最新框架"（三个月换一茬，面试官也未必熟）

## 12. 自测：用你自己的项目出 8 道题

这 8 道来自本项目真实代码，能流畅答出 6 道以上，说明你到"能撑住追问"的程度了。答不上来的，就是这周的复习重点。

1. 鉴权拦截器每个请求都查一次数据库，QPS 上来会怎样？给出两种优化方案及各自的风险。
2. `UserHolder` 用了 `ThreadLocal`，如果 `afterCompletion` 里忘记 `remove()` 会发生什么？为什么？
3. `AuthInterceptor` 和 `UserService` 循环依赖，怎么解决的？为什么构造函数注入解决不了？
4. 注册时既然有唯一索引，为什么还要先 `count` 一次？并发下这两步分别会发生什么？
5. 登录失败时账号格式错、账号不存在、密码错都返回同一文案，为什么？这个做法有什么副作用？
6. 逻辑删除是怎么实现的？它对 SQL 和索引有什么影响？
7. 现在错误响应都是 HTTP 200 + body 里的 `code`，这样设计的利弊是什么？
8. `/user/search` 只支持按 `username` 模糊查询，数据量大了怎么优化？为什么 `like '%x'` 用不上索引？

## 13. 演示脚本（面试时照着念）

**地址：** `http://123.57.252.56:8080/api`（阿里云轻量机，Ubuntu 22.04 + Docker Compose）

### 演示前 30 秒自检

```bash
ssh root@123.57.252.56
cd /opt/cleardesk && bash scripts/smoke-test.sh     # 必须"失败 0 项"
```

挂了就一条命令拉回来（数据在 Docker 卷里，不会丢）：

```bash
cd /opt/cleardesk && docker compose -f docker-compose.prod.yml -f docker-compose.ip-only.yml up -d
```

### 场景一：注册 + 登录 + 鉴权（讲"Session 存 Redis"）

```bash
BASE=http://123.57.252.56:8080/api

# 1) 注册一个新账号
curl -s -X POST $BASE/user/register -H 'Content-Type: application/json' \
  -d '{"userAccount":"demo001","userPassword":"12345678","checkPassword":"12345678"}'
# 预期 {"code":0,"data":4,...}

# 2) 未登录访问受保护接口 → 被拦
curl -s $BASE/user/current
# 预期 code 非 0，提示未登录

# 3) 用管理员账号登录，cookie 落到 /tmp/ck.txt
curl -s -c /tmp/ck.txt -X POST $BASE/user/login -H 'Content-Type: application/json' \
  -d '{"userAccount":"probe001","userPassword":"12345678"}'
# 预期 {"code":0,"data":{...userRole":1...}}

# 4) 带上 cookie 再访问 → 通过，看 @AuthCheck 生效
curl -s -b /tmp/ck.txt $BASE/user/current
```

**这里主动说**：登录态没存在应用内存里，而是 Spring Session 序列化进 Redis（命名空间 `cleardesk:session`），
所以应用重启登录态不丢、以后多实例部署也能共享。Session 里**只存用户 id**，角色和状态每次从库里读——
好处是改了角色立刻生效、不用等 Session 过期，代价是每个请求多一次库查询（第 12 节第 1 题就是问这个）。

### 场景二：管理员搜索 + 逻辑删除（讲"唯一索引 + 逻辑删除"）

```bash
# 5) 管理员分页搜索
curl -s -b /tmp/ck.txt "$BASE/user/search?current=1&pageSize=10"
# 预期 {"code":0,"data":{"records":[...],"total":4,...}}

# 6) 删除 demo001（id 用上一步返回值里的）
curl -s -b /tmp/ck.txt -X POST $BASE/user/delete -H 'Content-Type: application/json' -d '{"id":4}'
# 预期 {"code":0,"data":true}

# 7) 再搜索一次 → demo001 消失了
curl -s -b /tmp/ck.txt "$BASE/user/search?current=1&pageSize=10"

# 8) 删自己 → 被拒绝（这是我特意加的保护）
curl -s -b /tmp/ck.txt -X POST $BASE/user/delete -H 'Content-Type: application/json' -d '{"id":1}'
# 预期 {"code":... ,"message":"不能删除当前登录账号"}
```

**三个可主动抛的点：**

1. **删除是逻辑删除**（`isDelete=1`），不是物理删。MyBatis-Plus 全局配了 `logic-delete-field`，
   业务代码里只调 `removeById`，框架自动把 `delete` 改写成 `update`，查询也自动带 `isDelete=0`。
   代价：表会一直膨胀，且 `userAccount` 唯一索引会和已删除的记录冲突（想复用账号要另想办法）。
2. **第 8 步的保护**：管理员把自己删了就再也没人能做管理操作，所以服务层直接拒绝。
3. **注册为什么既有唯一索引又先查一次**：先查是为了给出友好提示，唯一索引是并发下的最后防线——
   两个请求同时通过检查时，数据库会拦住第二个（第 12 节第 4 题）。

### 如果面试官让你用 Postman / Apifox

选 POST，URL 填 `http://123.57.252.56:8080/api/user/login`，Body 选 raw → JSON，
粘 `{"userAccount":"probe001","userPassword":"12345678"}`，Send。
**工具会自动保存 Cookie**（比 curl 的 `-c/-b` 省事），后面 `/user/current`、`/user/search` 直接换 URL 即可。

### 演示时别踩的坑

| 坑 | 后果 | 规避 |
|----|------|------|
| 用 `https://` 访问 8080 | 日志报 `Invalid character found in method name`，请求失败 | 这个阶段只有 HTTP，等接了域名再上 HTTPS |
| 忘了 `-b /tmp/ck.txt` | 所有需要登录的接口都返回未登录 | 上面每步都带了，照抄别漏 |
| 用 `probe002` 登录去搜索 | 403（它是普通用户） | 只有 `probe001` 是管理员 |
| 机器重启后没等 30 秒 | MySQL 还在初始化就访问，报连接失败 | `restart: unless-stopped` 会自动拉起，等自检变绿 |

### 面试官追问部署（答不上来最扣分的三题）

1. **"数据库端口开着吗？"** → 没开。编排里 MySQL 和 Redis **不映射宿主机端口**，只在 Docker 内网暴露，
   安全组也只有 22 和 8080。远程连库走 SSH 隧道，不开放 3306。
2. **"应用为什么用 `mysql` 而不是 `localhost` 连库？"** → 容器有独立的网络命名空间，
   `localhost` 指容器自己；Compose 的 service 名由 Docker 内部 DNS 解析成内网 IP。
3. **"启动顺序怎么保证的？"** → `depends_on` 只保证启动顺序、不保证 MySQL 已就绪，
   所以配了 `healthcheck` + `condition: service_healthy`，MySQL 健康检查通过才起应用。

> 详细原理和完整踩坑记录在 `doc/deployment.md` 和 `doc/deploy-runbook.md`。

## 14. 面试当天

- 不会的**别硬编**。说"这块我了解得不深，我的理解是……可能是错的"比编一个被拆穿的答案好得多。
- 面试官问"你项目里最难的点是什么" —— 提前准备好一个真实答案（建议用你部署、压测或迁移 Boot 4 的经历）。
- 每次面试后**当天记录被问到的题**，答不上来的立刻补。前 3 场面试的收获最大，所以**早点开始投**，别等"准备好"。
