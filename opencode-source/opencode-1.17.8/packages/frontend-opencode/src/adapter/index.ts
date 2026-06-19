// 适配层入口（适配层）：替换 opencode 直连 server 的发送与回复流，经平台后端 test-agent-app。
// 详见 frontend-opencode/README.md。

export { sendPrompt, type SendPromptInput } from "./send"
export { setDispatch, registerRun, unregisterRun } from "./event-bridge"
export { projectRunEvent, type RunEventSsePayload } from "./project"
export { PlatformRequestError, type PlatformError } from "./platform-client"
