package com.mcmaster.aws.lambda.oracleRdsRotate;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Properties;

public class AWS {

    private final String CONST_AWS_CURRENT = "AWSCURRENT";

    public AWSSecretsManager getClient() {

        String sRegion = System.getenv("REGION");
        String sEndpoint = String.format("secretsmanager.%s.amazonaws.com", sRegion);
        AwsClientBuilder.EndpointConfiguration config = new AwsClientBuilder.EndpointConfiguration(sEndpoint, sRegion);
        AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard();
        clientBuilder.setEndpointConfiguration(config);
        AWSSecretsManager client = clientBuilder.build();
        return client;

    }

    public Properties getSecret(String secretName) throws JsonProcessingException, ResourceNotFoundException {
        return getSecret(secretName, "", CONST_AWS_CURRENT);
    }

    public Properties getSecret(String secretName, String version, String stage) throws JsonProcessingException, ResourceNotFoundException {

        System.out.println(String.format("Retrieving Secret: '%s'", secretName));
        AWSSecretsManager client = this.getClient();
        GetSecretValueRequest gsv_request = new GetSecretValueRequest().withSecretId(secretName).withVersionStage(stage);
        if(!version.isEmpty()) {
            gsv_request.withVersionId(version);
        }
        GetSecretValueResult gsv_result = null;
        gsv_result = client.getSecretValue(gsv_request);

        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        Properties props = mapper.readValue(gsv_result.getSecretString(), Properties.class);
        return props;

    }

}
