from __future__ import annotations

import base64
import hashlib
import hmac
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
import pytest

from testagent_runner.credentials import RunnerCredentialDecryptor
from testagent_runner.security import (
    InMemoryNonceStore,
    RunnerHmacAuthenticator,
    RunnerRequestError,
    SqliteNonceStore,
)


def test_runner_hmac_rejects_replay_and_body_tampering() -> None:
    secret = b"0123456789abcdef0123456789abcdef"
    authenticator = RunnerHmacAuthenticator(
        secret,
        "runner-a",
        InMemoryNonceStore(),
        clock=lambda: 1_800_000_000,
    )
    body = b'{"runId":"run_12345678"}'
    path = "/runner-api/v1/tasks/task_12345678/prepare"
    digest = hashlib.sha256(body).hexdigest()
    canonical = "\n".join(
        ["POST", path, digest, "runner-a", "1800000000", "nonce_1234567890"]
    )
    headers = {
        "x-workflow-runner-id": "runner-a",
        "x-workflow-timestamp": "1800000000",
        "x-workflow-nonce": "nonce_1234567890",
        "x-workflow-body-sha256": digest,
        "x-workflow-signature": hmac.new(secret, canonical.encode(), hashlib.sha256).hexdigest(),
    }

    authenticator.verify("POST", path, body, headers)
    with pytest.raises(RunnerRequestError, match="重放"):
        authenticator.verify("POST", path, body, headers)

    changed = {**headers, "x-workflow-nonce": "nonce_changed_1234"}
    with pytest.raises(RunnerRequestError, match="摘要"):
        authenticator.verify("POST", path, b"{}", changed)


@pytest.mark.parametrize(
    "nonce",
    [
        "short",
        "n" * 129,
        "nonce_1234567890\nforged",
        "nonce_1234567890/forged",
    ],
)
def test_runner_hmac_rejects_nonce_outside_canonical_contract(nonce: str) -> None:
    authenticator = RunnerHmacAuthenticator(
        b"0123456789abcdef0123456789abcdef",
        "runner-a",
        InMemoryNonceStore(),
        clock=lambda: 1_800_000_000,
    )

    with pytest.raises(RunnerRequestError, match="nonce"):
        authenticator.verify(
            "POST",
            "/runner-api/v1/tasks/task_12345678/prepare",
            b"{}",
            {
                "x-workflow-runner-id": "runner-a",
                "x-workflow-timestamp": "1800000000",
                "x-workflow-nonce": nonce,
                "x-workflow-body-sha256": hashlib.sha256(b"{}").hexdigest(),
                "x-workflow-signature": "0" * 64,
            },
        )


def test_checkout_private_key_is_decrypted_only_in_credential_directory(tmp_path: Path) -> None:
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    private_path = tmp_path / "runner-private.pem"
    private_path.write_bytes(
        private.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    private_path.chmod(0o600)
    plaintext = b"-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n"
    encrypted = private.public_key().encrypt(
        plaintext,
        padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
    )
    credential_root = tmp_path / "credentials"
    decryptor = RunnerCredentialDecryptor(private_path, credential_root, require_tmpfs=False)

    with decryptor.materialize(base64.b64encode(encrypted).decode()) as key_path:
        assert key_path.parent == credential_root
        assert key_path.read_bytes() == plaintext
        assert key_path.stat().st_mode & 0o777 == 0o600

    assert list(credential_root.iterdir()) == []


def test_runner_rejects_private_key_readable_by_group_or_others(tmp_path: Path) -> None:
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    private_path = tmp_path / "runner-private.pem"
    private_path.write_bytes(
        private.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    private_path.chmod(0o644)

    with pytest.raises(Exception, match="权限"):
        RunnerCredentialDecryptor(
            private_path,
            tmp_path / "credentials",
            require_tmpfs=False,
        )


def test_nonce_replay_remains_blocked_after_runner_process_restart(tmp_path: Path) -> None:
    path = tmp_path / "runner-nonces.sqlite"

    assert SqliteNonceStore(path, clock=lambda: 100).reserve("nonce-persisted", 200) is True
    assert SqliteNonceStore(path, clock=lambda: 101).reserve("nonce-persisted", 201) is False
