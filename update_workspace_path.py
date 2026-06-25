
import psycopg2

db_config = {
    'host': '192.168.100.194',
    'port': '5432',
    'database': 'test_agent',
    'user': 'testagent',
    'password': 'testagent'
}

print("=== 修改 workspace 路径为 Windows 路径 ===")

try:
    conn = psycopg2.connect(**db_config)
    cursor = conn.cursor()
    
    # 查看当前数据
    cursor.execute("SELECT workspace_id, root_path FROM workspaces WHERE workspace_id = %s", ("wrk_fcoss_20260620",))
    row = cursor.fetchone()
    print(f"当前: workspace_id={row[0]}, root_path={row[1]}")
    
    # 修改为 Windows 路径
    new_path = "D:\\workspace"
    cursor.execute("UPDATE workspaces SET root_path = %s WHERE workspace_id = %s", (new_path, "wrk_fcoss_20260620"))
    conn.commit()
    
    # 验证修改
    cursor.execute("SELECT workspace_id, root_path FROM workspaces WHERE workspace_id = %s", ("wrk_fcoss_20260620",))
    row = cursor.fetchone()
    print(f"修改后: workspace_id={row[0]}, root_path={row[1]}")
    
    cursor.close()
    conn.close()
    
    print("\n✅ 已修改！现在试试再次调用 API！")
    print("http://localhost:8080/api/internal/agent/opencode/api/agent?workspaceId=wrk_fcoss_20260620")
    
except Exception as e:
    print(f"错误: {e}")
    import traceback
    traceback.print_exc()
