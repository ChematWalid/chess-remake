#!/usr/bin/env bash
# test.sh — build then run all tests with clean output
set -e

./build.sh

echo ""
echo ">>> Running core tests..."
./build/bin/chess_tests

echo ""
echo ">>> Running server & benchmark tests..."
./build/bin/chess_server_tests
