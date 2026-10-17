FROM ubuntu:24.04 AS build

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        cmake \
        git \
        libssl-dev \
        nlohmann-json3-dev \
        zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY CMakeLists.txt ./
COPY core ./core
COPY cli ./cli
COPY tests ./tests
COPY server ./server
RUN cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build --target chess_server -j2

FROM ubuntu:24.04 AS runtime

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends libssl3 zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 chess \
    && useradd --system --uid 10001 --gid chess --home-dir /var/lib/chess --create-home chess

WORKDIR /app
COPY --from=build --chown=chess:chess /workspace/build/bin/chess_server ./chess_server
RUN mkdir -p /var/lib/chess/outbox && chown -R chess:chess /var/lib/chess

USER chess
EXPOSE 9002
ENTRYPOINT ["/app/chess_server"]
