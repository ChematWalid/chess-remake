#!/usr/bin/env bash
set -e

./build.sh

echo ""
echo ">>> Running core tests..."
./build/bin/chess_tests

echo ""
echo ">>> Running server & benchmark tests..."
./build/bin/chess_server_tests
