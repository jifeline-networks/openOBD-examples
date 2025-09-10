FROM eclipse-temurin:21.0.5_11-jre-alpine-3.21

WORKDIR /opt/project

COPY --chmod=755 ./entrypoint_local.sh /opt/project/entrypoint.sh

ENV FUNCTIONS_FILE_LOCATION="/opt/project/functions.json"
ENV GRPC_TARGET_PORT="50051"

ENTRYPOINT [ "./entrypoint.sh" ]