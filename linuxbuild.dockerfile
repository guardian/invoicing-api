FROM oracle/graalvm-ce
RUN gu install native-image
WORKDIR /target/scala-2.13
CMD native-image -jar invoicing-api.jar --enable-url-protocols=https,http --no-fallback --allow-incomplete-classpath bootstrap

