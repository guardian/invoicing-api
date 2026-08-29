#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { InvoicingApiStack } from '../lib/invoicing-api-stack';

const app = new cdk.App();

const stack = 'support';
const stage = app.node.tryGetContext('stage') || 'CODE';

new InvoicingApiStack(app, `InvoicingApi-${stage}`, {
  stack,
  stage,
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION || 'eu-west-1',
  },
});
