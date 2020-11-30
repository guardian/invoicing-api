# Make sure gcc et al are present for native linking
FROM oracle/graalvm-ce:20.2.0-java11

# Intended for: zip -r -j target/scala-2.13/invoicing-api.zip target/scala-2.13/bootstrap
RUN yum -y install zip

# Install sbt and coursier via www.scala-lang.org/2020/06/29/one-click-install.html
RUN curl -Lo /opt/cs https://git.io/coursier-cli-linux && chmod +x opt/cs
RUN ./opt/cs setup --yes
ENV PATH="/root/.local/share/coursier/bin:$PATH"

# Manage JVM with coursier
RUN cs java-home --jvm graalvm-java11:20.2.0

# Intended for: docker run -v "$(pwd -P)":/invoicing-api invoicing-api
WORKDIR /invoicing-api
CMD ["sbt", "packageNativeAwsImage"]

