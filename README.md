# Introduction
This is a Java/Maven project for creating a Lambda function that can be used to Rotate Oracle Database Passwords.

It should be noted that the AWS Console supports the automatic provisioning of a Lambda, however, most people will deploy infrastructure using automation.  The .JAR file generated by this project can be deployed to Lambda to allow the automatic rotation of Oracle Passwords.

The .jar file is fully self-contained, and will not require the use of any additional Lambda Layers.  The Lambda was written in Java since the Python alternative requires the inclusion of a full Oracle Client.  It is also more suitable for organisations who already extensively use Java.

The Lambda will change the Oracle Password to a Random, 30 Character Password.

<strong>Note:</strong> If you do not want to download the full Project and "Package" the .jar yourself, a ready-made .jar file can be found in the /target folder of this project.

# Using the Lambda
I have used the following Lambda Configuration:

1. Runtime: [Java 11 (Corretto)](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/what-is-corretto-11.html)
2. Memory 512Mb
3. Timeout: 1 minute
4. VPC Enabled - running in Private VPCs
5. Handler Name: com.mcmaster.aws.lambda.oracleRdsRotate.RotateHandl

All other Lambda settings are defaults.

You must have a Secret in Secrets Manager that has the following structure:

```json
{
  "host": "<Database Host, on the AWS Console use the 'Endpoint' value, for example: 'dbtest.fed87geffdsf.eu-west-1.rds.amazonaws.com'>",
  "port": "<Database Port, e.g. 1521>",
  "dbname": "<Database Name, default is ORCL>",
  "username": "<User Name, default is admin>",
  "password": "<Oracle Password, entered when the database was created>"
}
```
Once you have configured all of the security requirements as per the below, you can configure your secret to be rotated, and specify the Lambda you have created as the target for Rotation.

<strong>Note:</strong> You will only be able to add the Lambda as a target after configuring access from Secrets Manager to Lambda, as described below.

# Running the Lambda in a VPC
It is generally good practice to run your RDS Database within a Private VPC, without any Public Access.

In cases such as this your Lambda will also need to run within a VPC.

When running a Lambda in a VPC, ensure that you create a VPC Endpoint for Secrets Manager, to allow Lambda to retrieve the secret that contains your database configuration.  

The VPC Endpoint should be connected to the same set of Subnets that your Lambda Belongs to.

# Security Configuration

## Access from Secrets Manager to Lambda

The following AWSCLI command can be used to allow secrets manager to access your Lambda:

aws lambda add-permission --function-name {LAMBDA_FUNCTION_ARN} --action lambda:InvokeFunction --principal secretsmanager.amazonaws.com --statement-id SecretsManagerAccess

## Access required for the Lambda

Your Lambda should have an AWS Iam role attached to it.  That IAM Role should have the following policies attached:

1. Provide access to the Secret That Contains the Database Configuration Information (e.g. Secrets Manager);
2. If the Secret Containing the Database Configuration is encrypted with a CMK, the Lambda must also have access to this CMK; 
3. Provide access to CloudWatch (e.g. the AWS Role "AWSLambdaBasicExecutionRole")

Your Lambda should also have a Security Group attached to it.  In your Oracle RDS Security Group, allow access <strong>from</strong> your Lambda to Oracle by adding the Lambda SG ID to the ingress rules for the Oracle Database on port 1521.

