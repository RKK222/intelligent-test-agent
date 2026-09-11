/*
用途：统计两个周期内，每位用户在每个对话里的人工发送次数。
交付形式：一条 PostgreSQL 只读 SELECT；整段执行，不需要建表或创建临时表。
适用对象：当前平台数据库中的 sessions、session_messages、runs、users 四张表。

一、默认统计周期（年份固定为 2026，不随执行年份变化）
  周期 1：2026-08-31 至 2026-09-08，含首尾两天。
  周期 2：2026-09-09 至执行当天，含首尾两天；“当天”以北京时间为准。
  例如在北京时间 2026-09-10 执行，周期 2 就是 2026-09-09 至 2026-09-10。
  两个周期在同一个结果集中输出，各自独立计算总数。

二、同事使用步骤
  1. 在数据库客户端连接需要统计的平台 PostgreSQL 数据库。
  2. 按需修改下面 params 中的日期/人员关键词，然后执行整个 WITH ... SELECT。
     默认查询所有人；人员关键词支持姓名、统一认证号、用户 ID 的不区分大小写包含匹配。
     NULL 或空白关键词表示全部；% 和 _ 按 PostgreSQL ILIKE 通配符解释。
  3. 默认输出“周期 × 用户 × 日期 × 对话”的明细，可直接用客户端导出 Excel/CSV。
     如需每人每周期一行的汇总，或逐条发送时间，使用文件末尾的备用 SELECT。
     备用 SELECT 必须替换默认的最后一个 SELECT，保留整段 WITH；不要单独执行备用 SELECT。
  4. 只查一个周期时，在默认 SELECT 的 FROM details 后加入 WHERE period_no = 1 或 2。

三、时间边界
  SQL 一律使用“开始包含、结束不包含”，不用 23:59:59，避免遗漏微秒级时间。
  周期 1 实际条件：发送时间 >= 2026-08-31 00:00:00 且 < 2026-09-09 00:00:00。
  周期 2 实际条件：发送时间 >= 2026-09-09 00:00:00 且 < 执行当天的次日 00:00:00。
  因此 9 月 8 日深夜属于周期 1，9 月 9 日零点属于周期 2，不重复、不漏算交界。
  当前后端以 Asia/Shanghai 写入 timestamp without time zone，直接按列值分自然日。
  “当天”也显式转换为 Asia/Shanghai，不依赖数据库连接默认时区。

四、计数口径
  - 统计数据库仍保留的、平台已记录的人工 USER 消息，不统计未落库的发送动作。
  - LEGACY_FULL 按 USER 消息记录计数；REDIS_SUMMARY 按唯一 Run 锚点计数，
    不再叠加该 Run 的原文/终态摘要，覆盖正在运行的消息并避免重复计数。
  - 不计 AI 回复、定时自动触发和内部 SIDE_QUESTION；定时会话中的后续人工发送计入。
  - 已记录的发送即使 Run 失败或取消也计数；撤回后重新发送另计一次。
    同一 Run 的模型内部重试、工具调用、流式事件不增加发送次数。
  - 共享对话优先归实际发送人，而不是执行所属人；历史缺失字段时依次兼容回退。
    完全无法确认人员的消息保留为“未知用户”，个人总数留空，不假定属于同一个人。
  - 姓名、组织和对话标题读取当前主数据，不表示发送当时的历史快照。
  - 只输出有发送记录的分组，没有发送的用户/日期/对话不会生成 0 次行。

五、结果字段说明（“区间”均指当前行所属的统计周期）
  周期序号 / 统计周期：1 或 2，以及首尾均包含的日期范围。
  用户ID / 姓名 / 统一认证号：实际发送人；同名用户按用户ID区分，不合并。
  机构 / 研发部 / 部门：该用户当前组织信息；缺失时留空。
  日期 / 对话ID / 对话标题：发送发生的自然日及所属对话。
  当天发送次数：这个人在这个日期、这个对话内的发送次数。
  当天首次发送时间 / 当天末次发送时间：同一明细分组的最早/最晚发送时间。
  该用户对话区间总次数：这个人在“本周期 + 本对话”中的累计发送次数。
  该用户区间总次数：这个人在“本周期 + 所有对话”中的累计发送次数。
  两个总数字段会在明细中重复展示，不要逐行相加；汇总请加“当天发送次数”，
  或使用末尾的每人每周期汇总 SELECT。两个周期的总数不会互相累加。

六、实现与性能要点
  - 两个周期共用一次范围扫描：只对 [周期 1 首日, 当天次日) 过滤一次，再用交界日
    （second_start_date）把行归入 period 1/2，不再为每个周期各扫一遍基表或做区间自连接。
  - 过滤条件全部是可直接下推的 sargable 范围（created_at >= 下界、< 上界），
    在 created_at 索引存在时可走范围扫描；runs 已有 created_at 相关索引。
  - 会话标题随消息本身一次 join sessions 取得，不再二次 join，减少一次连接。
  - 人员归属、来源排除、存储模式互斥等口径与旧版完全一致，本次仅调整查询结构。
*/

