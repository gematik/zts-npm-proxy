FROM gematik1/osadl-alpine-openjdk25-jre:1.0.8@sha256:257288f1dc49eb6984140869d104c0ea9ef884854eb357474a7b9ced7d01ef9e

# The STOPSIGNAL instruction sets the system call signal that will be sent to the container to exit
# SIGTERM = 15 - https://de.wikipedia.org/wiki/Signal_(Unix)
STOPSIGNAL SIGTERM

# Define the exposed port or range of ports for the service
EXPOSE 8080

# Defining Healthcheck
HEALTHCHECK --interval=15s \
            --timeout=10s \
            --start-period=30s \
            --retries=3 \
            CMD ["/usr/bin/wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/api/health/liveness"]

# Default USERID and GROUPID
ARG USERID=10000
ARG GROUPID=10000

COPY --chown=$USERID:$GROUPID target/npm-proxy.jar /app.jar

# Run as User (not root)
USER $USERID:$USERID

# --enable-native-access=ALL-UNNAMED is used because lucene's class MMapDirectory requires access to native code
# see https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html

ENTRYPOINT ["java","--enable-native-access=ALL-UNNAMED", "-jar", "/app.jar"]

# Git Args
ARG COMMIT_HASH
ARG VERSION

###########################
# Labels
###########################
LABEL de.gematik.vendor="gematik GmbH" \
      maintainer="zts@gematik.de" \
      de.gematik.app="ZTS NPM Proxy" \
      de.gematik.git-repo-name="https://gitlab.prod.ccs.gematik.solutions/zts/services/npm-proxy" \
      de.gematik.commit-sha=$COMMIT_HASH \
      de.gematik.version=$VERSION