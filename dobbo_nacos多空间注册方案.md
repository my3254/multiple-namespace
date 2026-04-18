# Dubbo + Nacos 多命名空间注册中心扩展技术实现详细文档

## 1. 文档目标

本文档给出一个可以直接落地开发的 **Dubbo + Nacos 多命名空间注册中心扩展方案**，目标如下：

1. 服务提供者支持注册到多个 Nacos namespace
2. 服务消费者优先从主 namespace 获取实例
3. 当主 namespace 没有可用实例时，自动从其他 namespace 降级获取
4. 实现过程中尽量少改业务侧 Dubbo 使用方式
5. 明确到“具体实现方案 + 具体类 + 每个类要做什么 + 方法职责 + 时序流程 + 风险控制”

---

## 2. 设计定位

这个需求不要实现成“Dubbo 自定义 RPC 协议”，而应该实现成：

- **自定义 Dubbo Registry 扩展**
- 必要时，再继续下沉兼容 Dubbo 3 的 **ServiceDiscovery 扩展**

也就是说，新增的不是传输协议，而是一个 **多命名空间注册发现增强层**。

推荐协议名：

```properties
huanxin-nacos://
```

示例配置：

```yaml
dubbo:
  registry:
    address: huanxin-nacos://127.0.0.1:8848
    username: nacos
    password: nacos
    parameters:
      registerNamespaces: ns-order,ns-shared
      subscribeNamespaces: ns-order,ns-shared,public
      primaryNamespace: ns-order
      namespaceSelectMode: primary-first
      namespaceFallback: true
      registerFailMode: partial-success
      queryParallel: true
      queryTimeoutMs: 1500
      healthyOnly: true
      allowedFallbackNamespaces: ns-shared,public
      forbiddenFallbackNamespaces: ns-test,ns-dev
      requiredInstanceMetadata.env: prod
      requiredInstanceMetadata.tenant: t1
```

---

## 3. 实现边界

## 3.1 本期必须实现

### Provider 侧
- 注册到多个 namespace
- 多 namespace 注销
- 注册失败策略控制
- 心跳由各 namespace 对应 Nacos 客户端自行维护

### Consumer 侧
- 主 namespace 优先查询
- 主为空时 fallback 到其他 namespace
- 查询结果聚合
- 查询结果去重
- 支持订阅变更并刷新地址列表

### 公共能力
- 配置解析
- namespace 白名单 / 黑名单
- metadata 过滤
- 本地缓存
- 日志 / 指标埋点

## 3.2 本期不强制实现

- 动态配置热刷新
- 与配置中心联动
- 多注册中心并存路由
- 灰度比例切流
- UI 管理后台
- 强一致跨 namespace 事务注册

---

## 4. 总体架构

```text
业务配置
   │
   ▼
huanxin-nacos:// RegistryFactory
   │
   ▼
MultiNamespaceNacosRegistry
   ├── Provider 侧：多 namespace register / unregister
   ├── Consumer 侧：多 namespace subscribe / lookup
   ├── 聚合层：实例聚合、去重、优先级选择
   ├── 缓存层：namespace 缓存、聚合缓存
   └── 适配层：多个 NamingService
            ├── ns-a -> NamingService A
            ├── ns-b -> NamingService B
            └── ns-c -> NamingService C
```

---

## 5. 模块划分

建议拆成 3 个模块：

## 5.1 dubbo-multins-registry-core

职责：
- 配置模型
- 选择策略
- 实例聚合与去重
- metadata 过滤
- 缓存抽象
- 通用异常处理
- 日志埋点工具

建议包结构：

```text
com.xxx.multins.core
├── config
├── model
├── policy
├── aggregator
├── cache
├── util
└── exception
```

## 5.2 dubbo-multins-nacos

职责：
- Nacos NamingService 创建
- 多 namespace register / unregister / subscribe / lookup
- Nacos Instance 与 Dubbo URL 转换
- Nacos 监听事件桥接

建议包结构：

```text
com.xxx.multins.nacos
├── registry
├── naming
├── listener
├── converter
└── support
```

## 5.3 dubbo-multins-spring-boot-starter

职责：
- Spring Boot 自动装配
- 配置绑定
- SPI 资源辅助加载
- 默认线程池、默认缓存实现

---

## 6. 类清单与职责分配

这一部分是重点，直接说明“具体要写哪些类”。

---

## 6.1 配置相关类

### 6.1.1 `MultiNamespaceRegistryConfig`
职责：
- 承载完整配置
- 被整个系统共享使用

建议字段：

```java
public class MultiNamespaceRegistryConfig {

    private String serverAddr;
    private String username;
    private String password;

    private List<String> registerNamespaces;
    private List<String> subscribeNamespaces;
    private String primaryNamespace;

    private NamespaceSelectMode namespaceSelectMode;
    private boolean namespaceFallback;
    private RegisterFailMode registerFailMode;

    private boolean queryParallel;
    private long queryTimeoutMs;
    private boolean healthyOnly;

    private List<String> allowedFallbackNamespaces;
    private List<String> forbiddenFallbackNamespaces;

    private Map<String, String> requiredInstanceMetadata;
}
```

### 6.1.2 `NamespaceSelectMode`
职责：
- 定义 namespace 查询模式

```java
public enum NamespaceSelectMode {
    PRIMARY_ONLY,
    PRIMARY_FIRST,
    MERGE_ALL
}
```

### 6.1.3 `RegisterFailMode`
职责：
- 定义 provider 注册失败语义

```java
public enum RegisterFailMode {
    ALL_SUCCESS,
    PARTIAL_SUCCESS
}
```

### 6.1.4 `MultiNamespaceRegistryConfigParser`
职责：
- 从 Dubbo `URL` 中解析出多 namespace 配置
- 对参数做默认值补齐
- 做配置合法性校验

