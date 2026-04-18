# Dubbo + Nacos 多 Namespace 方案说明

## 1. 文档范围

本文档说明 Dubbo 与 Nacos 多 namespace 注册发现方案的模块划分、协议暴露方式、配置模型、注册与订阅流程、实例选择规则、缓存机制及使用约束。

方案目标如下：

- Provider 支持同时注册到多个 Nacos namespace
- Consumer 支持在多个 namespace 之间按策略查询实例
- 支持主 namespace 独占、主 namespace 优先、全量合并三种实例选择模式
- 支持 metadata center 与注册中心共用 `huanxin-nacos://`

方案不包含以下能力：

- 独立监控指标上报
- 持久化缓存
- 自定义负载均衡器
- 自定义 group 路由规则
- 脱离 Dubbo/Nacos SPI 体系的额外控制面

## 2. 模块结构

根工程拆分为两个独立模块，逻辑保持一致，仅适配的 Dubbo 大版本不同：

- `multiple-namespace-dubbo2`
  - 适配 `Dubbo 2.7.8`
- `multiple-namespace-dubbo3`
  - 适配 `Dubbo 3.3.5`

两个模块均将以下依赖声明为 `provided`：

- `org.apache.dubbo:dubbo`
- `com.alibaba.nacos:nacos-client`
- `org.slf4j:slf4j-api`

该依赖策略用于保持宿主项目对 Dubbo 与 Nacos 版本的自主控制。

## 3. 协议与 SPI 暴露

方案通过 Dubbo SPI 暴露新的注册中心协议：

- `huanxin-nacos://`

SPI 扩展点如下：

- `RegistryFactory`
  - `huanxin-nacos=com.huanxin.multiple.nacos.registry.MultiNamespaceNacosRegistryFactory`
- `MetadataReportFactory`
  - `huanxin-nacos=com.huanxin.multiple.nacos.metadata.MultiNamespaceMetadataReportFactory`
- `Filter`
  - `multins-consumer-log=com.huanxin.multiple.nacos.filter.MultiNamespaceConsumerLogFilter`

边界说明如下：

- `huanxin-nacos://` 负责多 namespace 注册与发现
- `nacos://` 继续由 Dubbo 原生 Nacos 扩展处理
- Provider 注册阶段内部复用 Dubbo 原生 `nacos` 注册中心

## 4. 核心组件

以下组件在两个模块中保持同构设计：

- `MultiNamespaceNacosRegistry`
  - 多 namespace 注册中心主入口，负责注册、订阅、查询、缓存与通知
- `MultiNamespaceRegistryConfigParser`
  - 负责从 Dubbo URL 解析多 namespace 配置
- `NacosNamingServiceHolder`
  - 负责按 namespace 管理独立 `NamingService`
- `NacosInstanceQueryService`
  - 负责按 namespace 查询实例并执行实例可选性过滤
- `FallbackNamespaceQueryService`
  - 负责主 namespace 不可用时的降级查询
- `MultiNamespaceInstanceAggregator`
  - 负责跨 namespace 实例聚合与去重
- `NamespaceEventListenerAdapter`
  - 负责消费 Nacos 订阅事件并刷新缓存、触发通知
- `MultiNamespaceMetadataReportFactory`
  - 负责 metadata center 的 namespace 归一化
- `InvocationNamespaceTracker`
  - 负责记录消费请求命中的 namespace，供日志过滤器输出

## 5. 配置说明

### 5.1 注册中心示例

```yaml
dubbo:
  registry:
    address: huanxin-nacos://127.0.0.1:8848
    username: nacos
    password: nacos
    parameters:
      namespace: dev,test
      namespaceSelectMode: primary-first
      namespaceFallback: true
      healthyOnly: true
```

### 5.2 消费端覆盖示例

消费端可通过消费者参数覆盖查询空间：

```yaml
dubbo:
  consumer:
    parameters:
      namespace: dev,test
      primaryNamespace: dev
```

说明如下：

- 消费端覆盖仅影响订阅与查询链路
- Provider 注册仍使用注册中心 URL 中的 `registerNamespaces`

