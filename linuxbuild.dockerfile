FROM oracle/graalvm-ce
RUN gu install native-image
WORKDIR /tmp/dist
CMD native-image -jar /tmp/target/invoicing-api.jar --enable-url-protocols=https,http --no-fallback --allow-incomplete-classpath bootstrap