WITH params AS (
    SELECT
        DATE '2026-08-31' AS first_start_date,  -- 周期 1 首日，包含当天。
        DATE '2026-09-09' AS second_start_date, -- 周期 2 首日；同时也是周期 1 的排他上界/交界日。
        -- 动态取北京时间的当天；复查固定周期时可改成 DATE '2026-09-10'。
        (statement_timestamp() AT TIME ZONE 'Asia/Shanghai')::date AS report_date,
        NULL::text AS user_keyword            -- 例如改成 '张三'::text；NULL 查询所有人。
        -- 调整日期时需保持 first_start_date < second_start_date <= report_date，不能填 NULL。
), bounds AS (
    -- 把两个周期压成一次扫描：只保留 [周期 1 首日, 当天次日) 一个范围，
    -- 交界日 boundary 用于把行归到 period 1/2，避免区间自连接带来的重复扫描。
    SELECT
        p.first_start_date,
        p.second_start_date,
        p.report_date,
        p.user_keyword,
        p.first_start_date::timestamp AS lower_bound,        -- 整体下界，包含。
        p.second_start_date::timestamp AS boundary,          -- 交界日零点：< 它属于周期 1。
        (p.report_date + 1)::timestamp AS upper_bound        -- 整体上界，不包含。
    FROM params p
), user_messages AS (
    -- 旧存储模式（LEGACY_FULL）：每条人工 USER 消息计一次，发送时间来自消息记录。
    SELECT
        CASE WHEN m.created_at < b.boundary THEN 1 ELSE 2 END AS period_no,
        m.message_id AS message_key,
        m.run_id,
        m.session_id,
        s.title AS session_title,
        COALESCE(m.sender_user_id, r.message_sender_user_id,
                 r.triggered_by_user_id, s.created_by_user_id) AS user_id,
        m.created_at AS sent_at
    FROM session_messages m
    JOIN sessions s ON s.session_id = m.session_id
    LEFT JOIN runs r ON r.run_id = m.run_id
    CROSS JOIN bounds b
    WHERE m.role = 'USER'
      AND m.created_at >= b.lower_bound
      AND m.created_at <  b.upper_bound
      AND COALESCE(r.storage_mode, 'LEGACY_FULL') = 'LEGACY_FULL'
      AND COALESCE(r.source_type, m.source_type, 'MANUAL') = 'MANUAL'
      AND COALESCE(s.source_type, 'MANUAL') <> 'SIDE_QUESTION'

    UNION ALL

    -- 摘要模式（REDIS_SUMMARY）：每个 Run 锚点计一次，发送时间取 Run 创建时间；
    -- 与上一支按 storage_mode 互斥，禁止再 UNION 摘要消息，避免同一发送重复计数。
    SELECT
        CASE WHEN r.created_at < b.boundary THEN 1 ELSE 2 END AS period_no,
        r.run_id AS message_key,
        r.run_id,
        r.session_id,
        s.title AS session_title,
        COALESCE(r.message_sender_user_id, r.triggered_by_user_id,
                 s.created_by_user_id) AS user_id,
        r.created_at AS sent_at
    FROM runs r
    JOIN sessions s ON s.session_id = r.session_id
    CROSS JOIN bounds b
    WHERE r.storage_mode = 'REDIS_SUMMARY'
      AND r.created_at >= b.lower_bound
      AND r.created_at <  b.upper_bound
      AND COALESCE(r.source_type, 'MANUAL') = 'MANUAL'
      AND COALESCE(s.source_type, 'MANUAL') <> 'SIDE_QUESTION'
), details AS (
    -- 补齐周期日期范围（供默认明细与两个备用 SELECT 复用）与当前人员/对话主数据；
    -- LEFT JOIN 保留缺少用户目录/归属的历史消息，再按当前人员目录做关键词筛选。
    SELECT
        m.period_no,
        CASE WHEN m.period_no = 1 THEN b.first_start_date ELSE b.second_start_date END AS start_date,
        CASE WHEN m.period_no = 1 THEN b.second_start_date ELSE b.report_date + 1 END AS end_date_exclusive,
        m.message_key,
        m.run_id,
        m.session_id,
        m.session_title,
        m.user_id,
        m.sent_at,
        COALESCE(u.username, m.user_id, '未知用户') AS username,
        u.unified_auth_id,
        u.organization,
        u.rd_department,
        u.department
    FROM user_messages m
    CROSS JOIN bounds b
    LEFT JOIN users u ON u.user_id = m.user_id
    WHERE NULLIF(BTRIM(b.user_keyword), '') IS NULL
       OR u.username ILIKE '%' || BTRIM(b.user_keyword) || '%'
       OR u.unified_auth_id ILIKE '%' || BTRIM(b.user_keyword) || '%'
       OR m.user_id ILIKE '%' || BTRIM(b.user_keyword) || '%'
)
SELECT
    period_no AS "周期序号",
    TO_CHAR(start_date, 'YYYY-MM-DD') || ' 至 ' ||
        TO_CHAR(end_date_exclusive - 1, 'YYYY-MM-DD') AS "统计周期",
    user_id AS "用户ID",
    username AS "姓名",
    unified_auth_id AS "统一认证号",
    organization AS "机构",
    rd_department AS "研发部",
    department AS "部门",
    sent_at::date AS "日期",
    session_id AS "对话ID",
    session_title AS "对话标题",
    COUNT(*) AS "当天发送次数",
    MIN(sent_at) AS "当天首次发送时间",
    MAX(sent_at) AS "当天末次发送时间",
    -- 无归属的历史记录不能假定属于同一个人，保留当天计数但不生成个人总数。
    CASE WHEN user_id IS NOT NULL THEN
        SUM(COUNT(*)) OVER (PARTITION BY period_no, user_id, session_id)
    END AS "该用户对话区间总次数",
    CASE WHEN user_id IS NOT NULL THEN
        SUM(COUNT(*)) OVER (PARTITION BY period_no, user_id)
    END AS "该用户区间总次数"
