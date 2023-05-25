package org.springframework.boot.context.config;

import com.amazonaws.services.appconfigdata.model.GetLatestConfigurationRequest;
import com.amazonaws.services.appconfigdata.model.GetLatestConfigurationResult;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AWSAppConfigDataLoader implements ConfigDataLoader<AWSAppConfigResource> {

  @Override
  public ConfigData load(ConfigDataLoaderContext context, AWSAppConfigResource resource) {
    try {
      GetLatestConfigurationResult configurationResult = getApplicationPropertiesFromAWSAppConfig(resource);

      List<Map<String, Object>> yamlResource = getApplicationPropertiesInYaml(configurationResult);

      List<PropertySource<?>> propertySources = new ArrayList<>(yamlResource.size());
      for (Map<String, Object> property : yamlResource) {
        propertySources.add(new OriginTrackedMapPropertySource(resource.getRequest().toString(), Collections.unmodifiableMap(property), true));
      }
      ConfigData.PropertySourceOptions options = ConfigData.PropertySourceOptions.ALWAYS_NONE;

      return new ConfigData(propertySources, options);

    } catch (Exception e) {
      String errorMessage = e.getMessage() + " for " + resource.getRequest().toString();
      throw new RuntimeException(errorMessage);
    }
  }

  private List<Map<String, Object>> getApplicationPropertiesInYaml(GetLatestConfigurationResult configurationResult) throws Exception {
    ByteArrayResource byteArrayResource = new ByteArrayResource(configurationResult.getConfiguration().array());
    List<Map<String, Object>> yamlResource = new YamlLoader(byteArrayResource).load();

    if (yamlResource.isEmpty()) {
      throw new Exception("AWS AppConfig Property response is empty");
    }
    return yamlResource;
  }

  private GetLatestConfigurationResult getApplicationPropertiesFromAWSAppConfig(AWSAppConfigResource resource) throws Exception, InterruptedException {

    String sessionToken = resource.getAppConfig().startConfigurationSession(resource.getRequest()).getInitialConfigurationToken();
    while (true) {
      GetLatestConfigurationRequest configurationRequest = new GetLatestConfigurationRequest();
      configurationRequest.setConfigurationToken(sessionToken);
      GetLatestConfigurationResult configurationResult = resource.getAppConfig().getLatestConfiguration(configurationRequest);

      if (!Objects.equals("application/x-yaml", configurationResult.getContentType())) {
        throw new Exception("AWS AppConfig Response is expected to be YAML");
      }

      if (configurationResult.getConfiguration().array().length > 0) {
        return configurationResult;
      }

      sessionToken = configurationResult.getNextPollConfigurationToken();
      Thread.sleep(10_000);
    }
  }
}
