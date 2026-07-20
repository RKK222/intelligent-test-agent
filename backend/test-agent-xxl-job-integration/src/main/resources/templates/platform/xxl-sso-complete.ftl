<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>XXL-JOB 登录完成</title>
</head>
<body>
<script>
    // 只有 Admin 成功建立会话后才通知父页面，避免把网关 502 等普通 load 误判为就绪。
    window.parent.postMessage({ type: "test-agent-xxl-job-sso", status: "ready" }, window.location.origin);
    window.setTimeout(function () {
        window.location.replace("${redirectPath?js_string}");
    }, 0);
</script>
</body>
</html>
