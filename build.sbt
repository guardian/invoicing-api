
lazy val root = (project in file("."))
  .enablePlugins(RiffRaffArtifact)
  .settings(
    name := "invoicing-api",
    description := "Zuora Invoice management for supporters (refund, etc.)",
    version := "0.1.0",
    organization := "com.gu",
    organizationName := "The Guardian",
    scalaVersion := "2.13.1",
    libraryDependencies ++= List(
      "org.scalameta" %% "munit" % "0.7.1" % Test,
      "com.gu" %% "spy" % "0.1.1",
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
      "com.amazonaws" % "aws-lambda-java-events" % "1.2.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "org.scalaj" %% "scalaj-http" % "2.4.2",
      "com.lihaoyi" %% "upickle" % "1.0.0",
    ),
    assemblyJarName := "invoicing-api.jar",
    riffRaffPackageType := assembly.value,
    riffRaffUploadArtifactBucket := Option("riffraff-artifact"),
    riffRaffUploadManifestBucket := Option("riffraff-builds"),
    riffRaffManifestProjectName := "support:invoicing-api",
    riffRaffArtifactResources += (file("cfn.yaml"), "cfn/cfn.yaml")
  )

