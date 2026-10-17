import asyncio
import json
import os
import urllib.request
import uuid

import websockets


BASE_URL = os.environ.get("CHESS_E2E_BASE_URL", "http://localhost:8080")
SOCKJS_BASE_URL = os.environ.get("CHESS_E2E_SOCKJS_URL", "ws://localhost:8080/ws/000")
ENGINE_URL = os.environ.get("CHESS_E2E_ENGINE_URL", "ws://localhost:9002")
FRONTEND_ORIGIN = os.environ.get("CHESS_E2E_ORIGIN", "http://localhost:5173")
PASSWORD = os.environ["CHESS_E2E_PASSWORD"]
WHITE_USERNAME = os.environ["CHESS_E2E_WHITE_USERNAME"]
BLACK_USERNAME = os.environ["CHESS_E2E_BLACK_USERNAME"]
GAME_MODE = os.environ.get("CHESS_E2E_GAME_MODE", "checkmate")


def post_json(path, payload, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE_URL + path, json.dumps(payload).encode(), headers, method="POST")
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read())


def get_json(path, token):
    request = urllib.request.Request(BASE_URL + path, headers={"Authorization": "Bearer " + token})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read())


def login(username):
    response = post_json("/api/auth/login", {"identifier": username, "password": PASSWORD})
    return response["data"]["accessToken"]


def stomp_frame(command, headers=None, body=""):
    lines = [command]
    lines.extend(f"{key}:{value}" for key, value in (headers or {}).items())
    return "\n".join(lines) + "\n\n" + body + "\0"


async def sockjs_connect(token):
    socket = await websockets.connect(SOCKJS_BASE_URL + "/" + str(uuid.uuid4()) + "/websocket",
                                      origin=FRONTEND_ORIGIN, open_timeout=10)
    if await asyncio.wait_for(socket.recv(), timeout=5) != "o":
        raise RuntimeError("SockJS transport did not open")
    connect = stomp_frame("CONNECT", {
        "accept-version": "1.2",
        "host": "localhost",
        "Authorization": "Bearer " + token,
    })
    await socket.send(json.dumps([connect]))
    await receive_frame(socket, "CONNECTED", 10)
    for index, destination in enumerate(("/user/topic/challenges", "/user/topic/errors", "/user/topic/presence")):
        await socket.send(json.dumps([stomp_frame("SUBSCRIBE", {
            "id": str(index), "destination": destination, "ack": "auto",
        })]))
    return socket


async def receive_frame(socket, command, timeout=10):
    async with asyncio.timeout(timeout):
        while True:
            packet = await socket.recv()
            if packet == "h":
                continue
            if not packet.startswith("a"):
                continue
            for frame in json.loads(packet[1:]):
                if frame.startswith(command + "\n") or frame == command:
                    return frame


async def receive_event(socket, timeout=10):
    frame = await receive_frame(socket, "MESSAGE", timeout)
    body = frame.split("\n\n", 1)[1].rstrip("\0")
    return json.loads(body)


async def send_stomp(socket, destination, payload):
    frame = stomp_frame("SEND", {
        "destination": destination,
        "content-type": "application/json",
    }, json.dumps(payload))
    await socket.send(json.dumps([frame]))


async def wait_for_event_type(socket, event_type, timeout=10):
    async with asyncio.timeout(timeout):
        while True:
            event = await receive_event(socket, timeout)
            if event.get("eventType") == event_type:
                return event
            if event.get("errorCode"):
                raise RuntimeError(f"Unexpected STOMP error: {event['errorCode']}")


async def wait_for_error(socket, error_code, timeout=10):
    async with asyncio.timeout(timeout):
        while True:
            event = await receive_event(socket, timeout)
            if event.get("errorCode") == error_code:
                return event
            if event.get("errorCode"):
                raise RuntimeError(f"Expected STOMP error {error_code}, received {event['errorCode']}")


