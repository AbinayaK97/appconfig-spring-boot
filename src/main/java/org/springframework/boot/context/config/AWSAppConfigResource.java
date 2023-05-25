package org.springframework.boot.context.config;


import com.amazonaws.services.appconfigdata.AWSAppConfigDataClient;
import com.amazonaws.services.appconfigdata.model.StartConfigurationSessionRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AWSAppConfigResource extends ConfigDataResource {

    private AWSAppConfigDataClient appConfig;
    private StartConfigurationSessionRequest request;

    public AWSAppConfigResource(StartConfigurationSessionRequest configurationRequest) {
        this.appConfig = (AWSAppConfigDataClient) AWSAppConfigDataClient.builder().build();
        this.request = configurationRequest;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AWSAppConfigResource other = (AWSAppConfigResource) obj;
        return this.request.equals(other.request);
    }

    @Override
    public int hashCode() {
        return this.request.hashCode();
    }
}