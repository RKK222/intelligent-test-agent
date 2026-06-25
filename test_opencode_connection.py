
import requests

base_url = "http://127.0.0.1:4096"

print(f"Testing connection to: {base_url}")

try:
    # Test 1: Simple GET to baseUrl (like health check)
    print("\n--- Test 1: Simple GET ---")
    response1 = requests.get(base_url, timeout=3)
    print(f"Status: {response1.status_code}")
    
    # Test 2: Test with /api/agent path (like the actual API call)
    print("\n--- Test 2: GET /api/agent ---")
    response2 = requests.get(f"{base_url}/api/agent", timeout=3)
    print(f"Status: {response2.status_code}")
    print(f"Response: {response2.text[:200]}...")
    
    # Test 3: Test with a workspaceId parameter
    print("\n--- Test 3: GET /api/agent?workspaceId=test ---")
    response3 = requests.get(f"{base_url}/api/agent", params={"workspaceId": "test"}, timeout=3)
    print(f"Status: {response3.status_code}")
    print(f"Response: {response3.text[:200]}...")
    
except requests.exceptions.RequestException as e:
    print(f"\nError: {type(e).__name__}: {e}")
    import traceback
    traceback.print_exc()
