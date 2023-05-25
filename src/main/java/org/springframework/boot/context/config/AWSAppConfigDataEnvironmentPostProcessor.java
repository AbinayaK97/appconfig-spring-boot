package org.springframework.boot.context.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.log.LogMessage;

import java.util.Collection;

public class AWSAppConfigDataEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private final DeferredLogFactory logFactory;

    private final ConfigurableBootstrapContext bootstrapContext;

    private final ConfigDataEnvironmentUpdateListener environmentUpdateListener;

    private final Log logger;

    public AWSAppConfigDataEnvironmentPostProcessor(DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext) {
        this(logFactory, bootstrapContext, null);
    }

    public AWSAppConfigDataEnvironmentPostProcessor(DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext, ConfigDataEnvironmentUpdateListener environmentUpdateListener) {
        this.logFactory = logFactory;
        this.logger = logFactory.getLog(getClass());
        this.bootstrapContext = bootstrapContext;
        this.environmentUpdateListener = environmentUpdateListener;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        postProcessEnvironment(environment, application.getResourceLoader(), application.getAdditionalProfiles());
    }

    void postProcessEnvironment(ConfigurableEnvironment environment, ResourceLoader resourceLoader, Collection<String> additionalProfiles) {
        try {
            resourceLoader = (resourceLoader != null) ? resourceLoader : new DefaultResourceLoader();
            getConfigDataEnvironment(environment, resourceLoader, additionalProfiles).processAndApply();
        } catch (UseLegacyConfigProcessingException ex) {
            this.logger.info(LogMessage.format("Switching to legacy config file processing [%s]", ex.getConfigurationProperty()));
        }
    }

    AWSAppConfigDataEnvironment getConfigDataEnvironment(ConfigurableEnvironment environment, ResourceLoader resourceLoader, Collection<String> additionalProfiles) {
        return new AWSAppConfigDataEnvironment(this.logFactory, this.bootstrapContext, environment, resourceLoader, additionalProfiles, this.environmentUpdateListener);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
