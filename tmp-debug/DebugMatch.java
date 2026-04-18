import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.url.component.DubboServiceAddressURL;
import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.common.utils.UrlUtils;
import java.util.*;

public class DebugMatch {
    public static void main(String[] args) {
        URL consumer = URL.valueOf("consumer://172.25.32.1/com.huanxin.auth.interfaces.api.AuthFacadeApi?application=aggregate-service&background=false&category=providers,configurators,routers&check=false&dubbo=2.0.2&executor-management-mode=isolation&file-cache=true&interface=com.huanxin.auth.interfaces.api.AuthFacadeApi&metadata-type=local&methods=getUserProfile,login,register&namespace=dev&pid=35400&qos.enable=false&release=3.3.5&revision=0.0.1-SNAPSHOT&side=consumer&sticky=false&timestamp=1776523040946&unloadClusterRelated=false");
        Map<String,String> metadata = new LinkedHashMap<>();
        metadata.put("side","provider");
        metadata.put("release","3.3.5");
        metadata.put("methods","getUserProfile,login,register");
        metadata.put("deprecated","false");
        metadata.put("dubbo","2.0.2");
        metadata.put("interface","com.huanxin.auth.interfaces.api.AuthFacadeApi");
        metadata.put("service-name-mapping","true");
        metadata.put("generic","false");
        metadata.put("path","com.huanxin.auth.interfaces.api.AuthFacadeApi");
        metadata.put("protocol","dubbo");
        metadata.put("metadata-type","remote");
        metadata.put("application","auth-biz");
        metadata.put("prefer.serialization","hessian2,fastjson2");
        metadata.put("dynamic","true");
        metadata.put("category","providers");
        metadata.put("timestamp","1776523020260");

        URL serviceConfig = new ServiceConfigURL("dubbo", "172.25.32.1", 20881, metadata.get("path"), metadata);
        URL provider = new DubboServiceAddressURL(serviceConfig.getUrlAddress(), serviceConfig.getUrlParam(), consumer, null);

        System.out.println("consumer=" + consumer);
        System.out.println("provider=" + provider);
        System.out.println("consumer.interface=" + consumer.getServiceInterface());
        System.out.println("provider.interface=" + provider.getServiceInterface());
        System.out.println("consumer.category=" + consumer.getParameter("category"));
        System.out.println("provider.category=" + provider.getParameter("category"));
        System.out.println("match=" + UrlUtils.isMatch(consumer, provider));
    }
}
