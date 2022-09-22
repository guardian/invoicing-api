lazy val root = (project in file("."))
  .enablePlugins(RiffRaffArtifact, NativeImagePlugin)
  .settings(
    name := "invoicing-api",
    description := "Zuora Invoice management for supporters (refund, etc.)",
    version := "0.1.0",
    organization := "com.gu",
    organizationName := "The Guardian",
    scalaVersion := "2.13.9",
    libraryDependencies ++= List(
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "org.scalaj" %% "scalaj-http" % "2.4.2",
      "com.lihaoyi" %% "upickle" % "1.5.0",
      "com.gu" %% "spy" % "0.1.1",
      "org.scala-lang.modules" %% "scala-async" % "1.0.1",
      "com.lihaoyi" %% "pprint" % "0.7.2"
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    assemblyJarName := s"${name.value}.jar",
    riffRaffPackageType := crossTarget.value / s"${name.value}.zip",
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := "support:invoicing-api",
    riffRaffArtifactResources += (file("cfn.yaml"), "cfn/cfn.yaml"),
    scalacOptions ++= Seq(
      "-Xasync"
    ),
    Compile / mainClass := Some("bootstrap"), // AWS custom runtime entry point
    nativeImageOptions ++= Seq(
      "--enable-http",
      "--enable-https",
      "--no-fallback"
    )
  )

/** This uses Docker to enable building a linux image from any platform */
lazy val deployAwsLambda = inputKey[Unit](
  "Directly update AWS lambda code from DEV instead of via RiffRaff for faster feedback loop"
)
deployAwsLambda := {
  import complete.DefaultParsers._
  val stage = (Space ~> StringBasic).examples("<DEV | CODE | PROD>").parsed
  val lambdaNativeZip =
    s"""${crossTarget.value}/${name.value}.zip""" /* target/scala-2.13/invoicing-api.zip */
  import scala.sys.process._
  def updateLambda(functionName: String) =
    s"aws lambda update-function-code --function-name $functionName-$stage --zip-file fileb://$lambdaNativeZip --profile membership --region eu-west-1"

  """docker build -t invoicing-api ."""
    .#&&(s"""docker run -v ${baseDirectory.value}:/invoicing-api invoicing-api""")
    .#&&(s"""zip -r -j $lambdaNativeZip ${crossTarget.value}/bootstrap""")
    .#&&(updateLambda("invoicing-api-refund"))
    .#&&(updateLambda("invoicing-api-invoices"))
    .#&&(updateLambda("invoicing-api-pdf"))
    .#&&(updateLambda("invoicing-api-nextinvoicedate"))
    .#&&(updateLambda("invoicing-api-preview"))
    .#&&(updateLambda("invoicing-api-refund-erroneous-payment"))
    .!
}

addCommandAlias("packageNativeAwsImage", "nativeImageCopy target/scala-2.13/bootstrap")
