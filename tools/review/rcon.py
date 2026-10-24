"""Minimal Minecraft RCON client (Source RCON protocol), for the local review server only."""
import socket
import struct

AUTH = 3
COMMAND = 2


class RconError(Exception):
    pass


class Rcon:
    """One authenticated RCON connection. Commands run one at a time; replies over 4 KB are not reassembled."""

    def __init__(self, host="127.0.0.1", port=25575, password="review", timeout=10.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(60.0)
        self.n = 0
        self.n_auth = self._next()
        self._write(self.n_auth, AUTH, password)
        req, _ = self._reply()
        if req == -1:
            raise RconError("RCON password rejected")

    def _next(self):
        self.n += 1
        return self.n

    def _write(self, req, kind, body):
        payload = struct.pack("<ii", req, kind) + body.encode("utf-8") + b"\0\0"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _reply(self):
        length = struct.unpack("<i", self._read(4))[0]
        data = self._read(length)
        req = struct.unpack("<i", data[:4])[0]
        return req, data[8:-2].decode("utf-8", errors="replace")

    def _read(self, n):
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise RconError("RCON connection closed")
            data += chunk
        return data

    def cmd(self, command):
        self._write(self._next(), COMMAND, command)
        return self._reply()[1]

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()


def reachable(host, port, password):
    """True when an RCON login works (the review server is up)."""
    try:
        with Rcon(host, port, password, timeout=2.0):
            return True
    except (OSError, RconError):
        return False
