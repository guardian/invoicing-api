# Make sure gcc et al are present for native linking
FROM oracle/graalvm-ce:20.2.0-java11

# Intended for: zip -r -j target/scala-2.13/invoicing-api.zip target/scala-2.13/bootstrap
RUN yum -y install zip unzip

# Install sbt
RUN set -x \
  && SBT_VER="1.4.4" \
  && curl -Ls https://github.com/sbt/sbt/releases/download/v${SBT_VER}/sbt-$SBT_VER.tgz > /opt/sbt-${SBT_VER}.tgz \
  && tar -zxf /opt/sbt-${SBT_VER}.tgz -C /opt

ENV PATH="/opt/sbt/bin:$PATH"

# Intended for: docker run -v "$(pwd -P)":/invoicing-api invoicing-api
WORKDIR /invoicing-api
CMD ["sbt", "packageNativeAwsImage"]

