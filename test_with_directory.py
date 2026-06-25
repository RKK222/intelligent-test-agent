
import requests

base_url = "http://127.0.0.1:4096"
workspace_id = "wrk_fcoss_20260620"

print(f"=== 测试带 directory 参数的 API 调用 ===")

# 测试：用数据库里的路径（Linux 风格）
directory = "/tmp/test-agent/fcoss/20260620"
print(f"\n1. 测试 directory: {directory}")
try:
    response = requests.get(
        f"{base_url}/api/agent",
        params={"directory": directory, "workspaceId": workspace_id},
        timeout=5
    )
    print(f"   状态码: {response.status_code}")
    print(f"   响应: {response.text[:300]}")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")
    import traceback
    traceback.print_exc()

# 测试：用一个存在的 Windows 路径
print(f"\n2. 测试用存在的 Windows 路径")
try:
    response = requests.get(
        f"{base_url}/api/agent",
        params={"directory": "D:\\workspace", "workspaceId": workspace_id},
        timeout=5
    )
    print(f"   状态码: {response.status_code}")
    print(f"   响应: {response.text[:300]}")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")
    import traceback
    traceback.print_exc()

# 测试：不带 directory 参数（只带 workspaceId）
print(f"\n3. 测试不带 directory 参数")
try:
    response = requests.get(
        f"{base_url}/api/agent",
        params={"workspaceId": workspace_id},
        timeout=5
    )
    print(f"   状态码: {response.status_code}")
    print(f"   响应: {response.text[:300]}")
except Exception as e:
    print(f"   错误: {type(e).__name__}: {e}")
    import traceback
    traceback.print_exc()
