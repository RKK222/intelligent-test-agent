#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Update one encrypted DB password without printing plaintext."""
import argparse
import configparser
from pathlib import Path
from cryptography.fernet import Fernet

BASE = Path(__file__).resolve().parent.parent


def main():
    p = argparse.ArgumentParser()
    p.add_argument("env", choices=["jc2", "jc4", "jc6"])
    p.add_argument("--password", required=True)
    p.add_argument("--config", default=str(BASE / "config" / "database.ini"))
    p.add_argument("--key-file", default=str(BASE / "config" / ".encryption.key"))
    a = p.parse_args()
    cfg_path = Path(a.config)
    key_path = Path(a.key_file)
    if not key_path.exists():
        key_path.parent.mkdir(parents=True, exist_ok=True)
        key_path.write_bytes(Fernet.generate_key())
    cipher = Fernet(key_path.read_bytes())
    cfg = configparser.ConfigParser(); cfg.read(cfg_path, encoding="utf-8")
    if a.env not in cfg:
        raise SystemExit(f"配置中不存在 [{a.env}]")
    cfg[a.env]["password"] = cipher.encrypt(a.password.encode()).decode()
    with cfg_path.open("w", encoding="utf-8") as f:
        cfg.write(f)
    print(f"已更新 {a.env} 的加密密码")

if __name__ == "__main__":
    main()
