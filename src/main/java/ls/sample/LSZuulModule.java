package ls.sample;

import com.google.inject.Scopes;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.zuul.sample.ZuulSampleModule;

/**
 * Created by leishu on 18-10-10.
 */
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
