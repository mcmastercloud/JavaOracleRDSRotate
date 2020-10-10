package com.mcmaster.aws.lambda.oracleRdsRotate;

public class RotateHandlerCommand {

    private String SecretId;
    private String ClientRequestToken;
    private String Step;

    public String getSecretId() {
        return SecretId;
    }

    public void setSecretId(String secretId) {
        SecretId = secretId;
    }

    public String getClientRequestToken() {
        return ClientRequestToken;
    }

    public void setClientRequestToken(String clientRequestToken) {
        ClientRequestToken = clientRequestToken;
    }

    public String getStep() {
        return Step;
    }

    public void setStep(String step) {
        Step = step;
    }
}
