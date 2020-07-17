lazy val root = (project in file("."))
  .enablePlugins(RiffRaffArtifact)
  .settings(
    name := "invoicing-api",
    description := "Zuora Invoice management for supporters (refund, etc.)",
    version := "0.1.0",
    organization := "com.gu",
    organizationName := "The Guardian",
    scalaVersion := "2.13.3",
    libraryDependencies ++= List(
      "org.scalaj"      %% "scalaj-http"  % "2.4.2",
      "com.lihaoyi"     %% "upickle"      % "1.1.0",
      "org.scalameta"   %% "munit"        % "0.7.9"   % Test,
      "com.gu"          %% "spy"          % "0.1.1",
      "org.scala-lang.modules" %% "scala-async" % "1.0.0-M1"
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    assemblyJarName := "invoicing-api.jar",
    riffRaffPackageType := assembly.value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := "support:invoicing-api",
    riffRaffArtifactResources += (file("cfn.yaml"), "cfn/cfn.yaml"),
    scalacOptions ++= Seq(
      "-Xasync"
    )
  )

lazy val deployAwsLambda = taskKey[Unit]("Directly update AWS lambda code from DEV instead of via RiffRaff for faster feedback loop")
deployAwsLambda := {
  import scala.sys.process._
  assembly.value
  "aws lambda update-function-code --function-name invoicing-api-refund-CODE --zip-file fileb://target/scala-2.13/invoicing-api.jar --profile membership --region eu-west-1".!
  "aws lambda update-function-code --function-name invoicing-api-invoices-CODE --zip-file fileb://target/scala-2.13/invoicing-api.jar --profile membership --region eu-west-1".!
}
