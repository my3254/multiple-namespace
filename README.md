# multiple-namespace

## 项目概述

`multiple-namespace` 提供基于 Dubbo 与 Nacos 的多 namespace 注册发现扩展，支持在多个 Nacos namespace 之间进行服务注册、实例查询、降级切换与聚合返回。

项目通过 Dubbo SPI 暴露 `huanxin-nacos://` 注册中心协议，适用于以下场景：

- Provider 同时注册到多个 Nacos namespace
- Consumer 按主 namespace 优先策略查询实例
- 主 namespace 无可用实例时，按配置顺序降级到其他 namespace
- metadata center 与注册中心共用同一协议地址

## 模块说明

项目包含两个独立模块：

- `multiple-namespace-dubbo2`
  - 适用于 `Dubbo 2.7.x`
- `multiple-namespace-dubbo3`
  - 适用于 `Dubbo 3.3.x`

两个模块提供一致的多 namespace 能力，差异仅在于 Dubbo 版本适配层。

## 协议说明

系统通过 Dubbo SPI 注册以下扩展：

- `RegistryFactory`
  - 暴露 `huanxin-nacos://`
- `MetadataReportFactory`
  - 兼容 `huanxin-nacos://` 作为 metadata center 地址
- `Filter`
  - 输出消费端命中的 namespace 日志

使用边界如下：

- `huanxin-nacos://` 负责多 namespace 注册与发现
- `nacos://` 继续使用 Dubbo 原生 Nacos 逻辑
- metadata 固定写入单一 namespace，不执行多 namespace 广播

## 依赖方式

宿主项目根据 Dubbo 主版本选择对应模块。

### Dubbo 2

```xml
<dependency>
    <groupId>com.huanxin</groupId>
    <artifactId>multiple-namespace-dubbo2</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Dubbo 3

```xml
<dependency>
    <groupId>com.huanxin</groupId>
    <artifactId>multiple-namespace-dubbo3</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## 注册中心配置

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

推荐在生产环境中显式配置以下参数：

- `registerNamespaces`
- `subscribeNamespaces`
- `primaryNamespace`
- `namespaceSelectMode`
- `namespaceFallback`
- `registerFailMode`

## 消费端覆盖配置

消费端可单独覆盖查询空间：

```yaml
dubbo:
  consumer:
    parameters:
      namespace: dev,test
      primaryNamespace: dev
```

消费端覆盖规则如下：

- 仅作用于订阅与查询链路
- 不影响 Provider 注册链路
- 不覆盖 `registerNamespaces`
- 不覆盖 `namespaceSelectMode`
- 不覆盖 `healthyOnly`
- 不覆盖 `requiredInstanceMetadata.*`

## 配置规则

支持的主要参数如下：

| 参数 | 说明 |
| --- | --- |
| `namespace` | 注册与订阅共用的 namespace 列表 |
| `registerNamespaces` | Provider 注册使用的 namespace 列表 |
| `subscribeNamespaces` | Consumer 订阅与查询使用的 namespace 列表 |
| `primaryNamespace` | 主 namespace |
| `namespaceSelectMode` | 实例选择模式：`primary-only`、`primary-first`、`merge-all` |
| `namespaceFallback` | 主 namespace 无实例时是否允许降级 |
| `registerFailMode` | 注册失败策略：`all-success`、`partial-success` |
| `queryParallel` | fallback 查询是否并行执行 |
| `queryTimeoutMs` | fallback 并行查询超时时间 |
| `healthyOnly` | 是否仅返回健康实例 |
| `allowedFallbackNamespaces` | 允许参与 fallback 的 namespace 白名单 |
| `forbiddenFallbackNamespaces` | 禁止参与 fallback 的 namespace 黑名单 |
| `requiredInstanceMetadata.*` | 实例 metadata 精确匹配条件 |

默认解析规则如下：

- `registerNamespaces` 未配置时，使用 `namespace`
- `subscribeNamespaces` 未配置时，使用 `namespace`
- `primaryNamespace` 未配置时，优先取 `registerNamespaces` 第一个值，否则取 `subscribeNamespaces` 第一个值
- 若存在订阅空间，则 `primaryNamespace` 必须包含在 `subscribeNamespaces` 中

## 行为说明

### Provider

- 按 `registerNamespaces` 逐个 namespace 执行注册
- 底层注册动作复用 Dubbo 原生 `nacos://`
- 根据 `registerFailMode` 决定失败处理方式

### Consumer

- 支持 `primary-only`、`primary-first`、`merge-all` 三种模式
- `primary-first` 为默认模式
- 主 namespace 无可用实例时，可按配置执行 fallback
- 返回结果经过实例过滤、聚合去重、排序后再交由 Dubbo 使用

### Metadata Center

- 支持使用 `huanxin-nacos://` 作为 metadata center 地址
- metadata 写入单一 namespace
- namespace 选择优先级为：
  - `primaryNamespace`
  - `namespace` 第一个值
  - `registerNamespaces` 第一个值
  - `subscribeNamespaces` 第一个值

## 依赖策略

两个子模块均将以下依赖声明为 `provided`：

- `dubbo`
- `nacos-client`
- `slf4j-api`

该策略用于：

- 避免覆盖宿主项目中的 Dubbo 版本
- 分别适配 Dubbo 2 与 Dubbo 3
- 降低基础依赖冲突风险

## 约束说明

当前方案具备以下约束：

- Provider 注册 group 固定为 `DEFAULT_GROUP`
- 缓存为进程内存缓存，重启后失效
- `queryParallel` 仅作用于 fallback 查询
- metadata 匹配采用精确相等
- 跨 namespace 去重依赖 `instanceUniqueKey`，默认值为 `host:port`

## 本地校验

模块编译校验范围如下：

- `multiple-namespace-dubbo2` 对应 `Dubbo 2.7.8`
- `multiple-namespace-dubbo3` 对应 `Dubbo 3.3.5`
