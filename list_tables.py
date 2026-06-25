
import psycopg2

db_config = {
    'host': '192.168.100.194',
    'port': '5432',
    'database': 'test_agent',
    'user': 'testagent',
    'password': 'testagent'
}

print("=== 查询数据库中的所有表 ===")

try:
    conn = psycopg2.connect(**db_config)
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public'
        ORDER BY table_name
    """)
    tables = cursor.fetchall()
    
    print(f"找到 {len(tables)} 个表:")
    for table in tables:
        print(f"  - {table[0]}")
    
    cursor.close()
    conn.close()
    
except Exception as e:
    print(f"错误: {e}")
