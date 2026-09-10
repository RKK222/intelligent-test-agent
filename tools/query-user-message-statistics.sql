-- PostgreSQL 只读统计：实际发送人 × 自然日 × 对话。
-- 直接在数据库客户端执行；NULL 表示不限制，可修改 params 中的三个值。
-- 时间范围为 [start_time, end_time)，例如 2026-09-01 至 2026-10-01 表示整个 9 月。
-- 当前后端以 Asia/Shanghai 写入 timestamp without time zone，按该时间直接分日。
-- 只计平台已记录的人工 USER 消息；不计 AI 回复、定时触发、SIDE_QUESTION。
-- 撤回后重新发送是新的发送；同一 Run 的模型重试、工具事件不增加次数。
-- LEGACY_FULL 按消息记录计数；REDIS_SUMMARY 按 Run 锚点计数，覆盖运行中且避免终态摘要重复。
WITH params AS (
    SELECT
        NULL::timestamp AS start_time, -- 例如 TIMESTAMP '2026-09-01 00:00:00'
        NULL::timestamp AS end_time,   -- 例如 TIMESTAMP '2026-10-01 00:00:00'
        NULL::text AS user_keyword     -- 例如 '张三'、统一认证号或用户 ID；NULL 查询所有人
), user_messages AS (
    SELECT
        m.message_id AS message_key,
        m.run_id,
        m.session_id,
        COALESCE(m.sender_user_id, r.message_sender_user_id,
                 r.triggered_by_user_id, s.created_by_user_id) AS user_id,
        m.created_at AS sent_at
    FROM session_messages m
    JOIN sessions s ON s.session_id = m.session_id
    LEFT JOIN runs r ON r.run_id = m.run_id
    CROSS JOIN params p
    WHERE m.role = 'USER'
      AND COALESCE(r.storage_mode, 'LEGACY_FULL') = 'LEGACY_FULL'
      AND COALESCE(r.source_type, m.source_type, 'MANUAL') = 'MANUAL'
      AND COALESCE(s.source_type, 'MANUAL') <> 'SIDE_QUESTION'
      AND (p.start_time IS NULL OR m.created_at >= p.start_time)
      AND (p.end_time IS NULL OR m.created_at < p.end_time)

    UNION ALL

    SELECT
        r.run_id AS message_key,
        r.run_id,
        r.session_id,
        COALESCE(r.message_sender_user_id, r.triggered_by_user_id,
                 s.created_by_user_id) AS user_id,
        r.created_at AS sent_at
    FROM runs r
    JOIN sessions s ON s.session_id = r.session_id
    CROSS JOIN params p
    WHERE r.storage_mode = 'REDIS_SUMMARY'
      AND COALESCE(r.source_type, 'MANUAL') = 'MANUAL'
      AND COALESCE(s.source_type, 'MANUAL') <> 'SIDE_QUESTION'
      AND (p.start_time IS NULL OR r.created_at >= p.start_time)
      AND (p.end_time IS NULL OR r.created_at < p.end_time)
), details AS (
    SELECT
        m.*,
        COALESCE(u.username, m.user_id, '未知用户') AS username,
        u.unified_auth_id,
        u.organization,
        u.rd_department,
        u.department,
        s.title AS session_title
    FROM user_messages m
    JOIN sessions s ON s.session_id = m.session_id
    LEFT JOIN users u ON u.user_id = m.user_id
    CROSS JOIN params p
    WHERE NULLIF(BTRIM(p.user_keyword), '') IS NULL
       OR u.username ILIKE '%' || BTRIM(p.user_keyword) || '%'
       OR u.unified_auth_id ILIKE '%' || BTRIM(p.user_keyword) || '%'
       OR m.user_id ILIKE '%' || BTRIM(p.user_keyword) || '%'
)
SELECT
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
        SUM(COUNT(*)) OVER (PARTITION BY user_id, session_id)
    END AS "该用户对话区间总次数",
    CASE WHEN user_id IS NOT NULL THEN
        SUM(COUNT(*)) OVER (PARTITION BY user_id)
    END AS "该用户区间总次数"
FROM details
GROUP BY user_id, username, unified_auth_id, organization, rd_department,
         department, sent_at::date, session_id, session_title
ORDER BY user_id NULLS LAST, "日期" DESC, "当天首次发送时间", session_id;

-- 如需逐条发送时间，保留上面的 WITH 定义，把最后一个 SELECT 替换为以下内容：
-- SELECT user_id AS "用户ID", username AS "姓名", unified_auth_id AS "统一认证号",
--        session_id AS "对话ID", session_title AS "对话标题",
--        sent_at AS "发送时间", message_key AS "计数依据ID", run_id AS "RunID",
--        CASE WHEN user_id IS NOT NULL THEN
--            COUNT(*) OVER (PARTITION BY user_id, session_id)
--        END AS "该用户对话区间总次数"
-- FROM details
-- ORDER BY user_id NULLS LAST, sent_at, session_id, message_key;
