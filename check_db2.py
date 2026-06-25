
import psycopg2

db_config = {
    'host': '192.168.100.194',
    'port': '5432',
    'database': 'test_agent',
    'user': 'testagent',
    'password': 'testagent'
}

print("=== 查询数据库中的 execution_nodes 表 ===")

try:
    conn = psycopg2.connect(**db_config)
    cursor = conn.cursor()
    
    # 查询执行节点
    cursor.execute("SELECT * FROM execution_nodes")
    nodes = cursor.fetchall()
    
    # 获取列名
    col_names = [desc[0] for desc in cursor.description]
    
    print(f"找到 {len(nodes)} 个 execution_node:")
    for node in nodes:
        node_dict = dict(zip(col_names, node))
        print(f"  - {node_dict}")
    
    # 查询 workspace
    print("\n=== 查询 workspaces 表 ===")
    cursor.execute("SELECT * FROM workspaces")
    workspaces = cursor.fetchall()
    
    ws_col_names = [desc[0] for desc in cursor.description]
    print(f"找到 {len(workspaces)} 个 workspace:")
    for ws in workspaces:
        ws_dict = dict(zip(ws_col_names, ws))
        print(f"  - {ws_dict}")
    
    # 检查是否有 wrk_fcoss_20260620
    print("\n=== 检查是否存在 wrk_fcoss_20260620 ===")
    cursor.execute("SELECT * FROM workspaces WHERE id = %s", ("wrk_fcoss_20260620",))
    ws = cursor.fetchone()
    if ws:
        ws_dict = dict(zip(ws_col_names, ws))
        print(f"  找到: {ws_dict}")
    else:
        print(f"  未找到 workspace wrk_fcoss_20260620")
    
    cursor.close()
    conn.close()
    
except Exception as e:
    print(f"错误: {e}")
    import traceback
    traceback.print_exc()
