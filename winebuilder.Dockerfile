#use DOCKER_BUILDKIT=1 to cache downloaded layer and --output to export built Artefact
#./gradlew clean && DOCKER_BUILDKIT=1 docker build --progress=plain --file winebuilder.Dockerfile --output type=tar,dest=../artefact.tar .
FROM scratch as caching-downloader
ADD https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse /jdk.zip

FROM alpine:3.17 as builder
RUN apk add --no-cache --update wine git util-linux
RUN adduser -D user
ENV WINEPREFIX=/home/user \
	WINEDEBUG=-all \
    JAVA_OPTS="-Xmx2G -Dfile.encoding=UTF-8"
USER user
COPY --chown=user --from=caching-downloader / /tmp
WORKDIR "$WINEPREFIX/drive_c/Program Files"
RUN unzip /tmp/jdk.zip -d /tmp && rm /tmp/jdk.zip && mv /tmp/jdk* jdk
RUN wine64 wineboot --init && \
	wine64 reg add "HKEY_CURRENT_USER\\Environment" /v JAVA_HOME /t REG_SZ /d "c:\\Program Files\\jdk" && \
	wineserver -w && \
	sleep 5
WORKDIR "$WINEPREFIX/drive_c/project"
COPY --chown=user . .
ENV GRADLE_OPTS="-Dorg.gradle.caching=false -Dorg.gradle.caching.debug=false -Dorg.gradle.daemon=false -Dorg.gradle.configureondemand=false -Dorg.gradle.console=plain -Dorg.gradle.vfs.watch=false -Dorg.gradle.welcome=never"
#script is used to hackfix Stdout\err gradle error https://github.com/gradle/native-platform/issues/323
RUN script --return --quiet -c "wine64 cmd /c gradlew.bat clean jlink" /dev/null
FROM scratch AS export-stage
COPY --from=builder /home/user/drive_c/project/app/build/jre /jre
