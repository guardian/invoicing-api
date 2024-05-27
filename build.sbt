lazy val root = (project in file("."))
  .enablePlugins(RiffRaffArtifact)
  .settings(
    name := "invoicing-api",
    description := "Zuora Invoice management for supporters (refund, etc.)",
    version := "0.1.0",
    organization := "com.gu",
    organizationName := "The Guardian",
    scalaVersion := "2.13.14",
    libraryDependencies ++= List(
      "org.scalameta" %% "munit" % "1.0.0" % Test,
      "org.scalaj" %% "scalaj-http" % "2.4.2",
      "com.lihaoyi" %% "upickle" % "3.3.1",
      "com.gu" %% "spy" % "0.1.1",
      "org.scala-lang.modules" %% "scala-async" % "1.0.1",
      "com.lihaoyi" %% "pprint" % "0.9.0",
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.5",
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    assemblyJarName := s"${name.value}.jar",
    riffRaffPackageType := assembly.value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := "support:invoicing-api",
    riffRaffArtifactResources += (file("cfn.yaml"), "cfn/cfn.yaml"),
    scalacOptions ++= Seq( // Needed to support Scala async/await https://www.baeldung.com/scala/scala-async
      "-Xasync",
    ),
  )

lazy val deployTo =
  inputKey[Unit](
    "Directly update AWS lambda code from your local machine instead of via RiffRaff for faster feedback loop",
  )

deployTo := {
  import scala.sys.process._
  import complete.DefaultParsers._
  val jarFile = assembly.value

  val Seq(stage) = spaceDelimited("<arg>").parsed
  val s3Bucket = "membership-dist"
  val s3Path = s"support/$stage/invoicing-api/invoicing-api.jar"

  s"aws s3 cp $jarFile s3://$s3Bucket/$s3Path --profile membership --region eu-west-1".!!
  List(
    "invoicing-api-refund",
    "invoicing-api-invoices",
    "invoicing-api-pdf",
    "invoicing-api-nextinvoicedate",
    "invoicing-api-preview",
    "invoicing-api-refund-erroneous-payment",
  ).foreach { functionName =>
    System.out.println(s"Updating lambda $functionName")
    s"aws lambda update-function-code --function-name $functionName-$stage --s3-bucket $s3Bucket --s3-key $s3Path --profile membership --region eu-west-1".!!
  }
}
