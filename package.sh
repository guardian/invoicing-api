targetDir=target/scala-2.13

echo "
  FROM oracle/graalvm-ce
  RUN gu install native-image
  WORKDIR /${targetDir}
  CMD native-image -jar invoicing-api.jar --enable-url-protocols=https,http --no-fallback --allow-incomplete-classpath bootstrap
" | docker build -f - --tag linux-native-image .
docker run -v "$(pwd -P)/${targetDir}":/${targetDir} linux-native-image
zip -r -j ${targetDir}/invoicing-api.zip ${targetDir}/bootstrap