必须做的校验：
1. `primaryNamespace` 不能为空
2. `primaryNamespace` 必须属于 `subscribeNamespaces`
3. `registerNamespaces`、`subscribeNamespaces` 去重
4. 空白 namespace 去除
5. 黑白名单不能同时互相冲突

建议方法：

```java
public final class MultiNamespaceRegistryConfigParser {

    public static MultiNamespaceRegistryConfig parse(URL url) {
        // 解析 registry.address, username, password, parameters
    }

    private static void validate(MultiNamespaceRegistryConfig config) {
        // 合法性校验
    }
}
```

---

## 6.2 Nacos 客户端管理类

### 6.2.1 `NacosNamingServiceHolder`
职责：
- 管理多个 namespace 对应的 `NamingService`
- 提供懒加载或初始化时预加载
- 提供统一获取入口
- 负责关闭资源

关键字段：

```java
private final Map<String, NamingService> namingServiceMap = new ConcurrentHashMap<>();
```

关键方法：

```java
public class NacosNamingServiceHolder {

    public void init(MultiNamespaceRegistryConfig config) { }

    public NamingService get(String namespace) { }

    public Set<String> namespaces() { }

    public void destroy() { }
}
```

### 6.2.2 `NacosNamingServiceFactory`
职责：
- 专门创建单个 namespace 对应的 Nacos `NamingService`

```java
public class NacosNamingServiceFactory {

    public NamingService create(String serverAddr, String namespace, String username, String password) {
        Properties p = new Properties();
        p.put("serverAddr", serverAddr);
        p.put("namespace", namespace);
        if (username != null) {
            p.put("username", username);
        }
        if (password != null) {
            p.put("password", password);
        }
        return NamingFactory.createNamingService(p);
    }
}
```

说明：
- 一个 `NamingService` 只绑定一个 namespace
- 所以多 namespace 必须创建多个客户端实例

---

## 6.3 实例模型与转换类

### 6.3.1 `AggregatedInstance`
职责：
- 表示聚合后的统一实例模型
- 用于跨 namespace 聚合、去重、过滤、排序

```java
public class AggregatedInstance {
    private String serviceName;
    private String ip;
    private int port;
    private boolean healthy;
    private double weight;

    private String chosenNamespace;
    private List<String> availableNamespaces = new ArrayList<>();

    private Map<String, String> metadata = new HashMap<>();
}
```

### 6.3.2 `InstanceDedupKeyBuilder`
职责：
- 构建实例去重 key

建议优先顺序：
1. `metadata.instanceUniqueKey`
2. `serviceName + ip + port`
3. `ip + port`

```java
public class InstanceDedupKeyBuilder {

    public String build(AggregatedInstance instance) {
        String unique = instance.getMetadata().get("instanceUniqueKey");
        if (unique != null && !unique.isBlank()) {
            return unique;
        }
        return instance.getServiceName() + "#" + instance.getIp() + ":" + instance.getPort();
    }
}
```

### 6.3.3 `DubboUrlToNacosInstanceConverter`
职责：
- 把 Dubbo `URL` 转成 Nacos `Instance`
- 补充 metadata

注意点：
- provider 注册时 metadata 要保留 Dubbo 关键参数
- 建议写入以下 metadata：
  - `instanceUniqueKey`
  - `dubboApplication`
  - `multiNsRegistered=true`

```java
public class DubboUrlToNacosInstanceConverter {

    public Instance convert(URL url) {
        Instance instance = new Instance();
        instance.setIp(url.getHost());
        instance.setPort(url.getPort());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("instanceUniqueKey", url.getHost() + ":" + url.getPort());
        metadata.put("multiNsRegistered", "true");
        metadata.put("dubboApplication", url.getParameter("application", ""));
        instance.setMetadata(metadata);

        return instance;
    }
}
```

### 6.3.4 `NacosInstanceToAggregatedInstanceConverter`
职责：
- 将 Nacos `Instance` 转换为 `AggregatedInstance`
- 在转换时补上 `sourceNamespace`

```java
public class NacosInstanceToAggregatedInstanceConverter {

    public AggregatedInstance convert(String namespace, String serviceName, Instance nacosInstance) {
        AggregatedInstance ai = new AggregatedInstance();
        ai.setServiceName(serviceName);
        ai.setIp(nacosInstance.getIp());
        ai.setPort(nacosInstance.getPort());
        ai.setHealthy(nacosInstance.isHealthy());
        ai.setWeight(nacosInstance.getWeight());
        ai.setChosenNamespace(namespace);
        ai.setAvailableNamespaces(new ArrayList<>(List.of(namespace)));

        Map<String, String> md = new HashMap<>();
        if (nacosInstance.getMetadata() != null) {
            md.putAll(nacosInstance.getMetadata());
        }
        md.put("sourceNamespace", namespace);
        ai.setMetadata(md);
        return ai;
    }
}
```

---

## 6.4 策略类

### 6.4.1 `NamespaceFilterPolicy`
职责：
- 处理 fallback namespace 白名单 / 黑名单过滤
- 过滤掉主 namespace
- 输出可参与 fallback 的 namespace 列表

```java
public class NamespaceFilterPolicy {

    public List<String> fallbackNamespaces(MultiNamespaceRegistryConfig config) {
        return config.getSubscribeNamespaces().stream()
            .filter(ns -> !ns.equals(config.getPrimaryNamespace()))
            .filter(ns -> config.getForbiddenFallbackNamespaces() == null
                    || !config.getForbiddenFallbackNamespaces().contains(ns))
            .filter(ns -> config.getAllowedFallbackNamespaces() == null
                    || config.getAllowedFallbackNamespaces().isEmpty()
                    || config.getAllowedFallbackNamespaces().contains(ns))
            .toList();
    }
}
```

### 6.4.2 `InstanceMetadataMatchPolicy`
职责：
- 对实例 metadata 做必需条件校验
- 防止 fallback 到错误环境、错误租户、错误区域

