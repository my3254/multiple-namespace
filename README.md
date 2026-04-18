# multiple-namespace

`multiple-namespace` 现在已经拆成两个独立模块，分别给 Dubbo 2 和 Dubbo 3 使用。

## 模块说明

- `multiple-namespace-dubbo2`
  适用于宿主项目使用 `Dubbo 2.7.x`
- `multiple-namespace-dubbo3`
  适用于宿主项目使用 `Dubbo 3.3.x`

两个模块都提供同一套多 namespace 注册发现能力：

- Provider 同时注册到多个 Nacos namespace
- Consumer 优先从主 namespace 发现实例
- 主 namespace 没有可用实例时，自动 fallback 到备用 namespace
- 通过 Dubbo SPI 暴露 `multins-nacos://` 自定义注册中心协议

边界说明：

- `multins-nacos://` 负责多空间实例注册和多空间实例消费
- `nacos://` 仍然完全按宿主项目的原生 Dubbo/Nacos 逻辑执行
- 如果宿主项目把 `multins-nacos://` 同时当作 metadata center 使用，元数据会默认收敛到主空间，也就是 `namespace` 的第一个值

## 使用方式

宿主项目按自己的 Dubbo 主版本选择依赖：

```xml
<dependency>
    <groupId>com.huanxin</groupId>
    <artifactId>multiple-namespace-dubbo2</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

或：

```xml
<dependency>
    <groupId>com.huanxin</groupId>
    <artifactId>multiple-namespace-dubbo3</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

然后把注册中心地址改成：

```yaml
dubbo:
  registry:
    address: multins-nacos://127.0.0.1:8848
    username: nacos
    password: nacos
    parameters:
      namespace: dev,test
      namespaceSelectMode: primary-first
      namespaceFallback: true
      healthyOnly: true
```

如果消费端想单独覆盖消费空间，也可以这样配：

```yaml
dubbo:
  consumer:
    parameters:
      namespace: dev,test
```

默认行为：

- `primaryNamespace` 不配置时，优先取 `namespace` 的第一个
- `subscribeNamespaces` 不配置时，默认等于主空间
- `registerNamespaces` 不配置时，默认等于主空间

元数据说明：

- 这个扩展提供 `MetadataReportFactory` SPI，用于兼容 `multins-nacos://` 被同时当作 metadata center 使用的场景
- metadata 不做多空间广播，默认固定写入主空间，也就是 `primaryNamespace` 或 `namespace` 第一个值

## 依赖策略

两个子模块都把 `dubbo`、`nacos-client`、`slf4j-api` 设成了 `provided`。

这样做的目的：

- 不覆盖宿主项目自己的 Dubbo 版本
- Dubbo 2 项目和 Dubbo 3 项目分别引用对应模块
- 避免因为基础包版本冲突导致启动报错

## 本地校验

两个模块都已经分别通过 `javac` 编译校验：

- `multiple-namespace-dubbo2` 对 `Dubbo 2.7.8`
- `multiple-namespace-dubbo3` 对 `Dubbo 3.3.5`
