# 知枢后端运行镜像：只提供运行时（JRE + 字体 + curl），不把 jar 打包进镜像，也不在容器内编译。
# jar 由宿主机 deployment/app 目录只读挂载到 /app/deploy，换 jar 后 docker compose restart 即生效。
FROM eclipse-temurin:21-jre

# 容器内运行用户 uid/gid；挂宿主机目录时需保证该 uid 对 data/ 有写权限
ARG APP_UID=10001

# curl：HEALTHCHECK 依赖；fontconfig + fonts-dejavu-core：滑动验证码走 java.awt/hutool ImgUtil，缺字体会 500
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system zhishu \
    && useradd --system --gid zhishu --uid ${APP_UID} --create-home --home /home/zhishu zhishu

# WORKDIR=/app：配置里的相对路径 uploads、logs/zhishu-backend.log 分别对应下面两个挂载点
WORKDIR /app
# /app/deploy = 宿主机 deployment/app（jar）；/app/logs 与 /app/uploads = 宿主机 deployment/data 下同名目录
# 先以 root 建目录再 chown，保证后续挂上宿主机目录后权限正确
RUN mkdir -p /app/deploy /app/logs /app/uploads \
    && chown -R ${APP_UID}:${APP_UID} /app/logs /app/uploads

USER zhishu

# 与 compose 的 SERVER_PORT 默认值保持一致（application-prod.yml 原生 8080 已被覆盖为 8180）
EXPOSE 8180

# 最大堆固定 2G；mem_limit 需留出 Metaspace/线程栈/CodeCache/堆外余量（compose 默认 3g）
# 临时调参无需重建镜像：JAVA_OPTS="-Xmx4g ..." docker compose up -d（覆盖时请保留编码与 headless 参数）
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseContainerSupport -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -Djava.awt.headless=true -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DCONSOLE_LOG_CHARSET=UTF-8"

# 启动时从 /app/deploy 取修改时间最新的 jar（app/ 下建议只保留一个）；可用 JAR_GLOB 改变匹配名
# 无 jar 时直接退出并提示，避免带着旧代码假启动
ENV JAR_GLOB="zhishu-core-*.jar"
ENTRYPOINT ["sh","-c","JAR=\"$(ls -1t /app/deploy/${JAR_GLOB} 2>/dev/null | head -n 1)\"; [ -n \"$JAR\" ] || { echo \"[FATAL] /app/deploy 下未找到 ${JAR_GLOB}：请先在 Windows 执行 scripts/package-jar.ps1，再把 jar 放到 deployment/app/\"; exit 1; }; echo \"[START] java -jar $JAR\"; exec java $JAVA_OPTS -jar \"$JAR\" --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