```java
public class InstanceMetadataMatchPolicy {

    public boolean match(AggregatedInstance instance, Map<String, String> requiredMetadata) {
        if (requiredMetadata == null || requiredMetadata.isEmpty()) {
            return true;
        }
        Map<String, String> metadata = instance.getMetadata();
        for (Map.Entry<String, String> entry : requiredMetadata.entrySet()) {
            if (!Objects.equals(metadata.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
```

### 6.4.3 `NamespaceSelectPolicy`
职责：
- 根据 `NamespaceSelectMode` 决定怎么选实例
- 是 Consumer 查询的核心控制器

建议拆出三个子类：

```text
PrimaryOnlyNamespaceSelectPolicy
PrimaryFirstNamespaceSelectPolicy
MergeAllNamespaceSelectPolicy
```

接口：

```java
public interface NamespaceSelectPolicy {
    List<AggregatedInstance> select(String serviceName, String groupName, MultiNamespaceQueryContext context);
}
```

---

## 6.5 查询上下文类

### 6.5.1 `MultiNamespaceQueryContext`
职责：
- 把查询过程中的依赖对象收敛起来
- 减少策略类参数过多问题

```java
public class MultiNamespaceQueryContext {

    private MultiNamespaceRegistryConfig config;
    private NacosNamingServiceHolder namingServiceHolder;
    private NacosInstanceQueryService instanceQueryService;
    private FallbackNamespaceQueryService fallbackNamespaceQueryService;
    private NamespaceFilterPolicy namespaceFilterPolicy;
    private MultiNamespaceInstanceAggregator aggregator;
}
```

---

## 6.6 查询服务类

### 6.6.1 `NacosInstanceQueryService`
职责：
- 对单个 namespace 执行实际查询
- 统一处理 healthyOnly
- 统一异常转换
- 返回的是 `AggregatedInstance`

```java
public class NacosInstanceQueryService {

    private final NacosNamingServiceHolder namingServiceHolder;
    private final NacosInstanceToAggregatedInstanceConverter converter;

    public List<AggregatedInstance> query(String namespace, String serviceName, String groupName, boolean healthyOnly) {
        try {
            NamingService namingService = namingServiceHolder.get(namespace);
            List<Instance> instances = namingService.getAllInstances(serviceName, groupName, healthyOnly);
            return instances.stream()
                .map(i -> converter.convert(namespace, serviceName, i))
                .toList();
        } catch (Exception ex) {
            throw new MultiNamespaceQueryException(namespace, serviceName, ex);
        }
    }
}
```

### 6.6.2 `FallbackNamespaceQueryService`
职责：
- 专门处理 fallback namespace 查询
- 支持串行 / 并行两种模式

```java
public class FallbackNamespaceQueryService {

    public List<AggregatedInstance> queryFirstAvailable(String serviceName, String groupName, MultiNamespaceQueryContext context) {
        // 串行或并行实现
        return List.of();
    }
}
```

### 6.6.3 `NamespaceResult`
职责：
- 封装单个 namespace 的查询结果
- 并行查询时用于承载 Future 返回值

```java
public record NamespaceResult(String namespace, List<AggregatedInstance> instances, Throwable error) {
    public static NamespaceResult success(String namespace, List<AggregatedInstance> instances) {
        return new NamespaceResult(namespace, instances, null);
    }

    public static NamespaceResult failure(String namespace, Throwable error) {
        return new NamespaceResult(namespace, List.of(), error);
    }
}
```

---

## 6.7 聚合与去重类

### 6.7.1 `MultiNamespaceInstanceAggregator`
职责：
- 合并多个 namespace 实例
- 按去重 key 去重
- 优先保留 primary namespace 实例
- 汇总 availableNamespaces

```java
public class MultiNamespaceInstanceAggregator {

    private final InstanceDedupKeyBuilder dedupKeyBuilder;

    public List<AggregatedInstance> aggregate(List<AggregatedInstance> instances, String primaryNamespace) {
        Map<String, AggregatedInstance> map = new LinkedHashMap<>();

        for (AggregatedInstance current : instances) {
            String key = dedupKeyBuilder.build(current);
            AggregatedInstance existing = map.get(key);

            if (existing == null) {
                map.put(key, current);
                continue;
            }

            List<String> merged = new ArrayList<>(existing.getAvailableNamespaces());
            merged.addAll(current.getAvailableNamespaces());
            existing.setAvailableNamespaces(merged.stream().distinct().toList());

            if (primaryNamespace.equals(current.getChosenNamespace())) {
                existing.setChosenNamespace(current.getChosenNamespace());
                existing.setMetadata(current.getMetadata());
                existing.setHealthy(current.isHealthy());
                existing.setWeight(current.getWeight());
            }
        }

        return new ArrayList<>(map.values());
    }
}
```

### 6.7.2 `InstanceSortSupport`
职责：
- 对最终实例做稳定排序
- 主 namespace 优先
- 同 namespace 下可以按权重、ip、端口稳定排序

---

## 6.8 缓存相关类

### 6.8.1 `NamespaceInstanceCache`
职责：
- 缓存每个 namespace 下某服务的原始实例快照

Key：

```text
serviceName + "#" + namespace
```

Value：

```text
List<AggregatedInstance>
```

接口：

```java
public interface NamespaceInstanceCache {
    void put(String serviceName, String namespace, List<AggregatedInstance> instances);
    List<AggregatedInstance> get(String serviceName, String namespace);
    void evict(String serviceName, String namespace);
}
```

### 6.8.2 `InMemoryNamespaceInstanceCache`
职责：
- 默认内存实现
- 使用 `ConcurrentHashMap`

