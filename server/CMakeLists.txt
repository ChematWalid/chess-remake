add_library(chess_server_lib
    src/RoomCode.cpp
    src/Protocol.cpp
    src/GameRoom.cpp
    src/RoomManager.cpp
    src/WebSocketSession.cpp
    src/WebSocketServer.cpp
    src/JwtToken.cpp
    src/WebhookClient.cpp
)

target_include_directories(chess_server_lib PUBLIC
    include
    ${CMAKE_SOURCE_DIR}/core/include
)

target_link_libraries(chess_server_lib PUBLIC
    chess_core
    ixwebsocket
    OpenSSL::Crypto
    nlohmann_json::nlohmann_json
    Threads::Threads
)

add_executable(chess_server src/main.cpp)
target_link_libraries(chess_server PRIVATE chess_server_lib)
