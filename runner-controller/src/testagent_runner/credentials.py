"""一次性checkout ticket中的SSH私钥解封与tmpfs生命周期。"""

from __future__ import annotations

import base64
from contextlib import contextmanager
import os
from pathlib import Path
import tempfile
from collections.abc import Iterator

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa


class RunnerCredentialError(RuntimeError):
    pass


class RunnerCredentialDecryptor:
    def __init__(
        self,
        private_key_path: Path,
        credential_root: Path,
        *,
        require_tmpfs: bool = True,
    ) -> None:
        if private_key_path.stat().st_mode & 0o077:
            raise RunnerCredentialError("Runner私钥文件权限禁止授予group或other")
        loaded = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
        if not isinstance(loaded, rsa.RSAPrivateKey):
            raise RunnerCredentialError("Runner必须使用RSA私钥")
        self._private_key = loaded
        self._credential_root = credential_root.resolve()
        self._credential_root.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self._credential_root, 0o700)
        if require_tmpfs and not _is_tmpfs(self._credential_root):
            raise RunnerCredentialError("Runner凭据目录必须挂载为tmpfs")

    def public_key_pem(self) -> str:
        return self._private_key.public_key().public_bytes(
            serialization.Encoding.PEM,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode()

    @contextmanager
    def materialize(self, encrypted_private_key: str | None) -> Iterator[Path | None]:
        if not encrypted_private_key:
            yield None
            return
        try:
            plaintext = self._private_key.decrypt(
                base64.b64decode(encrypted_private_key, validate=True),
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None,
                ),
            )
        except Exception as exception:
            raise RunnerCredentialError("checkout凭据无法解封") from exception
        descriptor, raw_path = tempfile.mkstemp(prefix="checkout-", dir=self._credential_root)
        key_path = Path(raw_path)
        try:
            os.fchmod(descriptor, 0o600)
            with os.fdopen(descriptor, "wb", closefd=True) as stream:
                stream.write(plaintext)
                stream.flush()
                os.fsync(stream.fileno())
            yield key_path
        finally:
            plaintext = b"\0" * len(plaintext)
            if key_path.exists():
                try:
                    size = key_path.stat().st_size
                    with key_path.open("r+b", buffering=0) as stream:
                        stream.write(b"\0" * size)
                        stream.flush()
                        os.fsync(stream.fileno())
                finally:
                    key_path.unlink(missing_ok=True)


def _is_tmpfs(path: Path) -> bool:
    mountinfo = Path("/proc/self/mountinfo")
    if not mountinfo.exists():
        return False
    best_match: tuple[int, str] | None = None
    for line in mountinfo.read_text(errors="replace").splitlines():
        before, separator, after = line.partition(" - ")
        if not separator:
            continue
        fields = before.split()
        filesystem = after.split()[0] if after.split() else ""
        if len(fields) < 5:
            continue
        mountpoint = Path(fields[4].replace("\\040", " "))
        try:
            path.relative_to(mountpoint)
        except ValueError:
            continue
        candidate = (len(str(mountpoint)), filesystem)
        if best_match is None or candidate[0] > best_match[0]:
            best_match = candidate
    return best_match is not None and best_match[1] == "tmpfs"
