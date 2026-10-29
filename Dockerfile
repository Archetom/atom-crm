FROM eclipse-temurin:27-jre

# The upstream 27-jre image still ships 27+35; install the published CPU release.
ENV JAVA_VERSION=jdk-27.0.1.0.2+1
RUN set -eux; \
    case "$(dpkg --print-architecture)" in \
        amd64) \
            jre_arch=x64; \
            jre_sha256=104b5d204314aa33dff4a9a5b925e2c58790e9d8cd7097b678e8e4d8cf86ced0 ;; \
        arm64) \
            jre_arch=aarch64; \
            jre_sha256=d77286f01dc2482e483a20c9c80591e3d4c38b27a6e035268a4f213a8e8bd1e8 ;; \
        *) echo "Unsupported runtime architecture" >&2; exit 1 ;; \
    esac; \
    jre_saved_apt_marks="$(apt-mark showmanual)"; \
    apt-get update; \
    apt-get install -y --no-install-recommends wget; \
    wget --https-only -O /tmp/temurin-jre.tar.gz \
        "https://github.com/adoptium/temurin27-binaries/releases/download/jdk-27.0.1.0.2%2B1/OpenJDK27U-jre_${jre_arch}_linux_hotspot_27.0.1.0.2_1.tar.gz"; \
    echo "${jre_sha256}  /tmp/temurin-jre.tar.gz" | sha256sum -c -; \
    rm -rf /opt/java/openjdk; \
    mkdir -p /opt/java/openjdk; \
    tar --extract --file /tmp/temurin-jre.tar.gz --directory /opt/java/openjdk \
        --strip-components 1 --no-same-owner; \
    rm -f /tmp/temurin-jre.tar.gz; \
    apt-mark auto '.*' > /dev/null; \
    apt-mark manual ${jre_saved_apt_marks} > /dev/null; \
    apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false; \
    rm -rf /var/lib/apt/lists/*; \
    find /opt/java/openjdk/lib -name '*.so' -exec dirname '{}' ';' | sort -u > /etc/ld.so.conf.d/docker-openjdk.conf; \
    ldconfig; \
    java --version

WORKDIR /app
RUN mkdir -p /app/logs && chown -R 10001:10001 /app
COPY --chown=10001:10001 start/target/atom-crm-start.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
