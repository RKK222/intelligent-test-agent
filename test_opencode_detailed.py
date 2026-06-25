
import requests

base_url = "http://127.0.0.1:4096"
workspace_id = "wrk_fcoss_20260620"  # 从你的错误信息中获取

print(f"=== 测试 opencode 服务在 {base_url} ===")

# 测试 1: 简单 GET 根路径
print("\n1. 测试根路径 GET:")
try:
    response = requests.get(base_url, timeout=5)
    print(f"   状态码: {response.status_code}")
    print(f"   响应头: {dict(response.headers)}")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")

# 测试 2: GET /api/agent （不带参数）
print("\n2. 测试 /api/agent (不带参数):")
try:
    response = requests.get(f"{base_url}/api/agent", timeout=5)
    print(f"   状态码: {response.status_code}")
    print(f"   响应: {response.text[:200]}...")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")

# 测试 3: GET /api/agent?workspaceId=... 
print("\n3. 测试 /api/agent?workspaceId=...:")
try:
    response = requests.get(f"{base_url}/api/agent", params={"workspaceId": workspace_id}, timeout=5)
    print(f"   状态码: {response.status_code}")
    print(f"   响应: {response.text[:200]}...")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")

# 测试 4: 加上 directory 参数（后端会加这个）
print("\n4. 测试 /api/agent?directory=... (模拟后端调用):")
try:
    # 后端通常会传入 directory 参数
    response = requests.get(f"{base_url}/api/agent", params={
        "directory": "C:\\Users\\73111",  # 从之前的测试结果推测
        "workspaceId": workspace_id
    }, timeout=5)
    print(f"   状态码: {response.status_code}")
    print(f"   响应: {response.text[:200]}...")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")

# 测试 5: 尝试 /global/health（健康检查）
print("\n5. 测试 /global/health (健康检查):")
try:
    response = requests.get(f"{base_url}/global/health", timeout=5)
    print(f"   状态码: {response.status_code}")
    print(f"   响应: {response.text[:200]}...")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")
