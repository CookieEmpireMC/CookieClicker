"""Core logic for acting as the ``cookie-bakery`` GitHub App.

Standard library plus the ``openssl`` CLI only (no PyJWT / cryptography). Every step is a
small function so it can be tested on its own:

1. ``load_config``          read ``APP_ID``, ``INSTALLATION_ID``, ``KEY_PATH`` from the env
                            file, environment variables override the file
2. ``check_key_file``       the private key must exist, be a regular file owned by the
                            current user and have no group/other permissions (mode 600)
3. ``build_jwt``            RS256 App JWT (iat -60 s, exp +9 min, iss = App ID), signed by
                            ``openssl dgst -sha256 -sign``; Python never reads the key
4. ``create_installation_token``  ``POST /app/installations/<id>/access_tokens``
5. ``create_pull_request``  ``POST /repos/<owner>/<repo>/pulls`` with the installation token

The API base URL comes from ``GITHUB_API_URL`` (default ``https://api.github.com``) so tests
can point at a fake local server; plain ``http`` is only accepted for loopback hosts.

Secrets: the installation token is only ever returned to the caller. It is never written to
disk or logged, and every error message is scrubbed of the JWT and the token.
"""

import base64
import json
import os
import re
import stat
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_CONFIG_PATH = os.path.join("~", ".config", "cookieclicker", "github-app.env")
CONFIG_ENV_VAR = "GITHUB_APP_CONFIG"
API_URL_ENV_VAR = "GITHUB_API_URL"
DEFAULT_API_URL = "https://api.github.com"
DEFAULT_REPO = "CookieEmpireMC/CookieClicker"
CONFIG_KEYS = ("APP_ID", "INSTALLATION_ID", "KEY_PATH")

JWT_BACKDATE_SECONDS = 60       # iat: allow for clock drift
JWT_LIFETIME_SECONDS = 9 * 60   # exp: GitHub allows at most 10 minutes
HTTP_TIMEOUT_SECONDS = 30
API_VERSION = "2022-11-28"
USER_AGENT = "cookieclicker-github-app"
LOOPBACK_HOSTS = ("localhost", "127.0.0.1", "::1")
REPO_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
MAX_REMOTE_MESSAGE = 300


class GitHubAppError(Exception):
    """Base class for every expected failure; the message is safe to show."""


class ConfigError(GitHubAppError):
    """Missing or invalid settings."""


class KeyFileError(GitHubAppError):
    """Private key missing, unsafe or unusable."""


class NetworkError(GitHubAppError):
    """The API could not be reached."""


class ApiError(GitHubAppError):
    """The API answered with an error status."""

    def __init__(self, message, status=None, remote_message="", remote_errors=()):
        super().__init__(message)
        self.status = status
        self.remote_message = remote_message
        self.remote_errors = list(remote_errors)


class PullRequestExistsError(ApiError):
    """A pull request for this head branch is already open."""


class Config:
    """Resolved App settings. ``sources`` maps each key to where its value came from."""

    def __init__(self, app_id, installation_id, key_path, sources=None):
        self.app_id = app_id
        self.installation_id = installation_id
        self.key_path = key_path
        self.sources = dict(sources or {})

    def __repr__(self):
        return (f"Config(app_id={self.app_id!r}, installation_id={self.installation_id!r}, "
                f"key_path={self.key_path!r})")


# --- config -----------------------------------------------------------------------------

def parse_env_file(text):
    """Parse ``KEY=VALUE`` lines. Blank lines, ``#`` comments, an ``export`` prefix and
    matching single/double quotes around the value are allowed. Raises ConfigError on a
    malformed line (reported by line number only, never by content)."""
    values = {}
    for number, raw in enumerate(text.splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):].lstrip()
        key, sep, value = line.partition("=")
        key = key.strip()
        if not sep or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            raise ConfigError(f"config line {number} is not KEY=VALUE")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key] = value
    return values


def resolve_config_path(path=None, environ=None):
    """Return ``(path, explicit)``: ``path`` argument > ``$GITHUB_APP_CONFIG`` > default."""
    environ = os.environ if environ is None else environ
    if path:
        return os.path.expanduser(path), True
    if environ.get(CONFIG_ENV_VAR):
        return os.path.expanduser(environ[CONFIG_ENV_VAR]), True
    return os.path.expanduser(DEFAULT_CONFIG_PATH), False


