cmake_minimum_required(VERSION 3.20)
project(Chess CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Nice compile warnings
add_compile_options(-Wall -Wextra -Wpedantic)

# Put binaries in a predictable place
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

# ── Dependencies ─────────────────────────────────────────────────────────────
include(FetchContent)

FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG        v1.16.0
)
# Prevent GTest from installing itself system-wide
set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

find_package(nlohmann_json 3.2.0 REQUIRED)

FetchContent_Declare(
    ixwebsocket
    GIT_REPOSITORY https://github.com/machinezone/IXWebSocket.git
    GIT_TAG        v11.4.6
)
set(USE_TLS ON CACHE BOOL "" FORCE)
set(USE_WS ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(ixwebsocket)

find_package(Threads REQUIRED)
find_package(OpenSSL REQUIRED)

# ── Subdirectories ────────────────────────────────────────────────────────────
add_subdirectory(core)
add_subdirectory(tests)
add_subdirectory(cli)
add_subdirectory(server)
