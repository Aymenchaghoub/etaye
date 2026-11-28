"""Export the opt-in Compose inbox to a private, short-lived local browser file."""

import argparse
from pathlib import Path
import subprocess
import tempfile
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", default="http://localhost:4179")
    args = parser.parse_args()
    try:
        origin = urlsplit(args.origin)
        port = origin.port
    except ValueError:
        parser.error("Use an HTTP loopback origin with a port.")
    if (origin.scheme != "http" or origin.hostname not in {"localhost", "127.0.0.1"}
            or port is None or origin.netloc != f"{origin.hostname}:{port}"
            or origin.path not in {"", "/"} or origin.query or origin.fragment):
        parser.error("Use an HTTP loopback origin with a port.")

    # Docker access is the operator boundary. The proxy and published sockets never see this request.
    compose_file = Path(__file__).resolve().with_name("compose.yml")
    reply = subprocess.run(
        ["docker", "compose", "-f", str(compose_file), "exec", "-T", "api",
         "bash", "-ec", (
             "exec 3<>/dev/tcp/127.0.0.1/8025\n"
             "printf 'GET / HTTP/1.1\\r\\nHost: 127.0.0.1:8025\\r\\nConnection: close\\r\\n\\r\\n' >&3\n"
             "cat <&3"
         )],
        capture_output=True, timeout=10, check=False,
    )
    headers, _, body = reply.stdout.partition(b"\r\n\r\n")
    if reply.returncode or not headers.startswith(b"HTTP/1.1 200 ") or not body.startswith(b"<!doctype html>"):
        parser.exit(1, "Local recovery inbox unavailable. Check the Compose project and opt-in flag.\n")

    # Preserve the inbox-rendered document and adjust only its standalone development origin.
    target = f"http://{origin.hostname}:{port}/recuperation/nouveau#".encode()
    with tempfile.NamedTemporaryFile(prefix="etaye-recovery-", suffix=".html", delete=False) as output:
        output.write(body.replace(b"http://localhost:5173/recuperation/nouveau#", target))
    print(Path(output.name).as_uri())


if __name__ == "__main__":
    main()