```java
public class InMemoryNamespaceInstanceCache implements NamespaceInstanceCache {

    private final Map<String, List<AggregatedInstance>> cache = new ConcurrentHashMap<>();

    @Override
    public void put(String serviceName, String namespace, List<AggregatedInstance> instances) {
        cache.put(serviceName + "#" + namespace, instances);
    }

    @Override
    public List<AggregatedInstance> get(String serviceName, String namespace) {
        return cache.getOrDefault(serviceName + "#" + namespace, List.of());
    }

    @Override
    public void evict(String serviceName, String namespace) {
        cache.remove(serviceName + "#" + namespace);
    }
}
```

### 6.8.3 `AggregatedInstanceCache`
职责：
- 缓存最终聚合结果

Key：

```text
serviceName + primaryNamespace + mode + subscribeNamespaces
```

接口：

```java
public interface AggregatedInstanceCache {
    void put(String cacheKey, List<AggregatedInstance> instances);
    List<AggregatedInstance> get(String cacheKey);
    void evict(String cacheKey);
}
```

### 6.8.4 `InMemoryAggregatedInstanceCache`
职责：
- 默认聚合缓存内存实现

### 6.8.5 `CacheKeyBuilder`
职责：
- 统一生成聚合缓存 key

---

## 6.9 注册中心核心类

### 6.9.1 `MultiNamespaceNacosRegistryFactory`
职责：
- 解析 URL
- 创建 `MultiNamespaceNacosRegistry`
- 是 Dubbo SPI 的入口类

```java
public class MultiNamespaceNacosRegistryFactory implements RegistryFactory {

    @Override
    public Registry getRegistry(URL url) {
        MultiNamespaceRegistryConfig config = MultiNamespaceRegistryConfigParser.parse(url);
        return new MultiNamespaceNacosRegistry(config);
    }
}
```

### 6.9.2 `MultiNamespaceNacosRegistry`
职责：
- 整个系统最核心的类
- 实现 Dubbo 的 `Registry`
- Provider 注册 / 注销
- Consumer 查询 / 订阅
- 协调所有辅助类

建议成员：

```java
public class MultiNamespaceNacosRegistry implements Registry {

    private final MultiNamespaceRegistryConfig config;
    private final NacosNamingServiceHolder namingServiceHolder;

    private final DubboUrlToNacosInstanceConverter providerConverter;
    private final NacosInstanceQueryService instanceQueryService;
    private final FallbackNamespaceQueryService fallbackNamespaceQueryService;
    private final MultiNamespaceInstanceAggregator aggregator;
    private final NamespaceFilterPolicy namespaceFilterPolicy;
    private final InstanceMetadataMatchPolicy metadataMatchPolicy;

    private final NamespaceInstanceCache namespaceInstanceCache;
    private final AggregatedInstanceCache aggregatedInstanceCache;
    private final MultiNamespaceNotifyDispatcher notifyDispatcher;
}
```

### 这个类必须实现的方法

#### 1. `register(URL url)`
作用：
- Provider 启动时注册实例到多个 namespace

#### 2. `unregister(URL url)`
作用：
- Provider 下线时从多个 namespace 注销实例

#### 3. `subscribe(URL url, NotifyListener listener)`
作用：
- Consumer 订阅服务变化
- 对多个 namespace 分别建立订阅
- 统一聚合后再通知 Dubbo

#### 4. `unsubscribe(URL url, NotifyListener listener)`
作用：
- 清理多个 namespace 的订阅关系

#### 5. `lookup(URL url)`
作用：
- Consumer 主动查询可用实例
- 主 namespace 优先
- fallback
- 聚合、过滤、去重、排序

---

## 7. Provider 侧具体实现方案

## 7.1 register 流程

```text
Provider 启动
   │
   ▼
Dubbo 调用 Registry.register(url)
   │
   ▼
转换 Dubbo URL -> Nacos Instance
   │
   ▼
遍历 registerNamespaces
   │
   ├── ns-a 注册
   ├── ns-b 注册
   └── ns-c 注册
   │
   ▼
汇总成功/失败结果
   │
   ▼
按 registerFailMode 决定是否抛异常
```

## 7.2 `register(URL url)` 伪代码

```java
@Override
public void register(URL url) {
    Instance instance = providerConverter.convert(url);
    String serviceName = buildServiceName(url);
    String groupName = buildGroupName(url);

    List<String> successNamespaces = new ArrayList<>();
    Map<String, Throwable> failedNamespaces = new LinkedHashMap<>();

    for (String namespace : config.getRegisterNamespaces()) {
        try {
            NamingService namingService = namingServiceHolder.get(namespace);
            namingService.registerInstance(serviceName, groupName, instance);
            successNamespaces.add(namespace);
        } catch (Throwable ex) {
            failedNamespaces.put(namespace, ex);
        }
    }

    handleRegisterResult(successNamespaces, failedNamespaces);
}
```

## 7.3 `handleRegisterResult(...)`
职责：
- 主 namespace 失败时立即抛错
- 全量成功模式下任一失败都抛错
- 部分成功模式下仅告警

```java
private void handleRegisterResult(List<String> successNamespaces, Map<String, Throwable> failedNamespaces) {
    String primary = config.getPrimaryNamespace();

    if (failedNamespaces.containsKey(primary)) {
        throw new IllegalStateException("主 namespace 注册失败: " + primary, failedNamespaces.get(primary));
    }

    if (config.getRegisterFailMode() == RegisterFailMode.ALL_SUCCESS && !failedNamespaces.isEmpty()) {
        throw new IllegalStateException("多 namespace 注册未全部成功: " + failedNamespaces.keySet());
    }

    if (!failedNamespaces.isEmpty()) {
        log.warn("部分 namespace 注册失败, success={}, failed={}", successNamespaces, failedNamespaces.keySet());
    }
}
```

## 7.4 unregister 流程与实现

