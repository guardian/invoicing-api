# Make sure gcc et al are present for native linking
FROM oracle/graalvm-ce:20.2.0-java11

# Intended for: zip -r -j target/scala-2.13/invoicing-api.zip target/scala-2.13/bootstrap
RUN yum -y install zip

# Install sbt and coursier via www.scala-lang.org/2020/06/29/one-click-install.html
RUN curl -Lo /opt/cs https://git.io/coursier-cli-linux && chmod +x opt/cs
RUN echo 'Y' | ./opt/cs setup
ENV PATH="/root/.local/share/coursier/bin:$PATH"

# Manage JVM with coursier
RUN cs java-home --jvm graalvm-java11:20.2.0

# Populate sbt cache to speed up build
RUN set -x \
  && cd /tmp \
  && echo "ThisBuild / scalaVersion := \"2.13.3\"" >> build.sbt \
  && mkdir -p project \
  && echo "sbt.version=1.4.4" >> project/build.properties \
  && echo "object Test" >> Test.scala \
  && sbt compile \
  && sbt compile \
  && rm Test.scala \
  && rm -rf project \
  && rm -rf target \
  && rm build.sbt

# Intended for: docker run -v "$(pwd -P)":/invoicing-api invoicing-api
WORKDIR /invoicing-api
CMD ["sbt", "packageNativeAwsImage"]

