package ls.sample;

import com.google.inject.Injector;
import com.netflix.config.ConfigurationManager;
import com.netflix.governator.InjectorBuilder;
import com.netflix.zuul.netty.server.BaseServerStartup;
import com.netflix.zuul.netty.server.Server;
import org.apache.log4j.Logger;

/**
 * Created by leishu on 18-10-10.
 */
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
