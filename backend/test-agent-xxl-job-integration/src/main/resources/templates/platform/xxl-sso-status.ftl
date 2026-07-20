<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>XXL-JOB 登录状态</title>
    <style>
        body { margin: 0; padding: 32px; font: 14px/1.6 system-ui, sans-serif; color: #334155; background: #f8fafc; }
        main { max-width: 520px; margin: 10vh auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 10px; background: white; }
        h1 { margin: 0 0 8px; font-size: 18px; color: #0f172a; }
    </style>
</head>
<body>
<main>
    <h1>定时任务管理暂不可用</h1>
    <p>${message?html}</p>
</main>
<script>
    window.parent.postMessage({ type: "test-agent-xxl-job-sso", status: "${state?js_string}" }, window.location.origin);
</script>
</body>
</html>