```java
@Override
public void unregister(URL url) {
    String serviceName = buildServiceName(url);
    String groupName = buildGroupName(url);

    String ip = url.getHost();
    int port = url.getPort();

    for (String namespace : config.getRegisterNamespaces()) {
        try {
            NamingService namingService = namingServiceHolder.get(namespace);
            namingService.deregisterInstance(serviceName, groupName, ip, port);
        } catch (Throwable ex) {
            log.error("注销实例失败, namespace={}, serviceName={}", namespace, serviceName, ex);
        }
    }
}
```

---

## 8. Consumer 侧具体实现方案

## 8.1 lookup 核心目标

对 Consumer 而言，查询逻辑必须体现：

1. 主 namespace 优先
2. 主 namespace 有实例时，优先只返回主 namespace
3. 主 namespace 为空时，fallback
4. merge-all 模式允许合并全部 namespace
5. 最终做 metadata 过滤、去重、排序

## 8.2 `lookup(URL url)` 结构

```java
@Override
public List<URL> lookup(URL url) {
    String serviceName = buildServiceName(url);
    String groupName = buildGroupName(url);

    List<AggregatedInstance> finalInstances = doLookup(serviceName, groupName);

    return finalInstances.stream()
        .map(this::toDubboUrl)
        .toList();
}
```

## 8.3 `doLookup(...)` 核心实现

```java
private List<AggregatedInstance> doLookup(String serviceName, String groupName) {
    NamespaceSelectMode mode = config.getNamespaceSelectMode();

    return switch (mode) {
        case PRIMARY_ONLY -> queryPrimaryOnly(serviceName, groupName);
        case PRIMARY_FIRST -> queryPrimaryFirst(serviceName, groupName);
        case MERGE_ALL -> queryMergeAll(serviceName, groupName);
    };
}
```

## 8.4 `queryPrimaryOnly(...)`

```java
private List<AggregatedInstance> queryPrimaryOnly(String serviceName, String groupName) {
    List<AggregatedInstance> primary = instanceQueryService.query(
        config.getPrimaryNamespace(),
        serviceName,
        groupName,
        config.isHealthyOnly()
    );
    return finalizeInstances(primary);
}
```

## 8.5 `queryPrimaryFirst(...)`

```java
private List<AggregatedInstance> queryPrimaryFirst(String serviceName, String groupName) {
    List<AggregatedInstance> primary;
    try {
        primary = instanceQueryService.query(
            config.getPrimaryNamespace(),
            serviceName,
            groupName,
            config.isHealthyOnly()
        );
    } catch (Exception ex) {
        primary = Collections.emptyList();
        log.warn("主 namespace 查询失败, namespace={}, serviceName={}", config.getPrimaryNamespace(), serviceName, ex);
    }

    if (!primary.isEmpty()) {
        return finalizeInstances(primary);
    }

    if (!config.isNamespaceFallback()) {
        return Collections.emptyList();
    }

    List<AggregatedInstance> fallback = fallbackNamespaceQueryService.queryFirstAvailable(
        serviceName, groupName, buildQueryContext()
    );
    return finalizeInstances(fallback);
}
```

## 8.6 `queryMergeAll(...)`

```java
private List<AggregatedInstance> queryMergeAll(String serviceName, String groupName) {
    List<AggregatedInstance> all = new ArrayList<>();

    for (String namespace : config.getSubscribeNamespaces()) {
        try {
            all.addAll(instanceQueryService.query(namespace, serviceName, groupName, config.isHealthyOnly()));
        } catch (Exception ex) {
            log.warn("namespace 查询失败, namespace={}, serviceName={}", namespace, serviceName, ex);
        }
    }

    return finalizeInstances(all);
}
```

## 8.7 `finalizeInstances(...)`
职责：
- metadata 过滤
- 聚合去重
- 排序
- 返回最终实例

```java
private List<AggregatedInstance> finalizeInstances(List<AggregatedInstance> instances) {
    List<AggregatedInstance> filtered = instances.stream()
        .filter(i -> metadataMatchPolicy.match(i, config.getRequiredInstanceMetadata()))
        .toList();

    List<AggregatedInstance> aggregated = aggregator.aggregate(filtered, config.getPrimaryNamespace());

    return aggregated.stream()
        .sorted((a, b) -> {
            int pa = config.getPrimaryNamespace().equals(a.getChosenNamespace()) ? 0 : 1;
            int pb = config.getPrimaryNamespace().equals(b.getChosenNamespace()) ? 0 : 1;
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
            int ipCompare = a.getIp().compareTo(b.getIp());
            if (ipCompare != 0) {
                return ipCompare;
            }
            return Integer.compare(a.getPort(), b.getPort());
        })
        .toList();
}
```

---

## 9. subscribe / unsubscribe 实现方案

## 9.1 为什么必须自己做 subscribe 聚合

因为多 namespace 情况下：

- Dubbo 只知道当前 Registry 在“订阅一个服务”
- 但你内部要对多个 namespace 分别订阅
- 任一 namespace 发生变更后，都要重新聚合出最新地址列表再回调 Dubbo

所以这里不能简单复用单 namespace 逻辑，必须做一个监听桥接层。

## 9.2 需要新增的监听相关类

### 9.2.1 `MultiNamespaceNotifyDispatcher`
职责：
- 管理 Dubbo `NotifyListener`
- 保存“某个服务 + 某个 listener” 对应的 namespace 监听器集合
- namespace 任意变化时，重新组装结果并执行 notify

核心结构建议：

```java
public class MultiNamespaceNotifyDispatcher {

    private final Map<String, Map<NotifyListener, List<NamespaceEventListenerAdapter>>> listenerRegistry = new ConcurrentHashMap<>();

    public void bind(String serviceKey, NotifyListener listener, List<NamespaceEventListenerAdapter> adapters) {
        listenerRegistry.computeIfAbsent(serviceKey, k -> new ConcurrentHashMap<>()).put(listener, adapters);
    }

    public List<NamespaceEventListenerAdapter> get(String serviceKey, NotifyListener listener) {
        return listenerRegistry.getOrDefault(serviceKey, Map.of()).getOrDefault(listener, List.of());
    }

    public void remove(String serviceKey, NotifyListener listener) {
        Map<NotifyListener, List<NamespaceEventListenerAdapter>> map = listenerRegistry.get(serviceKey);
        if (map != null) {
            map.remove(listener);
        }
    }
}
```