def load_config(path=None, environ=None):
    """Load settings from the env file, then let environment variables override them.

    The default file may be absent when the environment supplies all three keys; a file
    named explicitly (argument or ``$GITHUB_APP_CONFIG``) must exist. A relative
    ``KEY_PATH`` from the file is resolved against the file's directory."""
    environ = os.environ if environ is None else environ
    config_path, explicit = resolve_config_path(path, environ)

    file_values = {}
    if os.path.isfile(config_path):
        try:
            with open(config_path, encoding="utf-8") as fh:
                file_values = parse_env_file(fh.read())
        except OSError as exc:
            raise ConfigError(f"cannot read config file {config_path}: {exc.strerror}") from None
        except ConfigError as exc:
            raise ConfigError(f"{config_path}: {exc}") from None
    elif explicit or os.path.exists(config_path):
        raise ConfigError(f"config file not found: {config_path}")

    values, sources = {}, {}
    for key in CONFIG_KEYS:
        if environ.get(key):
            values[key], sources[key] = environ[key].strip(), "environment"
        elif file_values.get(key):
            values[key], sources[key] = file_values[key], config_path

    missing = [key for key in CONFIG_KEYS if not values.get(key)]
    if missing:
        where = config_path if file_values or os.path.isfile(config_path) else \
            f"{config_path} (not found)"
        raise ConfigError(
            f"missing {', '.join(missing)}: set them in {where} or as environment variables "
            f"(format: one KEY=VALUE per line for {', '.join(CONFIG_KEYS)})")

    for key in ("APP_ID", "INSTALLATION_ID"):
        if not values[key].isdigit():
            raise ConfigError(f"{key} must be a number (from {sources[key]})")

    key_path = os.path.expanduser(values["KEY_PATH"])
    if not os.path.isabs(key_path) and sources["KEY_PATH"] == config_path:
        key_path = os.path.join(os.path.dirname(os.path.abspath(config_path)), key_path)
    return Config(values["APP_ID"], values["INSTALLATION_ID"], os.path.abspath(key_path), sources)


# --- private key ------------------------------------------------------------------------

def check_key_file(key_path):
    """Refuse a key that is missing, not a regular file, owned by someone else, or readable
    or writable by group/other. Only metadata is inspected; the key is never read here."""
    try:
        info = os.stat(key_path)
    except FileNotFoundError:
        raise KeyFileError(f"private key not found: {key_path}") from None
    except OSError as exc:
        raise KeyFileError(f"cannot access private key {key_path}: {exc.strerror}") from None
    if not stat.S_ISREG(info.st_mode):
        raise KeyFileError(f"private key is not a regular file: {key_path}")
    if hasattr(os, "geteuid") and info.st_uid != os.geteuid():
        raise KeyFileError(f"private key is not owned by the current user: {key_path}")
    mode = stat.S_IMODE(info.st_mode)
    if mode & 0o077:
        raise KeyFileError(
            f"private key {key_path} has unsafe permissions {mode:03o}; "
            f"run: chmod 600 {key_path}")
    if not mode & stat.S_IRUSR:
        raise KeyFileError(f"private key is not readable by its owner: {key_path}")
    return key_path


# --- JWT --------------------------------------------------------------------------------

def b64url(data):
    """Base64url without padding (RFC 7515)."""
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def b64url_decode(text):
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def jwt_header():
    return {"alg": "RS256", "typ": "JWT"}


def jwt_claims(app_id, now=None):
    """App JWT claims: issued 60 s in the past, expires 9 min from now, issuer = App ID."""
    now = int(time.time() if now is None else now)
    return {"iat": now - JWT_BACKDATE_SECONDS, "exp": now + JWT_LIFETIME_SECONDS,
            "iss": str(app_id)}


def _compact_json(value):
    return json.dumps(value, separators=(",", ":"), sort_keys=True).encode("utf-8")


def signing_input(claims, header=None):
    """``base64url(header) + "." + base64url(claims)`` as bytes."""
    header = jwt_header() if header is None else header
    return f"{b64url(_compact_json(header))}.{b64url(_compact_json(claims))}".encode("ascii")