### 5.3 配置项清单

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `namespace` | 注册与订阅共用的 namespace 列表，逗号分隔 | 空 |
| `registerNamespaces` | Provider 注册使用的 namespace 列表 | 为空时回落到 `namespace` |
| `subscribeNamespaces` | Consumer 订阅与查询使用的 namespace 列表 | 为空时回落到 `namespace` |
| `primaryNamespace` | 主 namespace | 未指定时优先取 `registerNamespaces` 第一个，否则取 `subscribeNamespaces` 第一个 |
| `namespaceSelectMode` | 实例选择模式：`primary-only` / `primary-first` / `merge-all` | `primary-first` |
| `namespaceFallback` | 主 namespace 无实例时是否允许降级 | `true` |
| `registerFailMode` | 注册失败策略：`all-success` / `partial-success` | `partial-success` |
| `queryParallel` | 降级查询时是否并行查询候选 fallback namespace | `true` |
| `queryTimeoutMs` | 并行降级查询等待超时时间，单位毫秒 | `1500` |
| `healthyOnly` | 是否只返回健康实例 | `true` |
| `allowedFallbackNamespaces` | 允许参与降级的 namespace 白名单 | 空 |
| `forbiddenFallbackNamespaces` | 禁止参与降级的 namespace 黑名单 | 空 |
| `requiredInstanceMetadata.*` | 实例元数据过滤条件，支持多个键值对 | 空 |

`requiredInstanceMetadata.*` 示例：

```yaml
dubbo:
  registry:
    parameters:
      namespace: dev,test
      requiredInstanceMetadata.env: prod
      requiredInstanceMetadata.zone: shanghai
```

### 5.4 配置解析规则

配置解析由 `MultiNamespaceRegistryConfigParser` 完成，规则如下：

1. `registerNamespaces` 为空时，使用 `namespace`
2. `subscribeNamespaces` 为空时，使用 `namespace`
3. `primaryNamespace` 为空时：
   - 优先取 `registerNamespaces` 第一个值
   - 若不存在，则取 `subscribeNamespaces` 第一个值
4. 当三者均未配置时：
   - 允许注册中心 URL 不显式指定 namespace
   - 可由消费端 `consumer.parameters.namespace` 在查询链路中补充
5. 若最终存在订阅空间，则 `primaryNamespace` 必须包含在 `subscribeNamespaces` 中
6. `allowedFallbackNamespaces` 与 `forbiddenFallbackNamespaces` 不允许冲突

## 6. Provider 注册逻辑

Provider 注册由 `MultiNamespaceNacosRegistry#doRegister` 处理。

处理流程如下：

1. 根据当前 Dubbo URL 构建服务名
2. 将 Dubbo URL 转换为 Nacos `Instance`
3. 遍历 `registerNamespaces`
4. 为每个 namespace 构造内部原生 `nacos` 注册中心 URL
5. 调用 Dubbo 原生 `nacos` 注册中心执行注册

该机制的含义如下：

- 外部协议为 `huanxin-nacos://`
- 内部注册动作按 namespace 复用 Dubbo 原生 `nacos://`

### 6.1 注册失败策略

`registerFailMode` 支持两种模式：

- `partial-success`
  - 允许部分 namespace 注册成功而不抛出异常
- `all-success`
  - 任一 namespace 注册失败即抛出 `MultiNamespaceRegisterException`

### 6.2 实例 metadata 写入规则

`DubboUrlToNacosInstanceConverter` 在原始 URL 参数基础上补充以下 metadata：

- `category`
- `protocol`
- `path`
- `instanceUniqueKey`
- `multiNsRegistered=true`
- `dubboApplication`
- `dubbo.url`
- `dubbo.protocol`

其中：

- `instanceUniqueKey` 默认值为 `host:port`
- `dubbo.url` 用于恢复原始 Dubbo URL 信息

## 7. Consumer 查询与订阅逻辑

Consumer 相关流程由 `MultiNamespaceNacosRegistry#doSubscribe`、`lookup` 及 `NamespaceEventListenerAdapter` 协同完成。

### 7.1 生效配置

查询前会生成消费端生效配置：

