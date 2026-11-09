import socket
from pathlib import Path

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", 8000))
    server.listen(1)

    print("Waiting for Android request...")

    connection, address = server.accept()

    with connection:
        connection.settimeout(10)
        request = b""

        while b"\r\n\r\n" not in request:
            chunk = connection.recv(4096)
            if not chunk:
                break
            request += chunk

        text = request.decode("utf-8", errors="replace")
        Path("android_request.txt").write_text(
            text,
            encoding="utf-8",
        )

        print(text)

        connection.sendall(
            b"HTTP/1.1 200 OK\r\n"
            b"Content-Type: application/json\r\n"
            b"Content-Length: 2\r\n"
            b"Connection: close\r\n"
            b"\r\n"
            b"[]"
        )

print("Saved: android_request.txt")