def sign_rs256(data, key_path, openssl="openssl"):
    """RSASSA-PKCS1-v1_5 SHA-256 signature of ``data``, made by the openssl CLI."""
    try:
        result = subprocess.run(
            [openssl, "dgst", "-sha256", "-sign", key_path, "-binary"],
            input=data, capture_output=True, timeout=30, check=False)
    except FileNotFoundError:
        raise KeyFileError(f"openssl not found ({openssl}); it is needed to sign the JWT") from None
    except subprocess.TimeoutExpired:
        raise KeyFileError("openssl timed out while signing the JWT") from None
    if result.returncode != 0 or not result.stdout:
        raise KeyFileError(
            f"openssl could not sign with {key_path} (exit {result.returncode}); "
            "is it the App's RSA private key in PEM format?")
    return result.stdout


def build_jwt(app_id, key_path, now=None, openssl="openssl"):
    """Signed App JWT. The key file is checked first (see ``check_key_file``)."""
    check_key_file(key_path)
    data = signing_input(jwt_claims(app_id, now))
    return f"{data.decode('ascii')}.{b64url(sign_rs256(data, key_path, openssl))}"


# --- HTTP -------------------------------------------------------------------------------

def api_base_url(environ=None):
    """``$GITHUB_API_URL`` or ``https://api.github.com``, without trailing slash. Plain
    http is accepted only for loopback hosts (fake servers in tests)."""
    environ = os.environ if environ is None else environ
    url = (environ.get(API_URL_ENV_VAR) or DEFAULT_API_URL).strip().rstrip("/")
    parts = urllib.parse.urlsplit(url)
    if parts.scheme == "https" and parts.hostname:
        return url
    if parts.scheme == "http" and parts.hostname in LOOPBACK_HOSTS:
        return url
    raise ConfigError(
        f"{API_URL_ENV_VAR} must be an https URL (http only for localhost), got {url!r}")


def redact(text, secrets):
    """Replace every non-empty secret in ``text`` with ``***``."""
    for secret in secrets:
        if secret:
            text = text.replace(secret, "***")
    return text


def _remote_details(raw):
    """``(message, [error messages])`` from a GitHub error body, tolerant of non-JSON."""
    try:
        payload = json.loads(raw.decode("utf-8", "replace") or "{}")
    except ValueError:
        return "", []
    if not isinstance(payload, dict):
        return "", []
    message = str(payload.get("message") or "")
    errors = []
    for item in payload.get("errors") or []:
        if isinstance(item, dict):
            text = item.get("message") or " ".join(
                str(item[k]) for k in ("resource", "field", "code") if item.get(k))
            if text:
                errors.append(str(text))
        elif item:
            errors.append(str(item))
    return message, errors


def api_request(method, url, auth, body=None, secrets=(), timeout=HTTP_TIMEOUT_SECONDS):
    """Send a JSON request with ``Authorization: Bearer <auth>``; return ``(status, json)``.

    Raises ApiError (with ``status``, ``remote_message``, ``remote_errors``) on an HTTP
    error status and NetworkError when the server can't be reached. Messages are redacted
    of ``auth`` and ``secrets``."""
    secrets = (auth, *secrets)
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {auth}",
        "X-GitHub-Api-Version": API_VERSION,
        "User-Agent": USER_AGENT,
        **({"Content-Type": "application/json"} if data is not None else {}),
    })
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as exc:
        try:
            raw = exc.read()
        except OSError:
            raw = b""
        message, errors = _remote_details(raw)
        message = redact(message[:MAX_REMOTE_MESSAGE], secrets)
        errors = [redact(e[:MAX_REMOTE_MESSAGE], secrets) for e in errors]
        detail = "; ".join(filter(None, [message, *errors]))
        raise ApiError(f"{method} {url} failed with HTTP {exc.code}"
                       + (f": {detail}" if detail else ""),
                       status=exc.code, remote_message=message, remote_errors=errors) from None
    except urllib.error.URLError as exc:
        raise NetworkError(f"cannot reach {url}: {redact(str(exc.reason), secrets)}") from None
    except (TimeoutError, OSError) as exc:
        raise NetworkError(f"cannot reach {url}: {redact(str(exc), secrets)}") from None
    try:
        payload = json.loads(raw.decode("utf-8") or "null")
    except ValueError:
        raise ApiError(f"{method} {url} returned HTTP {status} with a non-JSON body",
                       status=status) from None
    return status, payload