### 9.2.2 `NamespaceEventListenerAdapter`
职责：
- 适配 Nacos listener 回调
- 将 namespace 维度的变更写入缓存
- 触发聚合与下游通知

建议字段：

```java
public class NamespaceEventListenerAdapter implements EventListener {

    private final String namespace;
    private final String serviceName;
    private final String groupName;
    private final NamespaceInstanceCache namespaceInstanceCache;
    private final AggregatedInstanceCache aggregatedInstanceCache;
    private final MultiNamespaceNacosRegistry registry;
    private final NotifyListener notifyListener;
}
```

## 9.3 `subscribe(URL url, NotifyListener listener)` 实现思路

```java
@Override
public void subscribe(URL url, NotifyListener listener) {
    String serviceName = buildServiceName(url);
    String groupName = buildGroupName(url);
    String serviceKey = buildServiceKey(serviceName, groupName);

    List<NamespaceEventListenerAdapter> adapters = new ArrayList<>();

    for (String namespace : config.getSubscribeNamespaces()) {
        NamespaceEventListenerAdapter adapter = new NamespaceEventListenerAdapter(
            namespace,
            serviceName,
            groupName,
            namespaceInstanceCache,
            aggregatedInstanceCache,
            this,
            listener
        );

        try {
            NamingService namingService = namingServiceHolder.get(namespace);
            namingService.subscribe(serviceName, groupName, adapter);
            adapters.add(adapter);
        } catch (Exception ex) {
            log.error("订阅 namespace 失败, namespace={}, serviceName={}", namespace, serviceName, ex);
        }
    }

    notifyDispatcher.bind(serviceKey, listener, adapters);

    List<URL> urls = lookup(url);
    listener.notify(urls);
}
```

## 9.4 `unsubscribe(URL url, NotifyListener listener)` 实现思路

```java
@Override
public void unsubscribe(URL url, NotifyListener listener) {
    String serviceName = buildServiceName(url);
    String groupName = buildGroupName(url);
    String serviceKey = buildServiceKey(serviceName, groupName);

    List<NamespaceEventListenerAdapter> adapters = notifyDispatcher.get(serviceKey, listener);

    for (NamespaceEventListenerAdapter adapter : adapters) {
        try {
            NamingService namingService = namingServiceHolder.get(adapter.getNamespace());
            namingService.unsubscribe(serviceName, groupName, adapter);
        } catch (Exception ex) {
            log.warn("取消订阅失败, namespace={}, serviceName={}", adapter.getNamespace(), serviceName, ex);
        }
    }

    notifyDispatcher.remove(serviceKey, listener);
}
```

## 9.5 `NamespaceEventListenerAdapter.onEvent(...)`

这个方法是整个订阅链最关键的一段。

职责：
1. 把当前 namespace 的最新实例转换成 `AggregatedInstance`
2. 更新 namespace 缓存
3. 清理聚合缓存
4. 从缓存重建最终实例列表
5. 转为 Dubbo URL
6. 回调 `NotifyListener.notify(...)`

伪代码：

```java
@Override
public void onEvent(Event event) {
    if (!(event instanceof NamingEvent namingEvent)) {
        return;
    }

    List<AggregatedInstance> current = namingEvent.getInstances().stream()
        .map(i -> registry.convert(namespace, serviceName, i))
        .toList();

    namespaceInstanceCache.put(serviceName, namespace, current);
    registry.evictAggregateCache(serviceName);

    List<AggregatedInstance> merged = registry.rebuildFromCache(serviceName, groupName);
    List<URL> urls = merged.stream().map(registry::toDubboUrl).toList();

    notifyListener.notify(urls);
}
```

---

## 10. 基于缓存重建聚合结果

订阅场景下，不能每次事件都重新主动查所有 namespace，优先从缓存重建更合理。

## 10.1 `rebuildFromCache(...)`
职责：
- 从所有 namespace 缓存中取当前服务实例
- 按模式聚合
- 去重
- metadata 过滤
- 排序

```java
public List<AggregatedInstance> rebuildFromCache(String serviceName, String groupName) {
    List<AggregatedInstance> all = new ArrayList<>();

    for (String namespace : config.getSubscribeNamespaces()) {
        all.addAll(namespaceInstanceCache.get(serviceName, namespace));
    }

    return finalizeInstancesByMode(serviceName, groupName, all);
}
```

## 10.2 `finalizeInstancesByMode(...)`
需要按不同模式处理：

### `PRIMARY_ONLY`
只取主 namespace 缓存

### `PRIMARY_FIRST`
先看主 namespace 缓存是否非空：
- 非空：只用主 namespace 缓存
- 为空：用 fallback namespace 缓存中第一个非空集合，或按优先级合并

### `MERGE_ALL`
合并所有 namespace 缓存

---

## 11. SPI 文件与资源配置

## 11.1 RegistryFactory SPI

文件路径：

```text
META-INF/dubbo/org.apache.dubbo.registry.RegistryFactory
```

内容：

```properties
huanxin-nacos=com.xxx.multins.nacos.registry.MultiNamespaceNacosRegistryFactory
```

## 11.2 如果你要兼容 Dubbo 3 的 ServiceDiscovery

你还可以进一步扩展：

```text
META-INF/dubbo/org.apache.dubbo.registry.client.ServiceDiscoveryFactory
```

示例：

```properties
huanxin-nacos=com.xxx.multins.nacos.registry.MultiNamespaceServiceDiscoveryFactory
```

