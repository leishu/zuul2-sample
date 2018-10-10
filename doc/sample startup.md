@[TOC](Zuul2.1 sample程序启动篇)

# 问题
不使用AWS环境，Zuul2.1的sample程序是无法启动的。报错如下：
```log
WARN  com.netflix.discovery.internal.util.Archaius1Utils [main] Cannot find the properties specified : eureka-client. This may be okay if there are other environment specific properties or the configuration is installed with a different mechanism.
WARN  com.netflix.discovery.internal.util.Archaius1Utils [main] Cannot find the properties specified : eureka-client. This may be okay if there are other environment specific properties or the configuration is installed with a different mechanism.
WARN  com.netflix.appinfo.AmazonInfo$Builder [main] Skipping the rest of AmazonInfo init as we were not able to load instanceId after the configured number of retries: 3, per fail fast configuration: true
INFO  com.netflix.appinfo.RefreshableAmazonInfoProvider [main] Datacenter is: Amazon
```
分析一下源码，eureka支持三种数据中心：Netflix、Amazon和MyOwn。
BaseServerStartup类使用google inject实现依赖注入。
BaseServerStartup依赖ServerStatusManager和ApplicationInfoManager。
```java
    @Inject
    public ServerStatusManager(ApplicationInfoManager applicationInfoManager, DiscoveryClient discoveryClient) {
    }

    @Inject
    public ApplicationInfoManager(EurekaInstanceConfig config, InstanceInfo instanceInfo, OptionalArgs optionalArgs) {
    }
```
ApplicationInfoManager依赖EurekaInstanceConfig接口的实现。
```java
@ImplementedBy(CloudInstanceConfig.class)
public interface EurekaInstanceConfig { 

}

@Singleton
@ProvidedBy(CloudInstanceConfigProvider.class)
public class CloudInstanceConfig extends PropertiesInstanceConfig implements RefreshableInstanceConfig {
    public CloudInstanceConfig(String namespace) {
        this(namespace, new Archaius1AmazonInfoConfig(namespace), null, true);
    }    
}

public class RefreshableAmazonInfoProvider implements Provider<AmazonInfo> {
    
    private static AmazonInfo init(AmazonInfoConfig amazonInfoConfig, FallbackAddressProvider fallbackAddressProvider) {
        AmazonInfo info;
        try {
            info = AmazonInfo.Builder
                    .newBuilder()
                    .withAmazonInfoConfig(amazonInfoConfig)
                    .autoBuild(amazonInfoConfig.getNamespace());
            logger.info("Datacenter is: {}", DataCenterInfo.Name.Amazon);
        } catch (Throwable e) {
            logger.error("Cannot initialize amazon info :", e);
            throw new RuntimeException(e);
        }
        ......
    }
}
```
所以，官方的sample程序，必须使用AWS环境。

没有AWS环境怎么办呢？

# UML
![Alt](https://img-blog.csdn.net/20181010145332616?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80MzM2NDE3Mg==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

# 解决
```java
public class LSBootStrap {
    private static Logger logger = Logger.getLogger(LSBootStrap.class);

    public static void main(String[] args) {
        new LSBootStrap().start();
    }

    public void start() {
        logger.info("Leishu Zuul Sample: starting up.");
        long startTime = System.currentTimeMillis();
        int exitCode = 0;

        Server server = null;

        try {
            ConfigurationManager.loadCascadedPropertiesFromResources("leishu");
            Injector injector = InjectorBuilder.fromModule(new LSZuulModule()).createInjector();

            BaseServerStartup serverStartup = injector.getInstance(BaseServerStartup.class);
            server = serverStartup.server();

            long startupDuration = System.currentTimeMillis() - startTime;
            logger.info("Zuul Sample: finished startup. Duration = " + startupDuration + " ms");

            server.start(true);
        } catch (Throwable t) {
            logger.error("###############");
            logger.error("Zuul Sample: initialization failed. Forcing shutdown now.", t);
            logger.error("###############");
            exitCode = 1;
        } finally {
            // server shutdown
            if (server != null) server.stop();

            System.exit(exitCode);
        }
    }
}

public class LSZuulModule extends ZuulSampleModule {
    @Override
    protected void configure() {
        //DataCenterInfo
        bind(EurekaInstanceConfig.class)
                .toProvider(MyDataCenterInstanceConfigProvider.class)
                .in(Scopes.SINGLETON);

        super.configure();
    }
}
```
```properties
eureka.registration.enabled = false

## configuration related to reaching the eureka servers
eureka.preferSameZone = true
eureka.shouldUseDns = false


eureka.serviceUrl.default = http://localhost:8761/eureka


# Loading Filters
zuul.filters.root = src/main/groovy/com/netflix/zuul/sample/filters
zuul.filters.locations = ${zuul.filters.root}/inbound,${zuul.filters.root}/outbound,${zuul.filters.root}/endpoint
zuul.filters.packages = com.netflix.zuul.filters.common

api.ribbon.NIWSServerListClassName = com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList
api.ribbon.DeploymentContextBasedVipAddresses = 192.168.100.232:7001
```
这样，就可以使用MyOwn类型的数据中心了。
此时，注入ApplicationInfoManager的时候，config是null，instanceInfo就是MyDataCenterInstanceConfig的实例了。
看日志，启动正常。
```log
INFO  com.netflix.discovery.DiscoveryClient [main] Getting all instance registry info from the eureka server
INFO  com.netflix.discovery.DiscoveryClient [main] The response status is 200

WARN  com.netflix.zuul.netty.server.BaseServerStartup [main] Configured port: 7001
INFO  ls.sample.LSBootStrap [main] Zuul Sample: finished startup. Duration = 3475 ms
WARN  com.netflix.zuul.netty.server.Server [main] Proxy listening with TCP transport using NIO
INFO  com.netflix.zuul.netty.server.Server [main] Binding to port: 7001
```