FROM details
GROUP BY period_no, start_date, end_date_exclusive,
         user_id, username, unified_auth_id, organization, rd_department,
         department, sent_at::date, session_id, session_title
ORDER BY period_no, user_id NULLS LAST, "日期" DESC, "当天首次发送时间", session_id;

-- 备用 A：每人每周期一行，便于比较两个周期；保留 WITH，把默认的最后一个 SELECT 替换为此段。
-- 未知用户可能包含不同人员，按人汇总时排除；默认每日明细仍保留其发送记录。
-- SELECT period_no AS "周期序号",
--        TO_CHAR(start_date, 'YYYY-MM-DD') || ' 至 ' ||
--            TO_CHAR(end_date_exclusive - 1, 'YYYY-MM-DD') AS "统计周期",
--        user_id AS "用户ID", username AS "姓名", unified_auth_id AS "统一认证号",
--        COUNT(DISTINCT session_id) AS "参与对话数", COUNT(*) AS "周期发送总次数"
-- FROM details
-- WHERE user_id IS NOT NULL
-- GROUP BY period_no, start_date, end_date_exclusive, user_id, username, unified_auth_id
-- ORDER BY period_no, "周期发送总次数" DESC, user_id;

-- 备用 B：每条发送一行；保留 WITH，把默认的最后一个 SELECT 替换为此段。
-- “计数依据ID”在旧模式是 message_id，在摘要模式是 run_id，不代表两种模式都有持久化消息ID。
-- SELECT period_no AS "周期序号",
--        TO_CHAR(start_date, 'YYYY-MM-DD') || ' 至 ' ||
--            TO_CHAR(end_date_exclusive - 1, 'YYYY-MM-DD') AS "统计周期",
--        user_id AS "用户ID", username AS "姓名", unified_auth_id AS "统一认证号",
--        session_id AS "对话ID", session_title AS "对话标题",
--        sent_at AS "发送时间", message_key AS "计数依据ID", run_id AS "RunID",
--        CASE WHEN user_id IS NOT NULL THEN
--            COUNT(*) OVER (PARTITION BY period_no, user_id, session_id)
--        END AS "该用户对话区间总次数"
-- FROM details
-- ORDER BY period_no, user_id NULLS LAST, sent_at, session_id, message_key;