说明：
- 第一版先只做 `RegistryFactory` 就够了
- 如果你后续大量使用 application-level service discovery，再补 `ServiceDiscovery` 适配

---

## 12. 与 Dubbo URL 的映射处理

你需要统一定义下面几个构建函数。

### 12.1 `buildServiceName(URL url)`
职责：
- 生成 Nacos serviceName
- 最好与现有 Dubbo Nacos 适配规则保持一致

### 12.2 `buildGroupName(URL url)`
职责：
- 读取或推导 group
- 与现网兼容

### 12.3 `buildServiceKey(serviceName, groupName)`
职责：
- 用于缓存 key 和订阅管理

建议：

```java
private String buildServiceKey(String serviceName, String groupName) {
    return groupName + "@@" + serviceName;
}
```

### 12.4 `toDubboUrl(AggregatedInstance instance)`
职责：
- 把聚合实例转回 Dubbo URL
- metadata 写回 parameter

---

## 13. 异常体系设计

建议增加以下异常类：

### 13.1 `MultiNamespaceRegistryException`
顶级基类

### 13.2 `MultiNamespaceConfigException`
配置错误

### 13.3 `MultiNamespaceRegisterException`
注册失败

### 13.4 `MultiNamespaceQueryException`
查询失败

### 13.5 `MultiNamespaceSubscribeException`
订阅失败

这样好处是：
- 日志更清晰
- 调用链更好定位
- 上层可以分类处理

---

## 14. 日志与监控埋点

## 14.1 Provider 注册日志

建议打印：
- serviceName
- registerNamespaces
- successNamespaces
- failedNamespaces
- primaryNamespace

示例：

```text
multi-ns register done, service=order-service, primary=ns-order, success=[ns-order, ns-shared], failed=[]
```

## 14.2 Consumer 查询日志

建议打印：
- serviceName
- mode
- primaryNamespace
- fallback 是否触发
- 命中的 namespace
- 返回实例数

## 14.3 订阅事件日志

建议打印：
- namespace
- serviceName
- 当前实例数
- 聚合后实例数

## 14.4 指标建议

建议做 Micrometer 指标：

- `multins_registry_register_success_total`
- `multins_registry_register_fail_total`
- `multins_registry_lookup_total`
- `multins_registry_fallback_total`
- `multins_registry_namespace_event_total`
- `multins_registry_cache_hit_total`
- `multins_registry_cache_miss_total`

---

## 15. 关键风险与控制方案

## 15.1 跨环境污染
问题：
- fallback 到了错误 namespace，调用到了 test/dev 实例

方案：
- `allowedFallbackNamespaces`
- `forbiddenFallbackNamespaces`
- `requiredInstanceMetadata.env=prod`

## 15.2 跨租户污染
问题：
- 共享 namespace 中存在多个租户的实例

方案：
- `requiredInstanceMetadata.tenant=t1`

## 15.3 同实例重复出现
问题：
- 同一 provider 同时注册到多个 namespace
- merge 后被负载均衡视为多个实例

方案：
- 必须按 `instanceUniqueKey` 去重

## 15.4 查询风暴
问题：
- 多 namespace 多 listener 下频繁查 Nacos

方案：
- namespace 缓存
- 聚合缓存
- 订阅事件走缓存重建而不是实时全查

## 15.5 主 namespace 故障抖动
问题：
- 主 namespace 不稳定时频繁触发 fallback

方案：
- 记录主 namespace 连续失败次数
- 可后续扩展断路器和短暂 fallback 缓存窗口

---

## 16. 启动与生命周期建议

## 16.1 初始化阶段
在 `MultiNamespaceNacosRegistry` 构造时：
1. parse config
2. 校验 config
3. 初始化 NamingServiceHolder
4. 初始化缓存
5. 初始化策略类
6. 初始化 notifyDispatcher

## 16.2 destroy 阶段
应释放：
- Nacos NamingService
- 线程池
- 订阅关系
- 本地缓存

建议实现：

```java
public void destroy() {
    namingServiceHolder.destroy();
}
```

---

## 17. 开发落地顺序建议

强烈建议按下面顺序做，而不是一次把所有逻辑混在一起。

## 第 1 步：配置与 SPI
先完成：
- `MultiNamespaceRegistryConfig`
- `ConfigParser`
- `RegistryFactory SPI`
- `MultiNamespaceNacosRegistryFactory`

## 第 2 步：Provider 多 namespace 注册
先打通：
- `register`
- `unregister`
- `NacosNamingServiceHolder`
- `DubboUrlToNacosInstanceConverter`

这一步完成后，先验证 provider 能否成功注册到多个 namespace。

## 第 3 步：Consumer 主动查询 lookup
实现：
- `NacosInstanceQueryService`
- `FallbackNamespaceQueryService`
- `NamespaceFilterPolicy`
- `Aggregator`
- `lookup`

先让主动查询跑通，不急着做订阅。

## 第 4 步：subscribe / unsubscribe
实现：
- `MultiNamespaceNotifyDispatcher`
- `NamespaceEventListenerAdapter`
- `namespace cache`
- `rebuildFromCache`

## 第 5 步：metadata 过滤与风险控制
补齐：
- `InstanceMetadataMatchPolicy`
- 黑白名单校验
- 健康检查

## 第 6 步：日志、指标、优化
补齐：
- Micrometer 指标
- 缓存命中统计
- fallback 次数统计
- 详细错误日志

---

## 18. 测试方案

## 18.1 单元测试

### 配置解析测试
覆盖：
- 主 namespace 缺失
- subscribeNamespaces 为空
- 白名单黑名单冲突
- 去重是否正确

### 去重测试
场景：
- 相同 ip:port 来自两个 namespace
- primary namespace 优先保留

### metadata 过滤测试
场景：
- env 不匹配被过滤
- tenant 不匹配被过滤

