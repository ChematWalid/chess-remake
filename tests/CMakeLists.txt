enable_testing()

# ── core tests ────────────────────────────────────────────────────────────────
file(GLOB_RECURSE CORE_TEST_SOURCES CONFIGURE_DEPENDS core/*.cpp)

add_executable(chess_tests ${CORE_TEST_SOURCES})

target_link_libraries(chess_tests
    PRIVATE chess_core
            GTest::gtest_main
)

include(GoogleTest)
gtest_discover_tests(chess_tests PROPERTIES WORKING_DIRECTORY ${PROJECT_SOURCE_DIR})

# ── server tests ──────────────────────────────────────────────────────────────
file(GLOB_RECURSE SERVER_TEST_SOURCES CONFIGURE_DEPENDS server/*.cpp)

if(SERVER_TEST_SOURCES)
    add_executable(chess_server_tests ${SERVER_TEST_SOURCES})

    target_link_libraries(chess_server_tests
        PRIVATE chess_server_lib
                GTest::gtest_main
    )

    gtest_discover_tests(chess_server_tests PROPERTIES WORKING_DIRECTORY ${PROJECT_SOURCE_DIR})
endif()

# ── review harnesses ──────────────────────────────────────────────────────────
add_executable(review_perft review/perft.cpp)
target_link_libraries(review_perft PRIVATE chess_core)

add_executable(review_probes review/probes.cpp)
target_link_libraries(review_probes PRIVATE chess_core)

add_executable(review_net_probes review/net_probes.cpp)
target_link_libraries(review_net_probes PRIVATE chess_server_lib)

add_executable(review_net_probes2 review/net_probes2.cpp)
target_link_libraries(review_net_probes2 PRIVATE chess_server_lib)
