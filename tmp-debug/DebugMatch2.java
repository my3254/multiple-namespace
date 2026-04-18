import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.UrlUtils;
import java.util.*;

public class DebugMatch2 {
    public static void main(String[] args) {
        Map<String,String> c = new LinkedHashMap<>();
        c.put("application","aggregate-service");
        c.put("background","false");
        c.put("category","providers,configurators,routers");
        c.put("check","false");
        c.put("dubbo","2.0.2");
        c.put("executor-management-mode","isolation");
        c.put("file-cache","true");
        c.put("interface","com.huanxin.auth.interfaces.api.AuthFacadeApi");
        c.put("metadata-type","local");
        c.put("methods","getUserProfile,login,register");
        c.put("namespace","dev");
        c.put("qos.enable","false");
        c.put("release","3.3.5");
        c.put("revision","0.0.1-SNAPSHOT");
        c.put("side","consumer");
        c.put("sticky","false");
        c.put("timestamp","1776523040946");
        c.put("unloadClusterRelated","false");
        URL consumer = new URL("consumer","172.25.32.1",0,"com.huanxin.auth.interfaces.api.AuthFacadeApi",c);

        Map<String,String> p = new LinkedHashMap<>();
        p.put("side","provider");
        p.put("release","3.3.5");
        p.put("methods","getUserProfile,login,register");
        p.put("deprecated","false");
        p.put("dubbo","2.0.2");
        p.put("interface","com.huanxin.auth.interfaces.api.AuthFacadeApi");
        p.put("service-name-mapping","true");
        p.put("generic","false");
        p.put("path","com.huanxin.auth.interfaces.api.AuthFacadeApi");
        p.put("protocol","dubbo");
        p.put("metadata-type","remote");
        p.put("application","auth-biz");
        p.put("prefer.serialization","hessian2,fastjson2");
        p.put("dynamic","true");
        p.put("category","providers");
        p.put("timestamp","1776523020260");
        URL provider = new URL("dubbo","172.25.32.1",20881,"com.huanxin.auth.interfaces.api.AuthFacadeApi",p);

        System.out.println("consumer.interface=" + consumer.getServiceInterface());
        System.out.println("provider.interface=" + provider.getServiceInterface());
        System.out.println("match=" + UrlUtils.isMatch(consumer, provider));
    }
}