- 当消费端未显式传入 `namespace` 时，直接使用注册中心解析结果
- 当消费端显式传入 `namespace` 时：
  - 仅覆盖 `subscribeNamespaces`
  - 若未显式传入 `primaryNamespace`，则默认取消费端 namespace 列表第一个值
  - 若显式传入 `primaryNamespace`，则使用该值

以下配置项不支持由消费端覆盖：

- `registerNamespaces`
- `requiredInstanceMetadata`
- `healthyOnly`
- `namespaceSelectMode`
- `namespaceFallback`

### 7.2 订阅流程

订阅流程如下：

1. 根据生效配置预热每个 `subscribeNamespaces` 的 namespace 缓存
2. 为每个订阅 namespace 注册一个 `NamespaceEventListenerAdapter`
3. 绑定 `NotifyListener` 与 namespace listener 映射关系
4. 立即执行一次 `lookup`
5. 将查询结果通过 Dubbo `notify` 下发给消费端

### 7.3 查询流程

查询主流程如下：

1. 优先查询聚合缓存
2. 缓存未命中时，根据 `namespaceSelectMode` 选择查询方式
3. 对查询结果统一执行 metadata 过滤
4. 按规则完成聚合、排序并写回缓存
5. 将结果转换为 Dubbo URL 列表返回

### 7.4 实例过滤规则

`NacosInstanceQueryService` 与订阅事件处理均执行以下过滤：

- 过滤 `enabled=false` 的实例
- 当 `healthyOnly=true` 时，过滤 `healthy=false` 的实例
- 在此基础上按 `requiredInstanceMetadata.*` 执行 metadata 精确匹配

## 8. Namespace 选择模式

### 8.1 `primary-only`

处理规则如下：

1. 查询 `primaryNamespace`
2. 过滤 metadata
3. 聚合后返回

该模式不访问其他 fallback namespace。

### 8.2 `primary-first`

该模式为默认模式，处理规则如下：

1. 优先查询主 namespace
2. 若主 namespace 过滤后仍有实例：
   - 仅保留主 namespace 实例
   - 不再查询 fallback namespace
3. 若主 namespace 无实例且 `namespaceFallback=true`：
   - 从 fallback namespace 中选择第一个存在可用实例的 namespace
   - 返回该 namespace 的实例集合
4. 若 `namespaceFallback=false`：
   - 主 namespace 无实例时直接返回空结果

“第一个”由 `subscribeNamespaces` 的配置顺序决定，不受并行任务完成顺序影响。

### 8.3 `merge-all`

处理规则如下：

1. 查询全部 `subscribeNamespaces`
2. 合并查询结果
3. 执行去重聚合
4. 排序后返回

## 9. Fallback 规则

降级查询由 `FallbackNamespaceQueryService` 处理。

fallback namespace 生成规则如下：

1. 从 `subscribeNamespaces` 中排除 `primaryNamespace`
2. 若配置了 `forbiddenFallbackNamespaces`，则从候选集剔除
3. 若配置了 `allowedFallbackNamespaces`，则仅保留白名单内 namespace

### 9.1 顺序查询

当 `queryParallel=false` 时：

- 按 fallback namespace 配置顺序逐个查询
- 命中第一个满足条件的 namespace 后立即返回

### 9.2 并行查询

当 `queryParallel=true` 时：

- 并发查询全部 fallback namespace
- 每个查询共享同一个总超时时间窗口 `queryTimeoutMs`
- 最终仍按 fallback namespace 配置顺序选择第一个命中的 namespace

并行模式优化查询耗时，不改变 namespace 优先级顺序。

## 10. 聚合与排序规则

### 10.1 去重规则

`MultiNamespaceInstanceAggregator` 使用 `InstanceDedupKeyBuilder` 生成去重键：

1. 优先使用实例 metadata 中的 `instanceUniqueKey`
2. 若不存在，则回退为 `serviceName#ip:port`

### 10.2 聚合规则

聚合结果保留以下信息：

- `availableNamespaces`
  - 表示同一实例在哪些 namespace 中存在
- `chosenNamespace`
  - 表示该实例最终采用哪个 namespace 作为返回代表

当聚合过程中遇到主 namespace 的同地址实例时，主 namespace 实例信息覆盖以下字段：

- `chosenNamespace`
- `healthy`
- `weight`
- `metadata`

