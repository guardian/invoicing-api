import * as cdk from 'aws-cdk-lib';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigateway from 'aws-cdk-lib/aws-apigateway';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as certificatemanager from 'aws-cdk-lib/aws-certificatemanager';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as sns from 'aws-cdk-lib/aws-sns';
import { Construct } from 'constructs';
import { GuStack, GuStackProps } from '@guardian/cdk/lib/constructs/core';

export interface InvoicingApiStackProps extends GuStackProps {
  stack: string;
  stage: string;
}

export class InvoicingApiStack extends GuStack {
  constructor(scope: Construct, id: string, props: InvoicingApiStackProps) {
    super(scope, id, props);

    const { stack, stage } = props;

    // Configuration mappings
    const stageConfig = {
      CODE: {
        configVersion: 2,
        domainName: 'invoicing-api-code.support.guardianapis.com',
      },
      PROD: {
        configVersion: 1,
        domainName: 'invoicing-api.support.guardianapis.com',
      },
    }[stage];

    if (!stageConfig) {
      throw new Error(`Invalid stage: ${stage}`);
    }

    // TLS Certificate for *.support.guardianapis.com
    const certificate = certificatemanager.Certificate.fromCertificateArn(
      this,
      'TLSCertificate',
      `arn:aws:acm:${this.region}:${this.account}:certificate/b384a6a0-2f54-4874-b99b-96eeff96c009`
    );

    // IAM Role for Lambda Functions
    const lambdaRole = new iam.Role(this, 'InvoicingLambdaRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      path: '/',
      inlinePolicies: {
        LambdaPolicy: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              effect: iam.Effect.ALLOW,
              actions: [
                'logs:CreateLogGroup',
                'logs:CreateLogStream',
                'logs:PutLogEvents',
                'lambda:InvokeFunction',
              ],
              resources: [
                `arn:aws:logs:${this.region}:${this.account}:log-group:/aws/lambda/invoicing-api-refund-${stage}:log-stream:*`,
                `arn:aws:logs:${this.region}:${this.account}:log-group:/aws/lambda/invoicing-api-invoices-${stage}:log-stream:*`,
                `arn:aws:logs:${this.region}:${this.account}:log-group:/aws/lambda/invoicing-api-pdf-${stage}:log-stream:*`,
                `arn:aws:logs:${this.region}:${this.account}:log-group:/aws/lambda/invoicing-api-nextinvoicedate-${stage}:log-stream:*`,
                `arn:aws:logs:${this.region}:${this.account}:log-group:/aws/lambda/invoicing-api-preview-${stage}:log-stream:*`,
                `arn:aws:logs:${this.region}:${this.account}:log-group:/aws/lambda/invoicing-api-refund-erroneous-payment-${stage}:log-stream:*`,
              ],
            }),
          ],
        }),
        ReadPrivateCredentials: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              effect: iam.Effect.ALLOW,
              actions: ['s3:GetObject'],
              resources: [
                `arn:aws:s3:::gu-reader-revenue-private/membership/support-service-lambdas/${stage}/zuoraRest-${stage}*.json`,
              ],
            }),
          ],
        }),
        ReadFromDeploymentBucket: new iam.PolicyDocument({
          statements: [
            new iam.PolicyStatement({
              effect: iam.Effect.ALLOW,
              actions: ['s3:GetObject'],
              resources: ['arn:aws:s3:::membership-dist/*'],
            }),
          ],
        }),
      },
    });

    // Common Lambda properties
    const commonLambdaProps = {
      runtime: lambda.Runtime.JAVA_11,
      memorySize: 3008,
      timeout: cdk.Duration.minutes(15),
      role: lambdaRole,
      code: lambda.Code.fromBucket(
        cdk.aws_s3.Bucket.fromBucketName(this, 'DeployBucket', 'membership-dist'),
        `${stack}/${stage}/invoicing-api/invoicing-api.jar`
      ),
      environment: {
        Stage: stage,
        Config: `{{resolve:ssm:/invoicing-api/${stage}/config:${stageConfig.configVersion}}}`,
      },
    };

    // Lambda Functions
    const refundLambda = new lambda.Function(this, 'RefundLambda', {
      ...commonLambdaProps,
      functionName: `invoicing-api-refund-${stage}`,
      handler: 'com.gu.invoicing.refund.Lambda::handleRequest',
      description: 'Apply refund to Zuora invoice by adjusting invoice items',
    });

    const invoicesLambda = new lambda.Function(this, 'InvoicesLambda', {
      ...commonLambdaProps,
      functionName: `invoicing-api-invoices-${stage}`,
      handler: 'com.gu.invoicing.invoice.Lambda::handleRequest',
      description: 'Retrieve all invoices with payment methods by accountId',
    });

    const pdfLambda = new lambda.Function(this, 'PdfLambda', {
      ...commonLambdaProps,
      functionName: `invoicing-api-pdf-${stage}`,
      handler: 'com.gu.invoicing.pdf.Lambda::handleRequest',
      description: 'Download PDF invoice by invoiceId',
    });

    const nextInvoiceDateLambda = new lambda.Function(this, 'NextInvoiceDateLambda', {
      ...commonLambdaProps,
      functionName: `invoicing-api-nextinvoicedate-${stage}`,
      handler: 'com.gu.invoicing.nextinvoicedate.Lambda::handleRequest',
      description: 'Get next invoice date (day after last invoice period)',
    });

    const previewLambda = new lambda.Function(this, 'PreviewLambda', {
      ...commonLambdaProps,
      functionName: `invoicing-api-preview-${stage}`,
      handler: 'com.gu.invoicing.preview.Lambda::handleRequest',
      description: 'Preview affected publications within date range',
    });

    const refundErroneousPaymentLambda = new lambda.Function(this, 'RefundErroneousPaymentLambda', {
      ...commonLambdaProps,
      functionName: `invoicing-api-refund-erroneous-payment-${stage}`,
      handler: 'com.gu.invoicing.refundErroneousPayment.Lambda::handleRequest',
      description: 'Apply refund to Zuora invoice by shuffling credit balances',
    });

    // API Gateway
    const api = new apigateway.RestApi(this, 'InvoicingApi', {
      restApiName: `invoicing-api-${stage}`,
      description: 'Zuora invoice management (refunds, list invoices, download PDF, invoice preview)',
      binaryMediaTypes: ['application/pdf'],
      deployOptions: {
        stageName: stage,
      },
      domainName: {
        domainName: stageConfig.domainName,
        certificate: certificate,
      },
    });

    // API Routes and Methods

    // POST /refund
    const refundResource = api.root.addResource('refund');
    refundResource.addMethod('POST', new apigateway.LambdaIntegration(refundLambda), {
      apiKeyRequired: true,
    });

    // POST /refund-erroneous-payment
    const refundErroneousPaymentResource = api.root.addResource('refund-erroneous-payment');
    refundErroneousPaymentResource.addMethod('POST', new apigateway.LambdaIntegration(refundErroneousPaymentLambda), {
      apiKeyRequired: true,
    });

    // GET /invoices
    const invoicesResource = api.root.addResource('invoices');
    invoicesResource.addMethod('GET', new apigateway.LambdaIntegration(invoicesLambda), {
      apiKeyRequired: true,
      authorizationType: apigateway.AuthorizationType.IAM,
      requestParameters: {
        'method.request.path.accountId': true,
      },
    });

    // GET /invoices/{invoiceId}
    const invoiceIdResource = invoicesResource.addResource('{invoiceId}');
    invoiceIdResource.addMethod('GET', new apigateway.LambdaIntegration(pdfLambda), {
      apiKeyRequired: true,
      authorizationType: apigateway.AuthorizationType.IAM,
      requestParameters: {
        'method.request.path.invoiceId': true,
      },
    });

    // GET /next-invoice-date/{subscriptionName}
    const nextInvoiceDateResource = api.root.addResource('next-invoice-date');
    const subscriptionNameResource = nextInvoiceDateResource.addResource('{subscriptionName}');
    subscriptionNameResource.addMethod('GET', new apigateway.LambdaIntegration(nextInvoiceDateLambda), {
      apiKeyRequired: true,
      requestParameters: {
        'method.request.path.subscriptionName': true,
      },
    });

    // GET /preview/{subscriptionName}?startDate=yyyy-mm-dd&endDate=yyyy-mm-dd
    const previewResource = api.root.addResource('preview');
    const previewSubscriptionResource = previewResource.addResource('{subscriptionName}');
    previewSubscriptionResource.addMethod('GET', new apigateway.LambdaIntegration(previewLambda), {
      apiKeyRequired: true,
      requestParameters: {
        'method.request.path.subscriptionName': true,
        'method.request.querystring.startDate': true,
        'method.request.querystring.endDate': true,
      },
    });

    // Usage Plan and API Key
    const usagePlan = api.addUsagePlan('InvoicingApiUsagePlan', {
      name: 'invoicing-api',
      apiStages: [
        {
          api: api,
          stage: api.deploymentStage,
        },
      ],
    });

    const apiKey = api.addApiKey('InvoicingApiKey', {
      apiKeyName: `invoicing-api-key-${stage}`,
      description: 'Used by https://github.com/guardian/invoicing-api',
    });

    usagePlan.addApiKey(apiKey);

    // DNS Record
    new route53.CnameRecord(this, 'InvoicingApiDNSRecord', {
      zone: route53.HostedZone.fromLookup(this, 'HostedZone', {
        domainName: 'support.guardianapis.com',
      }),
      recordName: stageConfig.domainName.replace('.support.guardianapis.com', ''),
      domainName: api.domainName?.domainNameAliasDomainName || '',
      ttl: cdk.Duration.minutes(2),
    });

    // CloudWatch Alarms (only for PROD)
    if (stage === 'PROD') {
      const alarmTopic = sns.Topic.fromTopicArn(
        this,
        'AlarmTopic',
        `arn:aws:sns:${this.region}:${this.account}:alarms-handler-topic-PROD`
      );

      // Failed Invoices Alarm
      new cloudwatch.Alarm(this, 'FailedInvoicesAlarm', {
        alarmName: 'URGENT 9-5 - PROD: Failed to fetch Zuora invoice by account',
        alarmDescription: [
          '- manage-frontend user will not be able to see/download invoices',
          '- https://github.com/guardian/invoicing-api/blob/main/src/main/scala/com/gu/invoicing/invoice/README.md',
          `- export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-invoices-PROD --start='DD/mm/yyyy HH:MM' --end='DD/mm/yyyy HH:MM'`,
        ].join('\n'),
        metric: invoicesLambda.metricErrors({
          period: cdk.Duration.minutes(15),
        }),
        threshold: 5,
        evaluationPeriods: 1,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });

      // Failed PDF Alarm
      new cloudwatch.Alarm(this, 'FailedPdfAlarm', {
        alarmName: 'URGENT 9-5 - PROD: Failed to download invoice PDF',
        alarmDescription: [
          '- manage-frontend user will not be able to download invoice PDF',
          '- https://github.com/guardian/invoicing-api/blob/main/src/main/scala/com/gu/invoicing/pdf/README.md',
          `- export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-pdf-PROD --start='DD/mm/yyyy HH:MM' --end='DD/mm/yyyy HH:MM'`,
        ].join('\n'),
        metric: pdfLambda.metricErrors({
          period: cdk.Duration.minutes(15),
        }),
        threshold: 5,
        evaluationPeriods: 1,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });

      // Next Invoice Date Alarm
      new cloudwatch.Alarm(this, 'NextInvoiceDateAlarm', {
        alarmName: 'URGENT 9-5 - PROD: Failed to determine next invoice date',
        alarmDescription: [
          '- At least holiday or delivery problem credit processor will not be able to determine when to apply the credit',
          '- https://github.com/guardian/invoicing-api/blob/main/src/main/scala/com/gu/invoicing/nextinvoicedate/README.md',
          `- export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-nextinvoicedate-PROD --start='DD/mm/yyyy HH:MM' --end='DD/mm/yyyy HH:MM'`,
        ].join('\n'),
        metric: nextInvoiceDateLambda.metricErrors({
          period: cdk.Duration.minutes(5),
        }),
        threshold: 1,
        evaluationPeriods: 1,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });

      // Preview Alarm
      new cloudwatch.Alarm(this, 'PreviewAlarm', {
        alarmName: 'URGENT 9-5 - PROD: Failed to preview affected publications within date range',
        alarmDescription: [
          '- At least holiday-stop-api will not be able to predict which publications should be suspended',
          '- https://github.com/guardian/invoicing-api/blob/main/src/main/scala/com/gu/invoicing/preview/README.md',
          `- export AWS_DEFAULT_REGION=eu-west-1 && awslogs get --profile membership /aws/lambda/invoicing-api-preview-PROD --start='DD/mm/yyyy HH:MM' --end='DD/mm/yyyy HH:MM'`,
        ].join('\n'),
        metric: previewLambda.metricErrors({
          period: cdk.Duration.minutes(5),
        }),
        threshold: 1,
        evaluationPeriods: 1,
        treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      });
    }

    // Outputs
    new cdk.CfnOutput(this, 'ApiUrl', {
      value: api.url,
      description: 'URL of the API Gateway',
    });

    new cdk.CfnOutput(this, 'CustomDomainUrl', {
      value: `https://${stageConfig.domainName}`,
      description: 'Custom domain URL',
    });

    new cdk.CfnOutput(this, 'ApiKeyId', {
      value: apiKey.keyId,
      description: 'API Key ID for invoicing-api',
    });
  }
}
