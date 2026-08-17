# test-agent-workspace-filesystem

本模块是服务端和本地 OpenCode 客户端共同使用的文件系统安全内核。它从
`test-agent-workspace-management` 原样抽取以下能力：真实根路径锚定、相对路径约束、符号链接逃逸防护、
原子不覆盖移动、分片上传下载、UTF-8 渐进预览、搜索和删除。

上层只能向该内核传入已经授权的工作区根目录。正常文件 RPC 必须传相对路径，根目录注册和权限校验由
调用方在独立流程完成。
