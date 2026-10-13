# core/ — the pure chess library (no I/O, no networking)

# Collect sources automatically; add new .cpp files and they are picked up
file(GLOB_RECURSE CORE_SOURCES CONFIGURE_DEPENDS src/*.cpp)

# CMake requires at least one source file for a static library.
# The placeholder is compiled away once real sources exist.
if(NOT CORE_SOURCES)
    set(CORE_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/src/_placeholder.cpp)
    file(WRITE ${CMAKE_CURRENT_SOURCE_DIR}/src/_placeholder.cpp
        "// auto-generated placeholder — safe to delete once real sources exist\n")
endif()

add_library(chess_core STATIC ${CORE_SOURCES})

target_include_directories(chess_core
    PUBLIC  ${CMAKE_CURRENT_SOURCE_DIR}/include   # consumers: #include "chess/Foo.h"
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src
)
