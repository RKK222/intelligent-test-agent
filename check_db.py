
import psycopg2

db_config = {
    'host': '192.168.100.194',
    'port': '5432',
    'database': 'test_agent',
    'user': 'testagent',
    'password': 'testagent'
}

print("=== 查询数据库中的 execution_node 表 ===")

try:
    conn = psycopg2.connect(**db_config)
    cursor = conn.cursor()
    
    # 查询执行节点
    cursor.execute("SELECT * FROM execution_node")
    nodes = cursor.fetchall()
    
    print(f"找到 {len(nodes)} 个 execution_node:")
    for node in nodes:
        print(f"  - {node}")
    
    # 查询 workspace
    print("\n=== 查询 workspace 表 ===")
    cursor.execute("SELECT * FROM workspace")
    workspaces = cursor.fetchall()
    print(f"找到 {len(workspaces)} 个 workspace:")
    for ws in workspaces:
        print(f"  - {ws}")
    
    # 检查是否有 wrk_fcoss_20260620
    print("\n=== 检查是否存在 wrk_fcoss_20260620 ===")
    cursor.execute("SELECT * FROM workspace WHERE id = %s", ("wrk_fcoss_20260620",))
    ws = cursor.fetchone()
    print(f"  结果: {ws}")
    
    cursor.close()
    conn.close()
    
except Exception as e:
    print(f"错误: {e}")
    import traceback
    traceback.print_exc()
