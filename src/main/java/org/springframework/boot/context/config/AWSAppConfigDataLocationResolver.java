package org.springframework.boot.context.config;

import com.amazonaws.services.appconfigdata.model.StartConfigurationSessionRequest;
import org.springframework.core.Ordered;

import java.util.Collections;
import java.util.List;

public class AWSAppConfigDataLocationResolver implements ConfigDataLocationResolver<AWSAppConfigResource>, Ordered {

    private static final String AWS_APP_CONFIG_LOCATION = "aws-app-config";

    @Override
    public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return location.getValue().equals(AWS_APP_CONFIG_LOCATION);
    }

    @Override
    public List<AWSAppConfigResource> resolve(ConfigDataLocationResolverContext context, ConfigDataLocation location) throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
        return Collections.emptyList();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public List<AWSAppConfigResource> resolveProfileSpecific(ConfigDataLocationResolverContext context, ConfigDataLocation location, Profiles profiles) {
        StartConfigurationSessionRequest configurationRequest = new StartConfigurationSessionRequest();
        configurationRequest.withApplicationIdentifier(System.getenv("AWS_APP_CONFIG_APPLICATION"));
        configurationRequest.withConfigurationProfileIdentifier("application-" + profiles.getActive().get(0));
        configurationRequest.withEnvironmentIdentifier(profiles.getActive().get(0));
        AWSAppConfigResource awsAppConfigResource = new AWSAppConfigResource(configurationRequest);
        return Collections.singletonList(awsAppConfigResource);
    }
}
