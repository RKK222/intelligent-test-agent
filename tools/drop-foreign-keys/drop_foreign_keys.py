
import psycopg2
from psycopg2 import sql

# 数据库连接信息
db_config = {
    'host': '192.168.100.194',
    'port': '5432',
    'database': 'test_agent',
    'user': 'testagent',
    'password': 'testagent'
}

try:
    # 连接数据库
    conn = psycopg2.connect(**db_config)
    cursor = conn.cursor()
    print("成功连接到数据库")

    # 查询所有外键
    cursor.execute("""
        SELECT
          tc.table_schema,
          tc.table_name,
          tc.constraint_name,
          kcu.column_name,
          ccu.table_schema AS foreign_table_schema,
          ccu.table_name AS foreign_table_name,
          ccu.column_name AS foreign_column_name
        FROM
          information_schema.table_constraints AS tc
          JOIN information_schema.key_column_usage AS kcu
            ON tc.constraint_name = kcu.constraint_name
            AND tc.table_schema = kcu.table_schema
          JOIN information_schema.constraint_column_usage AS ccu
            ON ccu.constraint_name = tc.constraint_name
            AND ccu.table_schema = tc.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY'
        ORDER BY tc.table_schema, tc.table_name, tc.constraint_name
    """)

    foreign_keys = cursor.fetchall()
    print(f"找到 {len(foreign_keys)} 个外键约束：")
    for fk in foreign_keys:
        print(f"  - {fk[0]}.{fk[1]}.{fk[2]} (引用 {fk[4]}.{fk[5]}.{fk[6]})")

    # 删除所有外键
    print("\n开始删除外键约束...")
    for fk in foreign_keys:
        table_schema, table_name, constraint_name = fk[0], fk[1], fk[2]
        
        # 构建删除语句
        drop_sql = sql.SQL("ALTER TABLE {}.{} DROP CONSTRAINT {}").format(
            sql.Identifier(table_schema),
            sql.Identifier(table_name),
            sql.Identifier(constraint_name)
        )
        
        cursor.execute(drop_sql)
        print(f"  ✓ 已删除: {table_schema}.{table_name}.{constraint_name}")

    # 提交事务
    conn.commit()
    print("\n所有外键约束已删除成功！")

except Exception as e:
    print(f"错误: {e}")
    import traceback
    traceback.print_exc()
    if 'conn' in locals():
        conn.rollback()
finally:
    if 'cursor' in locals():
        cursor.close()
    if 'conn' in locals():
        conn.close()

