package com.mcmaster.aws.lambda.oracleRdsRotate;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.*;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class RotateHandler implements RequestHandler<Map<String,String>, String> {

    String secretName = "";
    private final String CONST_AWS_CURRENT = "AWSCURRENT";
    private final String CONST_AWS_PREVIOUS = "AWSPREVIOUS";
    private final String CONST_AWS_PENDING = "AWSPENDING";
    private final String CONST_DB_PASSWORD = "password";
    private final String CONST_DB_USERNAME = "username";
    private final String CONST_DB_NAME = "dbname";
    private final String CONST_DB_HOST = "host";

    @Override
    public String handleRequest(Map<String,String> cmd, Context context) {

        RotateHandlerCommand rotateHandlerCommand = new RotateHandlerCommand();
        rotateHandlerCommand.setClientRequestToken(cmd.get("ClientRequestToken"));
        rotateHandlerCommand.setSecretId(cmd.get("SecretId"));
        rotateHandlerCommand.setStep(cmd.get("Step"));
        secretName = rotateHandlerCommand.getSecretId();

        System.out.println(String.format("Secret id: %s", rotateHandlerCommand.getSecretId()));
        System.out.println(String.format("Step: %s", rotateHandlerCommand.getStep()));
        System.out.println(String.format("Client Request Token: %s", rotateHandlerCommand.getClientRequestToken()));

        AWS aws = new AWS();
        AWSSecretsManager client = aws.getClient();

        DescribeSecretRequest dsr = new DescribeSecretRequest();
        dsr.setSecretId(rotateHandlerCommand.getSecretId());
        DescribeSecretResult dsres = client.describeSecret(dsr);
        String errorMessage;

        if (!dsres.isRotationEnabled()) {
            errorMessage = String.format("Secret %s is not enabled for Rotation", rotateHandlerCommand.getSecretId());
            System.out.println(errorMessage);
            return "ERROR";
        }
        Map<String, List<String>> versionStageData = dsres.getVersionIdsToStages();
        if (!versionStageData.containsKey(rotateHandlerCommand.getClientRequestToken())) {
            errorMessage = String.format("Secret %s, version %s has no stage for rotation of secret", rotateHandlerCommand.getSecretId(), rotateHandlerCommand.getClientRequestToken());
            System.out.println(errorMessage);
            return "ERROR";
        }
        List<String> tokenData = versionStageData.get(rotateHandlerCommand.getClientRequestToken());
        if (tokenData.contains(CONST_AWS_CURRENT)) {
            errorMessage = String.format("Secret %s is already at stage %s", rotateHandlerCommand.getSecretId(), CONST_AWS_CURRENT);
            System.out.println(errorMessage);
            return "ERROR";
        }
        if (!tokenData.contains(CONST_AWS_PENDING)) {
            errorMessage = String.format("Secret %s is not set as AWS Pending for rotation", rotateHandlerCommand.getSecretId());
            System.out.println(errorMessage);
            return "ERROR";
        }
        String step = rotateHandlerCommand.getStep();

        try {
            switch (step) {
                case "createSecret":
                    System.out.println("Going to create secret");
                    createSecret(client, rotateHandlerCommand.getSecretId(), rotateHandlerCommand.getClientRequestToken());
                    break;
                case "setSecret":
                    System.out.println("Going to set secret");
                    setSecret(rotateHandlerCommand.getSecretId(), rotateHandlerCommand.getClientRequestToken());
                    break;
                case "testSecret":
                    System.out.println("Going to test secret");
                    testSecret(rotateHandlerCommand.getSecretId(), rotateHandlerCommand.getClientRequestToken());
                    break;
                case "finishSecret":
                    System.out.println("Going to finish secret");
                    finishSecret(client, rotateHandlerCommand.getSecretId(), rotateHandlerCommand.getClientRequestToken());
                    break;
            }
        } catch (Exception ex) {
            System.out.println(String.format("Critical Exception in Password Rotation Lambda: %s", ex.getMessage()));
            return "ERROR";
        }
        return "OK";
    }

    private void createSecret(AWSSecretsManager client, String arn, String token) throws ResourceNotFoundException, JsonProcessingException {

        AWS aws = new AWS();
        Properties current_props = aws.getSecret(arn);
        try {
            aws.getSecret(arn, token, CONST_AWS_PENDING);
        } catch (ResourceNotFoundException ex) {
            System.out.println(String.format("Secret %s version could not be found, creating new %s version now", CONST_AWS_PENDING, CONST_AWS_PENDING));
            String sSecret = Utilities.randomString(30);

            current_props.put("password", sSecret);
            ObjectMapper om = new ObjectMapper(new JsonFactory());

            PutSecretValueRequest psvr = new PutSecretValueRequest()
                .withSecretId(arn)
                .withClientRequestToken(token)
                .withSecretString(om.writeValueAsString(current_props))
                .withVersionStages(CONST_AWS_PENDING);

            client.putSecretValue(psvr);
            System.out.println(String.format("Successfully Put Secret for ARN %s and Version %s", arn, token));
            System.out.println("Sleeping for 10 seconds to ensure new Password version takes successfully");
            try {
                Thread.sleep(10000);
            } catch(java.lang.InterruptedException wokenup) { /* Non-Critical Error */ }
            System.out.println("I have risen");
        }
    }

    private void setSecret(String arn, String token) throws Exception {

        AWS aws = new AWS();
        Connection conn;
        Properties pending_prop = null;

        try {
            pending_prop = aws.getSecret(secretName, token, CONST_AWS_PENDING);
            conn = Oracle.connect(pending_prop.getProperty(CONST_DB_HOST), pending_prop.getProperty(CONST_DB_NAME), pending_prop.getProperty(CONST_DB_USERNAME), pending_prop.getProperty(CONST_DB_PASSWORD));
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT SYSDATE FROM DUAL");
            conn.close();
            System.out.println(String.format("SetSecret: AWS Pending Secret is already set as password in Oracle DB for Secret Arn %s", arn));
            return;
        } catch (SQLException ex) {
            // Could not connect to the Database, try the Current Password
            System.out.println("The proposed passed cannot connect to Oracle, changing password");
            try {
                Properties prop = aws.getSecret(secretName, "", CONST_AWS_CURRENT);
                conn = Oracle.connect(prop.getProperty(CONST_DB_HOST), prop.getProperty(CONST_DB_NAME), prop.getProperty(CONST_DB_USERNAME), prop.getProperty(CONST_DB_PASSWORD));
                System.out.println(String.format("Connected to Oracle using %s password", CONST_AWS_CURRENT));
            } catch (SQLException ex2) {
                // Cloud not connect to the Database, try the Previous Password
                try {
                    Properties prop = aws.getSecret(secretName, "", CONST_AWS_PREVIOUS);
                    System.out.println(String.format("Connected to Oracle using %s password", CONST_AWS_PREVIOUS));
                    conn = Oracle.connect(prop.getProperty(CONST_DB_HOST), prop.getProperty(CONST_DB_NAME), prop.getProperty(CONST_DB_USERNAME), prop.getProperty(CONST_DB_PASSWORD));
                } catch (SQLException ex3) {
                    String sError = String.format("SetSecret: Unable to log into database with Previous, Current, or Pending Secret of ARN %s", arn);
                    System.out.println(sError);
                    throw new Exception(sError);
                }
            }
        } catch (java.lang.ClassNotFoundException | JsonProcessingException ex) {
            throw ex;
        }

        Statement stmt = conn.createStatement();
        conn.setAutoCommit(true);
        stmt.execute(String.format("ALTER USER %S IDENTIFIED BY \"%s\"", pending_prop.getProperty("username"), pending_prop.getProperty("password")));
        conn.close();
        System.out.println("Successfully Changed Oracle Database Password");

    }

    private Boolean testSecret(String arn, String token) {
        AWS aws = new AWS();

        try {
            Properties prop = aws.getSecret(secretName, token, CONST_AWS_PENDING);
            Connection conn = Oracle.connect(prop.getProperty(CONST_DB_HOST), prop.getProperty(CONST_DB_NAME), prop.getProperty(CONST_DB_USERNAME), prop.getProperty(CONST_DB_PASSWORD));
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT SYSDATE FROM DUAL");
            return true;
        } catch (SQLException ex) {
            System.out.println("Cannot Connect to SQL");
            return false;
        } catch (JsonProcessingException | java.lang.ClassNotFoundException ex) {
            System.out.println("Error connecting to Oracle (e.g. Driver Issue or JSON Deserialisation issue");
            return false;
        }
    }

    private void finishSecret(AWSSecretsManager client, String arn, String token) {

        System.out.println("Confirming current password is correct before completing rotation");
        if(testSecret(arn, token)) {

            DescribeSecretRequest dsr = new DescribeSecretRequest();
            dsr.setSecretId(arn);

            DescribeSecretResult dsres = client.describeSecret(dsr);
            Map<String, List<String>> secretMetadata = dsres.getVersionIdsToStages();

            if (this.isVersionAtStage(secretMetadata, token, CONST_AWS_CURRENT)) {
                System.out.println(String.format("finishSecret: Version %s is already marked as %s for %s", token, CONST_AWS_CURRENT, arn));
                return;
            } else {
                System.out.println(String.format("Secret is not marked as %s, changing secret now", CONST_AWS_CURRENT));
            }

            UpdateSecretVersionStageRequest versReq = new UpdateSecretVersionStageRequest()
                    .withSecretId(arn)
                    .withVersionStage(CONST_AWS_CURRENT)
                    .withMoveToVersionId(token)
                    .withRemoveFromVersionId(this.getVersionForStage(secretMetadata, CONST_AWS_CURRENT));

            client.updateSecretVersionStage(versReq);
            System.out.println(String.format("finishSecret: Successfully Set %s Stage to Version %s for Secret %s", CONST_AWS_CURRENT, token, arn));
        } else {
            System.out.println("Not fixing in new password, as the change of Password did not succeed");
        }
    }

    public String getVersionForStage(Map<String, List<String>> secretMetadata, String stage) {
        System.out.println(String.format("Searching for the VersionId for %s Stage", CONST_AWS_CURRENT));

        for (String version : secretMetadata.keySet()) {
            System.out.println(String.format("Checking Version: %s", version));
            for (String currentStage : secretMetadata.get(version)) {
                System.out.println(String.format("Checking Stage: %s", currentStage));
                if (currentStage.equals(stage)) {
                    System.out.println("Found AWSCURRENT VersionId");
                    return version;
                }
            }
        }
        // Could not find version for Stage
        System.out.println(String.format("Cloud Not find version for Stage %s", stage));
        return "";
    }

    public Boolean isVersionAtStage(Map<String, List<String>> secretMetadata, String version, String stage) {
        for (String currentStage : secretMetadata.get(version)) {
            if (currentStage.equals(stage)) {
                return true;
            }
        }
        return false;
    }
}