targetDir=target/scala-2.13

echo "
  FROM oracle/graalvm-ce:20.2.0-java11-ol8
  RUN gu install native-image
  WORKDIR /${targetDir}
  CMD native-image -jar invoicing-api.jar --enable-url-protocols=https,http --no-fallback --allow-incomplete-classpath --report-unsupported-elements-at-runtime --initialize-at-build-time=scala.runtime.Statics$VM bootstrap
" | docker build -f - --tag linux-native-image .
docker run -v "$(pwd -P)/${targetDir}":/${targetDir} linux-native-image
if [ $? -ne 0 ]; then
  echo "Failed to build native image"
  exit 1
else
  echo "Successfully build native image"
  zip -r -j ${targetDir}/invoicing-api.zip ${targetDir}/bootstrap
fi