def _said(exc):
    detail = "; ".join(filter(None, [exc.remote_message, *exc.remote_errors]))
    return f" (GitHub said: {detail})" if detail else ""


# --- installation token -----------------------------------------------------------------

def create_installation_token(config, jwt, base_url):
    """Exchange the App JWT for an installation access token and return the token."""
    url = f"{base_url}/app/installations/{config.installation_id}/access_tokens"
    try:
        _, payload = api_request("POST", url, jwt)
    except ApiError as exc:
        if exc.status == 401:
            message = (f"GitHub rejected the App JWT (HTTP 401): check APP_ID ({config.app_id}) "
                       "and that KEY_PATH is this App's private key, and that the clock is right")
        elif exc.status == 404:
            message = (f"installation {config.installation_id} not found for App "
                       f"{config.app_id} (HTTP 404): check INSTALLATION_ID")
        elif exc.status == 403:
            message = "GitHub refused the token request (HTTP 403)"
        else:
            raise
        raise ApiError(message + _said(exc), status=exc.status,
                       remote_message=exc.remote_message, remote_errors=exc.remote_errors) from None
    token = payload.get("token") if isinstance(payload, dict) else None
    if not isinstance(token, str) or not token.strip():
        raise ApiError("token response from GitHub has no token")
    return token.strip()


def get_installation_token(config_path=None, environ=None, now=None, openssl="openssl"):
    """Full flow: config -> key check -> JWT -> installation token (returned, never stored)."""
    environ = os.environ if environ is None else environ
    config = load_config(config_path, environ)
    base_url = api_base_url(environ)
    jwt = build_jwt(config.app_id, config.key_path, now=now, openssl=openssl)
    return create_installation_token(config, jwt, base_url)


# --- pull request -----------------------------------------------------------------------

def validate_repo(repo):
    if not REPO_PATTERN.fullmatch(repo or "") or any(
            segment in (".", "..") for segment in repo.split("/")):
        raise ConfigError(f"--repo must look like owner/name, got {repo!r}")
    return repo


def pull_request_payload(head, base, title, body, draft=False):
    if not head or not head.strip():
        raise ConfigError("--head must not be empty")
    if not base or not base.strip():
        raise ConfigError("--base must not be empty")
    if not title or not title.strip():
        raise ConfigError("--title must not be empty")
    return {"title": title, "head": head, "base": base, "body": body, "draft": bool(draft)}


def create_pull_request(token, repo, head, base, title, body, draft=False, base_url=None):
    """Open a PR as the App. Returns ``{"url", "number", "author"}``."""
    base_url = api_base_url() if base_url is None else base_url
    validate_repo(repo)
    payload = pull_request_payload(head, base, title, body, draft)
    url = f"{base_url}/repos/{repo}/pulls"
    try:
        _, result = api_request("POST", url, token, body=payload)
    except ApiError as exc:
        texts = " ".join([exc.remote_message, *exc.remote_errors]).lower()
        if exc.status == 422 and "already exists" in texts:
            raise PullRequestExistsError(
                f"a pull request for head '{head}' already exists in {repo}; "
                "update that PR instead of opening a new one" + _said(exc),
                status=422, remote_message=exc.remote_message,
                remote_errors=exc.remote_errors) from None
        if exc.status == 422:
            message = (f"GitHub refused the pull request {head} -> {base} (HTTP 422); "
                       "check that both branches exist on GitHub and differ")
        elif exc.status == 403:
            message = (f"the App may not open pull requests in {repo} (HTTP 403): it needs "
                       "'Pull requests: write' on this repository")
        elif exc.status == 404:
            message = (f"repository {repo} not found or the App is not installed on it "
                       "(HTTP 404)")
        elif exc.status == 401:
            message = "GitHub rejected the installation token (HTTP 401)"
        else:
            raise
        raise ApiError(message + _said(exc), status=exc.status,
                       remote_message=exc.remote_message, remote_errors=exc.remote_errors) from None
    if not isinstance(result, dict):
        raise ApiError("pull request response from GitHub is not a JSON object")
    user = result.get("user") if isinstance(result.get("user"), dict) else {}
    return {"url": result.get("html_url") or result.get("url") or "",
            "number": result.get("number"),
            "author": user.get("login") or ""}