### fallback 策略测试
场景：
- primary 有实例
- primary 为空，fallback 成功
- primary 异常，fallback 成功
- 所有 namespace 都为空

## 18.2 集成测试

建议准备 3 个 Nacos namespace：

- `ns-a`
- `ns-b`
- `ns-shared`

测试用例：

### 用例 1：Provider 注册到多个 namespace
验证：
- `ns-a` 可见
- `ns-shared` 可见

### 用例 2：Consumer 主优先命中
验证：
- 主 namespace 有实例时，不走 fallback

### 用例 3：主 namespace 清空后 fallback
验证：
- Consumer 自动切到 `ns-shared`

### 用例 4：重复实例去重
验证：
- 同一个实例同时存在 `ns-a` 和 `ns-shared`
- Consumer 只感知为一个实例

### 用例 5：订阅变更回调
验证：
- 任一 namespace 实例变化时，Consumer 订阅地址刷新

---

## 19. 第一版不建议做的事情

为了控制复杂度，第一版先不要做这些：

1. 不要同时改 Registry 和 Router 和 LoadBalance
2. 不要一开始就支持动态热更新 namespace 列表
3. 不要一开始就做复杂灰度规则
4. 不要一开始就支持几十个 namespace 并行聚合
5. 不要把 fallback 做成无限制跨 namespace 混合

第一版目标很明确：

- 能注册
- 能查
- 能 fallback
- 能订阅更新
- 能去重
- 能控风险

---

## 20. 推荐的最终代码目录

```text
dubbo-multins-registry
├── dubbo-multins-registry-core
│   └── src/main/java/com/xxx/multins/core
│       ├── config
│       │   ├── MultiNamespaceRegistryConfig.java
│       │   ├── NamespaceSelectMode.java
│       │   └── RegisterFailMode.java
│       ├── model
│       │   ├── AggregatedInstance.java
│       │   └── NamespaceResult.java
│       ├── policy
│       │   ├── NamespaceFilterPolicy.java
│       │   ├── InstanceMetadataMatchPolicy.java
│       │   ├── NamespaceSelectPolicy.java
│       │   ├── PrimaryOnlyNamespaceSelectPolicy.java
│       │   ├── PrimaryFirstNamespaceSelectPolicy.java
│       │   └── MergeAllNamespaceSelectPolicy.java
│       ├── aggregator
│       │   ├── MultiNamespaceInstanceAggregator.java
│       │   └── InstanceDedupKeyBuilder.java
│       ├── cache
│       │   ├── NamespaceInstanceCache.java
│       │   ├── InMemoryNamespaceInstanceCache.java
│       │   ├── AggregatedInstanceCache.java
│       │   ├── InMemoryAggregatedInstanceCache.java
│       │   └── CacheKeyBuilder.java
│       └── exception
│           ├── MultiNamespaceRegistryException.java
│           ├── MultiNamespaceConfigException.java
│           ├── MultiNamespaceRegisterException.java
│           ├── MultiNamespaceQueryException.java
│           └── MultiNamespaceSubscribeException.java
│
├── dubbo-multins-nacos
│   └── src/main/java/com/xxx/multins/nacos
│       ├── registry
│       │   ├── MultiNamespaceNacosRegistryFactory.java
│       │   ├── MultiNamespaceNacosRegistry.java
│       │   └── MultiNamespaceRegistryConfigParser.java
│       ├── naming
│       │   ├── NacosNamingServiceFactory.java
│       │   └── NacosNamingServiceHolder.java
│       ├── converter
│       │   ├── DubboUrlToNacosInstanceConverter.java
│       │   └── NacosInstanceToAggregatedInstanceConverter.java
│       ├── support
│       │   ├── NacosInstanceQueryService.java
│       │   ├── FallbackNamespaceQueryService.java
│       │   ├── MultiNamespaceQueryContext.java
│       │   └── InstanceSortSupport.java
│       └── listener
│           ├── MultiNamespaceNotifyDispatcher.java
│           └── NamespaceEventListenerAdapter.java
│
├── dubbo-multins-spring-boot-starter
│   └── src/main/java/com/xxx/multins/starter
│
└── src/main/resources
    └── META-INF/dubbo
        └── org.apache.dubbo.registry.RegistryFactory
```

---

## 21. 最终结论

这个需求的最佳实现路径是：

1. **不是自定义 Dubbo RPC 协议**
2. **而是自定义 Dubbo Registry 扩展**
3. 在内部维护多个 Nacos `NamingService`
4. Provider 注册时向多个 namespace 多播注册
5. Consumer 查询时按“主优先 + fallback”聚合查询
6. 订阅时对多个 namespace 分别监听，再统一聚合回调
7. 用 metadata 过滤 + 黑白名单 + 去重机制控制风险

一句话概括：

> 这是一个基于 Dubbo Registry SPI 和 Nacos NamingService 的多命名空间注册发现增强层，核心实现是“多客户端、多 namespace、多播注册、聚合发现、主优先降级、统一通知”。

---

## 22. 推荐下一步开发动作

建议你接下来直接按这个顺序干：

1. 先把 `RegistryFactory + Registry + ConfigParser + NamingServiceHolder` 搭起来
2. 先完成 Provider 多 namespace 注册
3. 再完成 Consumer `lookup`
4. 最后做 `subscribe / unsubscribe`
5. 再补缓存和指标

如果你后续还要，我可以继续给你补两份内容：

### 方案 A：直接给你输出“核心类 Java 代码骨架”
包括：
- `MultiNamespaceNacosRegistry`
- `NacosNamingServiceHolder`
- `FallbackNamespaceQueryService`
- `NamespaceEventListenerAdapter`

### 方案 B：直接给你输出“starter 项目初始化代码结构”
包括：
- Maven 多模块
- SPI 文件
- Spring Boot 3 自动装配
- Demo provider / consumer