该规则用于保证跨 namespace 去重后，主 namespace 的 metadata 优先级更高。

### 10.3 排序规则

`InstanceSortSupport` 的排序规则如下：

1. 主 namespace 实例优先
2. `weight` 降序
3. IP 升序
4. Port 升序

## 11. 缓存与通知机制

方案包含两级本地内存缓存：

- `NamespaceInstanceCache`
  - 存储每个服务在每个 namespace 下的最新实例列表
- `AggregatedInstanceCache`
  - 存储最终聚合后的实例列表

聚合缓存 key 由以下信息组成：

- `groupName`
- `serviceName`
- `primaryNamespace`
- `namespaceSelectMode`
- `subscribeNamespaces`

### 11.1 事件刷新流程

Nacos 推送事件后的处理流程如下：

1. `NamespaceEventListenerAdapter` 接收 `NamingEvent`
2. 更新对应 namespace 的实例缓存
3. 删除该服务的聚合缓存
4. 调用 `MultiNamespaceNacosRegistry#notifyFromCache`
5. 根据 namespace 缓存重新构建聚合结果
6. 通过 Dubbo `notify` 回推给消费端

### 11.2 空结果通知

当查询结果为空时，注册中心不直接返回空列表，而是构造：

- `empty://...`

该行为符合 Dubbo 对“服务已订阅但当前无 provider”的通知约定。

## 12. Metadata Center 处理逻辑

`MultiNamespaceMetadataReportFactory` 用于兼容以下场景：

- 注册中心地址使用 `huanxin-nacos://`
- metadata center 复用同一地址

处理规则如下：

1. 将协议统一改写为原生 `nacos`
2. 选取一个固定 namespace 作为 metadata 写入空间

namespace 选择优先级如下：

1. `primaryNamespace`
2. `namespace` 第一个值
3. `registerNamespaces` 第一个值
4. `subscribeNamespaces` 第一个值

因此 metadata 不进行多 namespace 广播，而是固定写入单一 namespace。

## 13. 消费命中 Namespace 追踪

`InvocationNamespaceTracker` 维护以下信息：

- 服务标识
- Provider 地址
- 命中的 namespace

用途如下：

- 在 Dubbo 最终选中某个 Provider 地址时，可根据本地映射反查其所属 namespace
- `MultiNamespaceConsumerLogFilter` 负责输出消费端命中的 namespace、服务名、方法名与地址

该机制仅用于日志观测，不参与路由决策。

## 14. 约束与限制

方案约束如下：

1. Provider 注册 group 固定为 `DEFAULT_GROUP`
2. 不包含额外的限流、权重改写或自定义负载均衡策略
3. 不包含监控指标埋点输出
4. 缓存仅保存在进程内存中，重启后失效
5. `queryParallel` 仅作用于 fallback 查询，不作用于 `merge-all`
6. 消费端仅支持通过 `namespace` 与 `primaryNamespace` 覆盖查询空间
7. metadata 匹配采用精确相等，不支持模糊匹配与表达式
8. 同一实例跨 namespace 去重依赖 `instanceUniqueKey`，默认值为 `host:port`

## 15. 推荐配置方式

### 15.1 Provider

推荐显式配置：

```yaml
dubbo:
  registry:
    address: huanxin-nacos://127.0.0.1:8848
    username: nacos
    password: nacos
    parameters:
      registerNamespaces: dev,test
      subscribeNamespaces: dev,test
      primaryNamespace: dev
      namespaceSelectMode: primary-first
      namespaceFallback: true
      registerFailMode: partial-success
      healthyOnly: true
```

### 15.2 Consumer

当消费端仅需访问部分 namespace 时，可单独覆盖：

```yaml
dubbo:
  consumer:
    parameters:
      namespace: test,gray
      primaryNamespace: test
```

## 16. 方案摘要

本方案通过 `huanxin-nacos://` 协议对外提供多 namespace 注册发现能力，Provider 侧采用多 namespace 复用原生 Nacos 注册方式，Consumer 侧采用按策略查询、聚合与通知的方式组织实例结果，metadata center 固定收敛到单一 namespace，并通过本地缓存与订阅事件维持 Dubbo notify 结果。
