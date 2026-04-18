package com.huanxin.multiple.nacos.filter;

import com.huanxin.multiple.nacos.support.InvocationNamespaceTracker;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Activate(group = "consumer", order = -10000)
public class MultiNamespaceConsumerLogFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(MultiNamespaceConsumerLogFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        URL url = invoker.getUrl();
        String namespace = InvocationNamespaceTracker.lookup(url);
        if (namespace == null || namespace.trim().isEmpty()) {
            return invoker.invoke(invocation);
        }
        LOG.info("消费端本次调用命中实例，namespace={}, service={}, method={}, address={}:{}",
            namespace,
            resolveServiceName(url),
            invocation.getMethodName(),
            url.getHost(),
            url.getPort());
        return invoker.invoke(invocation);
    }

    private String resolveServiceName(URL url) {
        if (url.getServiceInterface() != null && !url.getServiceInterface().trim().isEmpty()) {
            return url.getServiceInterface();
        }
        return url.getPath();
    }
}