async def run():
    white_token = login(WHITE_USERNAME)
    black_token = login(BLACK_USERNAME)
    history_before = get_json("/api/games/my-history?page=0&size=50", white_token)["data"]["totalElements"]
    white = await sockjs_connect(white_token)
    black = await sockjs_connect(black_token)
    try:
        await send_stomp(white, "/app/challenge.send", {
            "toUsername": WHITE_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 300,
            "incrementSeconds": 0,
        })
        await wait_for_error(white, "SELF_CHALLENGE", 5)
        print("STOMP self-challenge: typed error delivered; socket remains open")

        await send_stomp(white, "/app/challenge.send", {
            "toUsername": BLACK_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 4,
            "incrementSeconds": 0,
        })
        await wait_for_error(white, "VALIDATION_FAILED", 5)
        await send_stomp(white, "/app/challenge.send", {
            "toUsername": BLACK_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 300,
        })
        await wait_for_error(white, "INVALID_TIME_CONTROL", 5)
        await send_stomp(black, "/app/challenge.accept", {"challengeId": 9223372036854775807})
        await wait_for_error(black, "CHALLENGE_NOT_FOUND", 5)
        print("STOMP invalid time controls and unknown challenge: typed errors; sessions remained open")

        await send_stomp(white, "/app/challenge.send", {
            "toUsername": BLACK_USERNAME,
            "challengerSide": "WHITE",
            "initialTimeSeconds": 300,
            "incrementSeconds": 0,
        })
        invitation = await wait_for_event_type(black, "INVITATION_RECEIVED")
        challenge_id = invitation["challengeId"]
        await wait_for_event_type(white, "INVITATION_SENT")
        await send_stomp(black, "/app/challenge.accept", {"challengeId": challenge_id})
        white_match = await wait_for_event_type(white, "MATCH_ASSIGNED")
        black_match = await wait_for_event_type(black, "MATCH_ASSIGNED")
        for match in (white_match, black_match):
            assert "token" not in match or match["token"] is None, "Room tokens must not be broadcast over STOMP"
        white_detail = get_json(f"/api/challenges/{challenge_id}", white_token)["data"]
        black_detail = get_json(f"/api/challenges/{challenge_id}", black_token)["data"]
        assert white_detail["roomCode"] == black_detail["roomCode"] == white_match["roomCode"]
        assert white_detail["token"] and black_detail["token"]
        print(f"STOMP challenge {challenge_id}: public assignment delivered; private room tokens fetched over authenticated REST")

        white_socket = await websockets.connect(white_detail["connectUrl"], open_timeout=10)
        black_socket = await websockets.connect(black_detail["connectUrl"], open_timeout=10)
        try:
            async def wait_cpp(socket, event_name, timeout=10):
                async with asyncio.timeout(timeout):
                    while True:
                        message = json.loads(await socket.recv())
                        if message.get("event") == event_name:
                            return message
                        if message.get("event") == "error":
                            raise RuntimeError("C++ server rejected action: " + message.get("message", "unknown error"))

            async def wait_cpp_error(socket, timeout=5):
                async with asyncio.timeout(timeout):
                    while True:
                        message = json.loads(await socket.recv())
                        if message.get("event") == "error":
                            return message

            await white_socket.send(json.dumps({
                "action": "connect_token", "room_code": white_detail["roomCode"], "token": white_detail["token"],
            }))
            await wait_cpp(white_socket, "room_created")
            await black_socket.send(json.dumps({
                "action": "connect_token", "room_code": black_detail["roomCode"], "token": black_detail["token"],
            }))
            await wait_cpp(white_socket, "game_started")
            await wait_cpp(black_socket, "game_started")
            if GAME_MODE == "checkmate":
                await black_socket.send(json.dumps({"action": "move", "san": "e5"}))
                out_of_turn = await wait_cpp_error(black_socket)
                assert "turn" in out_of_turn.get("message", "").lower(), out_of_turn
                await white_socket.send(json.dumps({"action": "move", "san": "e9"}))
                illegal_move = await wait_cpp_error(white_socket)
                assert "move" in illegal_move.get("message", "").lower(), illegal_move
                print("C++ invalid and out-of-turn moves: error events returned; match remained playable")
                for socket, san in ((white_socket, "f3"), (black_socket, "e5"), (white_socket, "g4"), (black_socket, "Qh4#")):
                    await socket.send(json.dumps({"action": "move", "san": san}))
                    await asyncio.gather(wait_cpp(white_socket, "move_made"), wait_cpp(black_socket, "move_made"))
                expected_reason = "CHECKMATE"
            elif GAME_MODE == "resignation":
                await white_socket.send(json.dumps({"action": "resign"}))
                expected_reason = "RESIGNATION"
            elif GAME_MODE == "draw":
                await white_socket.send(json.dumps({"action": "offer_draw"}))
                await wait_cpp(black_socket, "draw_offered")
                await black_socket.send(json.dumps({"action": "accept_draw"}))
                expected_reason = "DRAW_AGREED"
            else:
                raise ValueError("CHESS_E2E_GAME_MODE must be checkmate, resignation, or draw")
            white_over, black_over = await asyncio.gather(
                wait_cpp(white_socket, "game_over"), wait_cpp(black_socket, "game_over"),
            )
            assert white_over.get("reason_code") == expected_reason, white_over
            if GAME_MODE == "checkmate":
                assert len(white_over.get("moves", [])) == 4, white_over
                print("C++ Fool's Mate: both players received 4 moves and CHECKMATE game-over")
            else:
                print(f"C++ {GAME_MODE}: both players received {expected_reason} game-over")
        finally:
            await white_socket.close()
            await black_socket.close()

        deadline = asyncio.get_running_loop().time() + 30
        saved_game = None
        while asyncio.get_running_loop().time() < deadline:
            history = get_json("/api/games/my-history?page=0&size=50", white_token)["data"]
            saved_game = next((game for game in history["items"]
                               if game["whiteUsername"] == WHITE_USERNAME
                               and game["blackUsername"] == BLACK_USERNAME
                               and game["terminationReason"] == expected_reason), None)
            if history["totalElements"] <= history_before:
                saved_game = None
            if saved_game is not None:
                break
            await asyncio.sleep(0.5)
        assert saved_game is not None, "Completed game was not persisted to history"
        details = get_json(f"/api/games/{saved_game['id']}", white_token)["data"]
        assert len(details["fens"]) == len(details["moves"]) + 1, details
        assert details["pgn"], details
        print(f"Game history persisted: game {saved_game['id']} has replay FENs, moves, and PGN")

        for credentials, expected in (({"action": "move", "san": "e4"}, "Not currently in a game room"),
                                      ({"action": "connect_token", "room_code": "invalid", "token": "invalid"}, "Invalid or expired token")):
            socket = await websockets.connect(ENGINE_URL + "/ws/play", open_timeout=10)
            try:
                await socket.send(json.dumps(credentials))
                result = json.loads(await asyncio.wait_for(socket.recv(), timeout=5))
                assert result.get("event") == "error" and expected in result.get("message", ""), result
            finally:
                await socket.close()
        print("C++ missing and invalid room credentials: rejected with error events")
    finally:
        await white.close()
        await black.close()


asyncio.run(run())
