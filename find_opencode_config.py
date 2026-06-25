
import os

# 尝试找到 opencode 的配置目录
print("=== 查找 opencode 配置文件 ===")

# Windows 常见配置位置
user_home = os.path.expanduser("~")
possible_paths = [
    os.path.join(user_home, ".opencode", "config.json"),
    os.path.join(user_home, "AppData", "Roaming", "opencode", "config.json"),
    os.path.join(user_home, ".config", "opencode", "config.json"),
]

for path in possible_paths:
    print(f"\n检查: {path}")
    if os.path.exists(path):
        print(f"✅ 找到配置文件: {path}")
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
            print(f"\n内容: {content}")
    else:
        print(f"❌ 不存在")

print(f"\n\n你的用户目录: {user_home}")
