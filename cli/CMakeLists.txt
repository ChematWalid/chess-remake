# cli/ — interactive command-line chess interface

file(GLOB_RECURSE CLI_SOURCES CONFIGURE_DEPENDS src/*.cpp)

if(NOT CLI_SOURCES)
    set(CLI_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/src/_placeholder.cpp)
    file(WRITE ${CMAKE_CURRENT_SOURCE_DIR}/src/_placeholder.cpp
        "// placeholder\n")
endif()

add_executable(chess_cli ${CLI_SOURCES})

target_include_directories(chess_cli
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(chess_cli
    PRIVATE chess_core
)
