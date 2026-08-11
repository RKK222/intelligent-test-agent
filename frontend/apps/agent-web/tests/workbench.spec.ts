import { expect, test, type Page } from "@playwright/test";
import { applicationWorkspaceRestrictionsFixture as permissionFixture } from "../../../tests/fixtures/application-workspace-restrictions";

test("session share management and received list preserve one link and inactive history", async ({ page }) => {
  const sharePutRequests: Array<Record<string, unknown>> = [];
  const shareRevokeRequests: Array<{ sessionId: string; expectedVersion: string | null }> = [];
  await page.addInitScript(() => {
    window.open = ((url?: string | URL, target?: string, features?: string) => {
      Object.assign(window, {
        __testOpenedSessionShare: [String(url), target, features]
      });
      return window;
    }) as typeof window.open;
  });
  const sessionUnderShare = { ...session(), title: "支付回归协作会话" };
  await mockBackendApi(page, {
    sessions: [sessionUnderShare],
    sessionMessagesBySessionId: { ses_1: [] },
    sharedSessions: [
      sharedSessionListItem({ shareId: "shr_active", sessionTitle: "接口联调协作", status: "ACTIVE" }),
      sharedSessionListItem({ shareId: "shr_expired", sessionTitle: "已过期协作", status: "EXPIRED" })
    ],
    sessionCollaborationShare: null,
    sessionShareCandidates: [
      { userId: "usr_collaborator", unifiedAuthId: "ucid_collaborator", username: "协作者" }
    ],
    sessionSharePutRequests: sharePutRequests,
    sessionShareRevokeRequests: shareRevokeRequests,
    nightTasks: [{
      taskId: "net_share_pending",
      sessionId: "ses_1",
      workspaceId: "wrk_1234567890abcdef",
      sessionTitle: "支付回归协作会话",
      contentPreview: "稍后执行",
      status: "SCHEDULED",
      scheduleMode: "NIGHT_WINDOW",
      slotStart: "2026-08-09T13:00:00Z",
      slotEnd: "2026-08-09T13:15:00Z",
      windowEnd: "2026-08-09T23:00:00Z",
      rolloverCount: 0,
      runId: null,
      errorCode: null,
      errorMessage: null,
      createdAt: "2026-08-09T01:00:00Z",
      updatedAt: "2026-08-09T01:00:00Z"
    }]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await expect(page.getByRole("button", { name: "会话列表" })).toBeVisible({ timeout: 20_000 });
  await page.getByRole("button", { name: "会话列表" }).click();
  await page.getByRole("tab", { name: /分享给我/ }).click();
  await expect(page.getByRole("button", { name: /接口联调协作/ })).toBeEnabled();
  await expect(page.getByRole("button", { name: /已过期协作/ })).toBeDisabled();
  await expect(page.getByText("已过期", { exact: true })).toBeVisible();
  const ordinaryWorkbenchUrl = page.url();
  await page.getByRole("button", { name: /接口联调协作/ }).click();
  await expect.poll(() => page.evaluate(() => (
    window as typeof window & { __testOpenedSessionShare?: string[] }
  ).__testOpenedSessionShare)).toEqual([
    "/s/shr_active",
    "_blank",
    "noopener,noreferrer"
  ]);
  expect(page.url()).toBe(ordinaryWorkbenchUrl);

  await page.getByRole("tab", { name: /我的会话/ }).click();
  await historySessionButton(page, /支付回归协作会话/).click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click({ force: true });
  await page.getByTestId("manage-session-share").click();

  const dialog = page.locator(".session-share-dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("当前会话有 1 个待执行定时任务；分享失效后仍将按原计划执行。")).toBeVisible();
  await dialog.getByRole("button", { name: /协作者/ }).click();
  await dialog.locator(".session-share-dialog__permission-switch .el-switch__core").click();
  await dialog.getByRole("radio", { name: "3 天" }).click();
  await dialog.getByRole("button", { name: "创建分享" }).click();

  await expect.poll(() => sharePutRequests.length).toBe(1);
  expect(sharePutRequests[0]?.expectedVersion).toBeNull();
  expect(sharePutRequests[0]?.members).toEqual([{ userId: "usr_collaborator", canChat: true }]);
  await expect(page.getByText("已通知被分享人，分享设置已保存")).toBeVisible();
  await expect(dialog.getByLabel("唯一分享链接")).toHaveValue(/\/s\/shr_e2e_unique$/);

  await dialog.getByRole("button", { name: "取消分享" }).click();
  await expect(page.getByText(/取消分享不会取消任务/)).toBeVisible();
  await page.getByRole("button", { name: "取消分享", exact: true }).last().click();
  await expect.poll(() => shareRevokeRequests).toEqual([{ sessionId: "ses_1", expectedVersion: "0" }]);
});

test("session share notification appears in real time, opens a new tab, then reflects read and invalidated changes", async ({ page }) => {
  let releaseCreated!: () => void;
  let releaseRead!: () => void;
  let releaseInvalidated!: () => void;
  const createdGate = new Promise<void>((resolve) => { releaseCreated = resolve; });
  const readGate = new Promise<void>((resolve) => { releaseRead = resolve; });
  const invalidatedGate = new Promise<void>((resolve) => { releaseInvalidated = resolve; });
  const notificationReadRequests: string[] = [];
  const notificationEventRequests: string[] = [];
  const activeNotification = {
    notificationId: "ntf_share_live",
    type: "SESSION_SHARED",
    actorUserId: "usr_owner",
    title: "张敏 向你分享了对话",
    body: "支付回归问题定位 · 可对话",
    actionType: "SESSION_SHARE",
    actionTargetId: "shr_notification_live",
    status: "ACTIVE",
    invalidationReason: null,
    actionAvailable: true,
    unread: true,
    expiresAt: "2026-08-11T12:00:00Z",
    readAt: null,
    createdAt: "2026-08-10T10:00:00Z",
    updatedAt: "2026-08-10T10:00:00Z"
  };
  const notificationCapture = {
    authUser: {
      userId: "usr_collaborator",
      username: "协作者",
      unifiedAuthId: "ucid_collaborator",
      roles: ["APP_MEMBER"]
    },
    userNotifications: [] as Array<Record<string, unknown>>,
    userNotificationUnreadCount: 0,
    userNotificationReadRequests: notificationReadRequests,
    userNotificationEventRequests: notificationEventRequests,
    userNotificationEvents: [
      { gate: createdGate, changeType: "CREATED", notificationId: "ntf_share_live", unreadCount: 1 },
      { gate: readGate, changeType: "READ", notificationId: "ntf_share_live", unreadCount: 0 },
      { gate: invalidatedGate, changeType: "INVALIDATED", notificationId: "ntf_share_live", unreadCount: 0 }
    ]
  };
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v7:usr_collaborator", "seen");
    window.open = ((url?: string | URL, target?: string, features?: string) => {
      Object.assign(window, { __testOpenedNotificationShare: [String(url), target, features] });
      return window;
    }) as typeof window.open;
  });
  await mockBackendApi(page, notificationCapture);

  await gotoWorkbench(page, { selectConversation: false });
  const trigger = page.getByTestId("notification-center-trigger");
  await expect(trigger).toBeVisible({ timeout: 20_000 });
  await expect(page.locator(".user-notification-center__badge")).toHaveCount(0);

  notificationCapture.userNotifications = [activeNotification];
  notificationCapture.userNotificationUnreadCount = 1;
  releaseCreated();
  await expect(page.locator(".user-notification-center__badge")).toHaveText("1");

  await trigger.click();
  const activeItem = page.getByRole("button", { name: /张敏 向你分享了对话.*在新标签页打开/ });
  await expect(activeItem).toBeVisible();
  await expect(page.getByText("支付回归问题定位", { exact: true })).toBeVisible();
  await expect(page.getByText("可对话", { exact: true })).toBeVisible();
  const sourceUrl = page.url();
  await activeItem.click();
  await expect.poll(() => page.evaluate(() => (
    window as typeof window & { __testOpenedNotificationShare?: string[] }
  ).__testOpenedNotificationShare)).toEqual([
    "/s/shr_notification_live",
    "_blank",
    "noopener,noreferrer"
  ]);
  expect(page.url()).toBe(sourceUrl);
  expect(notificationReadRequests).toEqual([]);

  notificationCapture.userNotifications = [{
    ...activeNotification,
    unread: false,
    readAt: "2026-08-10T10:02:00Z",
    updatedAt: "2026-08-10T10:02:00Z"
  }];
  notificationCapture.userNotificationUnreadCount = 0;
  releaseRead();
  await expect(page.locator(".user-notification-center__badge")).toHaveCount(0);
  await expect(page.locator(".user-notification-center__item")).not.toHaveClass(/is-unread/);

  notificationCapture.userNotifications = [{
    ...activeNotification,
    status: "INVALIDATED",
    invalidationReason: "REVOKED",
    actionAvailable: false,
    unread: false,
    readAt: "2026-08-10T10:02:00Z",
    updatedAt: "2026-08-10T10:03:00Z"
  }];
  releaseInvalidated();
  const invalidItem = page.getByRole("button", { name: /张敏 向你分享了对话.*分享已失效/ });
  await expect(invalidItem).toBeDisabled();
  await expect(page.getByText("分享已失效", { exact: true })).toBeVisible();
  await expect.poll(() => notificationEventRequests.length).toBeGreaterThanOrEqual(3);
});

test("session share owner repairs historical collaborator resend attribution from audit metadata", async ({ page }) => {
  const historicalResend = {
    resendId: "rsd_collaborator_owner_view",
    trigger: "MANUAL",
    totalAttempt: 1,
    automaticAttempt: 0,
    automaticLimit: 3,
    status: "DISPATCHED",
    executeAt: "2026-08-09T01:00:00Z",
    sourceRunId: "run_collaborator_owner_source",
    replacementRunId: "run_collaborator_owner_view",
    requesterUserId: "usr_collaborator",
    requesterUsername: "协作者",
    requesterUnifiedAuthId: "ucid_collaborator",
    requestedBySharedUser: true
  };
  await mockBackendApi(page, {
    sessions: [{ ...session(), title: "所属人协作会话" }],
    sessionCollaborationShare: {
      shareId: "shr_owner_names",
      sharePath: "/s/shr_owner_names",
      sessionId: "ses_1",
      workspaceId: "wrk_1234567890abcdef",
      ownerUserId: "usr_admin",
      status: "ACTIVE",
      expiresAt: "2026-08-16T00:00:00Z",
      version: 3,
      members: [{
        userId: "usr_collaborator",
        unifiedAuthId: "ucid_collaborator",
        username: "协作者",
        canChat: true,
        status: "ACTIVE",
        sharedAt: "2026-08-09T00:00:00Z",
        updatedAt: "2026-08-09T00:00:00Z",
        removedAt: null
      }],
      createdAt: "2026-08-09T00:00:00Z",
      updatedAt: "2026-08-09T00:00:00Z",
      revokedAt: null
    },
    sessionMessagesBySessionId: {
      ses_1: [{
        messageId: "msg_collaborator_owner_view",
        remoteMessageId: "msg_remote_collaborator_owner_view",
        sessionId: "ses_1",
        role: "USER",
        content: "协作者发出的消息",
        senderUserId: "usr_admin",
        senderUnifiedAuthId: "ucid_owner",
        sentBySharedUser: false,
        createdAt: "2026-08-09T01:00:00Z",
        runId: "run_collaborator_owner_view",
        resend: historicalResend
      }]
    },
    historyRun: {
      runId: "run_collaborator_owner_view",
      sessionId: "ses_1",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      triggeredByUserId: "usr_admin",
      messageSenderUserId: "usr_admin",
      messageSenderUnifiedAuthId: "ucid_owner",
      messageSentBySharedUser: false,
      resend: historicalResend,
      createdAt: "2026-08-09T01:00:00Z",
      updatedAt: "2026-08-09T01:00:01Z"
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, /所属人协作会话/).click();
  const collaboratorTurn = page.locator('[data-oc-turn-id="msg_collaborator_owner_view"]');
  await expect(collaboratorTurn).toBeVisible({ timeout: 20_000 });
  await expect(collaboratorTurn.locator(".oc-user-message__sender")).toHaveText("协作者");
  await expect(page.getByRole("button", { name: "撤销重发最后一条消息" })).toHaveCount(0);
});

test("session share owner keeps one collaborator resend after compacted history and late native events", async ({ page }) => {
  const sessionMessageRequests: string[] = [];
  const sessionTreeRequests: string[] = [];
  const activeRun = {
    runId: "run_owner_resend_replacement",
    sessionId: "ses_owner_resend_revision",
    workspaceId: "wrk_1234567890abcdef",
    status: "RUNNING",
    triggeredByUserId: "usr_admin",
    // 模拟旧执行链把替代 Run 错误覆盖成所属人；resend requester 才是可信发送人。
    messageSenderUserId: "usr_admin",
    messageSenderUnifiedAuthId: "DEV_888888888",
    messageSentBySharedUser: false,
    resend: {
      resendId: "rsd_owner_resend_revision",
      trigger: "MANUAL",
      totalAttempt: 1,
      automaticAttempt: 0,
      automaticLimit: 3,
      status: "DISPATCHED",
      executeAt: "2026-08-10T03:30:02Z",
      sourceRunId: "run_owner_resend_source",
      replacementRunId: "run_owner_resend_replacement",
      requesterUserId: "usr_wr",
      requesterUsername: "wr",
      requesterUnifiedAuthId: "wr",
      requestedBySharedUser: true
    },
    createdAt: "2026-08-10T03:30:02Z",
    updatedAt: "2026-08-10T03:30:03Z"
  };
  const compactedHistory: Array<Record<string, unknown>> = [{
    messageId: "msg_owner_resend_compaction",
    sessionId: "ses_owner_resend_revision",
    role: "ASSISTANT",
    content: "",
    createdAt: "2026-08-10T03:29:58Z",
    parts: [{
      partId: "prt-owner-resend-compaction",
      type: "compaction",
      auto: false,
      overflow: false
    }]
  }, {
    messageId: "msg_owner_resend_compaction_envelope",
    sessionId: "ses_owner_resend_revision",
    role: "USER",
    content: "",
    createdAt: "2026-08-10T03:29:58.500Z",
    parts: []
  }, {
    messageId: "msg_owner_resend_compaction_summary",
    sessionId: "ses_owner_resend_revision",
    role: "ASSISTANT",
    content: "## Objective\n继续共享任务",
    createdAt: "2026-08-10T03:29:59Z",
    parts: [{
      partId: "prt-owner-resend-compaction-summary",
      type: "text",
      text: "## Objective\n继续共享任务"
    }]
  }];
  const messages: Record<string, Array<Record<string, unknown>>> = {
    ses_owner_resend_revision: [...compactedHistory, {
      messageId: "msg_owner_resend_old",
      remoteMessageId: "msg_remote_owner_resend_old",
      sessionId: "ses_owner_resend_revision",
      role: "USER",
      content: "仅答复 OK",
      senderUserId: "usr_wr",
      senderUsername: "wr",
      senderUnifiedAuthId: "wr",
      sentBySharedUser: true,
      createdAt: "2026-08-10T03:30:00Z",
      runId: "run_owner_resend_source"
    }, {
      messageId: "msg_owner_resend_old_answer",
      sessionId: "ses_owner_resend_revision",
      role: "ASSISTANT",
      content: "OK",
      createdAt: "2026-08-10T03:30:01Z",
      runId: "run_owner_resend_source"
    }]
  };
  await installAuthenticatedRunEventFetchStream(page, {
    run_owner_resend_replacement: [{
      releaseKey: "owner-resend-started",
      events: [{
        eventId: "evt_owner_resend_started",
        seq: 7,
        type: "run.resend.started",
        payload: {
          resendId: "rsd_owner_resend",
          sourceRunId: "run_owner_resend_source",
          replacementRunId: "run_owner_resend_replacement",
          trigger: "MANUAL",
          totalAttempt: 1,
          automaticAttempt: 0,
          automaticLimit: 3,
          status: "DISPATCHED",
          executeAt: "2026-08-10T03:30:02Z",
          requesterUserId: "usr_wr",
          requesterUsername: "wr",
          requesterUnifiedAuthId: "wr",
          requestedBySharedUser: true
        }
      }]
    }, {
      releaseKey: "owner-resend-late-native-events",
      events: [{
        eventId: "evt_owner_resend_late_compaction",
        seq: 8,
        type: "message.part.updated",
        payload: {
          part: {
            id: "prt-owner-resend-compaction",
            messageID: "msg_remote_owner_resend_compaction_envelope",
            type: "compaction",
            auto: false,
            overflow: false
          }
        }
      }, {
        eventId: "evt_owner_resend_answer",
        seq: 9,
        type: "message.updated",
        payload: {
          message: {
            id: "msg_remote_owner_resend_answer",
            role: "assistant",
            content: "123"
          }
        }
      }, {
        eventId: "evt_owner_resend_late_user",
        seq: 10,
        type: "message.updated",
        payload: {
          senderUserId: "usr_admin",
          senderUsername: "888888888",
          sentBySharedUser: false,
          message: {
            id: "msg_remote_owner_resend_new",
            role: "user",
            content: "仅答复 123",
            senderUserId: "usr_admin",
            senderUsername: "888888888",
            sentBySharedUser: false
          }
        }
      }]
    }]
  });
  await mockBackendApi(page, {
    sessions: [{
      ...session(),
      sessionId: "ses_owner_resend_revision",
      title: "所属人重发同步会话"
    }],
    sessionMessagesBySessionId: messages,
    sessionMessageRequests,
    sessionTreeRequests,
    sessionCollaborationShare: {
      shareId: "shr_owner_resend_revision",
      sharePath: "/s/shr_owner_resend_revision",
      sessionId: "ses_owner_resend_revision",
      workspaceId: "wrk_1234567890abcdef",
      ownerUserId: "usr_admin",
      status: "ACTIVE",
      expiresAt: "2026-08-16T00:00:00Z",
      version: 1,
      members: [{
        userId: "usr_wr",
        unifiedAuthId: "wr",
        username: "wr",
        canChat: true,
        status: "ACTIVE",
        sharedAt: "2026-08-10T03:00:00Z",
        updatedAt: "2026-08-10T03:00:00Z",
        removedAt: null
      }],
      createdAt: "2026-08-10T03:00:00Z",
      updatedAt: "2026-08-10T03:00:00Z",
      revokedAt: null
    },
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 0,
      permissionCount: 0,
      sessions: [{
        sessionId: "ses_owner_resend_revision",
        runId: "run_owner_resend_replacement",
        runStatus: "RUNNING",
        updatedAt: "2026-08-10T03:30:03Z"
      }],
      generatedAt: "2026-08-10T03:30:03Z"
    },
    activeRun,
    runsByRunId: {
      run_owner_resend_source: {
        ...activeRun,
        runId: "run_owner_resend_source",
        status: "SUCCEEDED",
        createdAt: "2026-08-10T03:30:00Z",
        updatedAt: "2026-08-10T03:30:01Z"
      },
      run_owner_resend_replacement: activeRun
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, /所属人重发同步会话/).click();
  await expect(page.getByText("仅答复 OK", { exact: true })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId("compaction-part-prt-owner-resend-compaction")).toBeVisible();
  await expect.poll(async () => page.evaluate(() =>
    (window as Window & { __titleWatchRunStreams?: Array<{ runId: string }> })
      .__titleWatchRunStreams?.some((item) => item.runId === "run_owner_resend_replacement") ?? false
  )).toBe(true);
  const initialMessageRequestCount = sessionMessageRequests.length;
  const initialSessionTreeRequestCount = sessionTreeRequests.length;
  await startHistoryLoadingObservation(page);

  messages.ses_owner_resend_revision = [...compactedHistory, {
    messageId: "msg_owner_resend_new",
    remoteMessageId: "msg_remote_owner_resend_new",
    sessionId: "ses_owner_resend_revision",
    role: "USER",
    content: "仅答复 123",
    senderUserId: "usr_admin",
    senderUsername: "888888888",
    senderUnifiedAuthId: "DEV_888888888",
    sentBySharedUser: false,
    createdAt: "2026-08-10T03:30:02Z",
    runId: "run_owner_resend_replacement",
    resend: activeRun.resend
  }];
  const released = await page.evaluate(() =>
    (window as Window & { __releaseRunEventBatch?: (releaseKey: string) => boolean })
      .__releaseRunEventBatch?.("owner-resend-started") ?? false
  );
  expect(released).toBe(true);

  await expect.poll(() => sessionMessageRequests.length).toBeGreaterThan(initialMessageRequestCount);
  await expect(page.getByText("仅答复 OK", { exact: true })).toHaveCount(0);
  const replacementTurn = page.locator('[data-oc-turn-id="msg_owner_resend_new"]');
  await expect(replacementTurn).toBeVisible();
  await expect(replacementTurn.locator(".oc-user-message__sender")).toHaveText("wr");
  await expect(replacementTurn.locator(".oc-user-message__bubble")).toHaveText("仅答复 123");
  expect(sessionTreeRequests).toHaveLength(initialSessionTreeRequestCount);
  expect(await historyLoadingObserved(page)).toBe(false);

  const nativeEventsReleased = await page.evaluate(() =>
    (window as Window & { __releaseRunEventBatch?: (releaseKey: string) => boolean })
      .__releaseRunEventBatch?.("owner-resend-late-native-events") ?? false
  );
  expect(nativeEventsReleased).toBe(true);

  await expect(page.getByText("123", { exact: true })).toBeVisible();
  await expect(page.getByText("仅答复 123", { exact: true })).toHaveCount(1);
  await expect(page.locator('[data-oc-turn-id="msg_remote_owner_resend_new"]')).toHaveCount(0);
  await expect(replacementTurn.locator(".oc-user-message__sender")).toHaveText("wr");
  const compaction = page.getByTestId("compaction-part-prt-owner-resend-compaction");
  await expect(compaction).toHaveCount(1);
  await expect(compaction).toBeVisible();
  expect(await page.locator([
    '[data-testid="compaction-part-prt-owner-resend-compaction"]',
    '[data-oc-turn-id="msg_owner_resend_new"]'
  ].join(", ")).evaluateAll((nodes) => nodes.map((node) =>
    node.getAttribute("data-oc-turn-id") ? "replacement" : "compaction"
  ))).toEqual(["compaction", "replacement"]);
});

test("session share read-only workbench shows sender identity colors and fixed scope", async ({ page }) => {
  const shareHeaderRequests: Array<{ method: string; path: string; shareId: string }> = [];
  const sharedSession = {
    ...session(),
    sessionId: "ses_shared_readonly",
    workspaceId: "wrk_shared_readonly",
    title: "共享只读会话"
  };
  await mockBackendApi(page, {
    authUser: { userId: "usr_reader", username: "阅读者", unifiedAuthId: "ucid_reader", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_readonly", name: "共享固定工作区" }],
    sessions: [sharedSession],
    sessionShareAccess: sessionShareAccess({ canChat: false }),
    sessionShareRuntimeStates: [sessionShareRuntimeState({ canChat: false })],
    sessionShareHeaderRequests: shareHeaderRequests,
    sessionMessagesBySessionId: {
      ses_shared_readonly: [
        {
          messageId: "msg_owner_shared",
          sessionId: "ses_shared_readonly",
          role: "USER",
          content: "所属人发出的消息",
          senderUserId: "usr_owner",
          senderUnifiedAuthId: "ucid_owner",
          sentBySharedUser: false,
          createdAt: "2026-08-09T01:00:00Z"
        },
        {
          messageId: "msg_reader_shared",
          sessionId: "ses_shared_readonly",
          role: "USER",
          content: "我之前发出的消息",
          senderUserId: "usr_reader",
          senderUnifiedAuthId: "ucid_reader",
          sentBySharedUser: true,
          createdAt: "2026-08-09T01:01:00Z"
        },
        {
          messageId: "msg_assistant_shared",
          sessionId: "ses_shared_readonly",
          role: "ASSISTANT",
          content: "协作回复",
          createdAt: "2026-08-09T01:02:00Z"
        }
      ]
    }
  });

  await page.goto("/s/shr_readonly", { waitUntil: "domcontentloaded" });
  await expect(page.getByTestId("header-fixed-share-context")).toContainText("共享固定工作区");
  await expect(page.getByText("所属人发出的消息")).toBeVisible();
  await expect(page.getByText("我之前发出的消息")).toBeVisible();
  const ownerTurn = page.locator('[data-oc-turn-id="msg_owner_shared"]');
  const ownTurn = page.locator('[data-oc-turn-id="msg_reader_shared"]');
  await expect(ownerTurn.locator(".oc-user-message__sender")).toHaveText("会话所属人");
  await expect(ownTurn.locator(".oc-user-message__sender")).toHaveCount(0);
  const ownerAppearance = await ownerTurn.locator(".oc-user-message__bubble").evaluate((element) => {
    const style = getComputedStyle(element);
    return { backgroundColor: style.backgroundColor, borderStyle: style.borderStyle };
  });
  const ownAppearance = await ownTurn.locator(".oc-user-message__bubble").evaluate((element) => {
    const style = getComputedStyle(element);
    return { backgroundColor: style.backgroundColor, borderStyle: style.borderStyle };
  });
  expect(ownerAppearance).toEqual({ backgroundColor: "rgb(222, 217, 246)", borderStyle: "none" });
  expect(ownAppearance).toEqual({ backgroundColor: "rgb(178, 237, 223)", borderStyle: "none" });
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await expect(page.locator(".figma-chat-textarea")).toHaveAttribute("title", "当前分享权限为只读，不能修改工作区或发送消息。");
  await expect(page.getByTestId("manage-session-share")).toHaveCount(0);
  const newConversationButton = page.getByRole("button", { name: "新建对话" });
  await expect(newConversationButton).toBeEnabled();

  await expect.poll(() => shareHeaderRequests.some((request) =>
    request.path === "/api/internal/platform/opencode-runtime/sessions/ses_shared_readonly/messages"
      && request.shareId === "shr_readonly")).toBe(true);
  expect(shareHeaderRequests.every((request) => request.shareId === "shr_readonly")).toBe(true);

  await newConversationButton.click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByTestId("header-fixed-share-context")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "新建对话" })).toBeVisible();
});

test("session share model picker selects from the fixed owner workspace catalog", async ({ page }) => {
  const runtimeCatalogRequests: Array<{ path: string; workspaceId: string | null; shareId: string | null }> = [];
  const sharedSession = {
    ...session(),
    sessionId: "ses_shared_model",
    workspaceId: "wrk_shared_model",
    title: "共享模型会话"
  };
  await mockBackendApi(page, {
    authUser: { userId: "usr_writer", username: "协作者", unifiedAuthId: "ucid_writer", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_model", name: "所属人固定工作区" }],
    sessions: [sharedSession],
    sessionShareAccess: sessionShareAccess({
      actorUserId: "usr_writer",
      actorUnifiedAuthId: "ucid_writer",
      actorUsername: "协作者",
      sessionId: "ses_shared_model",
      workspaceId: "wrk_shared_model",
      canChat: true
    }),
    sessionShareRuntimeStates: [sessionShareRuntimeState({
      sessionId: "ses_shared_model",
      workspaceId: "wrk_shared_model",
      canChat: true
    })],
    sessionMessagesBySessionId: { ses_shared_model: [] },
    models: [{ id: "shared-model", providerId: "owner-provider", name: "所属人模型" }],
    providers: [{ id: "owner-provider", name: "所属人 Provider", status: "ready" }],
    runtimeCatalogRequests
  });

  await page.goto("/s/shr_model", { waitUntil: "domcontentloaded" });
  await expect(page.getByTestId("header-fixed-share-context")).toContainText("所属人固定工作区");
  await page.getByRole("button", { name: "切换模型" }).click();
  await expect(page.locator(".figma-chat-model-group-title", { hasText: "所属人 Provider" })).toBeVisible();
  const ownerModel = page.locator(".figma-chat-model-option-item", { hasText: "所属人模型" });
  await expect(ownerModel).toBeVisible();
  await ownerModel.click();
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("所属人模型");
  await expect.poll(() => runtimeCatalogRequests).toEqual(expect.arrayContaining([
    { path: "/api/internal/platform/opencode-runtime/models", workspaceId: "wrk_shared_model", shareId: "shr_model" },
    { path: "/api/internal/platform/opencode-runtime/providers", workspaceId: "wrk_shared_model", shareId: "shr_model" }
  ]));
});

test("session share busy run blocks every participant and only sender can stop", async ({ page }) => {
  const activeRun = {
    runId: "run_shared_busy",
    sessionId: "ses_shared_readonly",
    workspaceId: "wrk_shared_readonly",
    status: "RUNNING",
    triggeredByUserId: "usr_owner",
    messageSenderUserId: "usr_writer",
    messageSenderUnifiedAuthId: "ucid_writer",
    messageSentBySharedUser: true,
    createdAt: "2026-08-09T01:00:00Z",
    updatedAt: "2026-08-09T01:00:01Z"
  };
  await mockBackendApi(page, {
    authUser: { userId: "usr_reader", username: "阅读者", unifiedAuthId: "ucid_reader", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_readonly", name: "共享固定工作区" }],
    sessions: [{
      ...session(),
      sessionId: "ses_shared_readonly",
      workspaceId: "wrk_shared_readonly",
      title: "共享运行中会话"
    }],
    sessionShareAccess: sessionShareAccess({ canChat: true }),
    sessionShareRuntimeStates: [sessionShareRuntimeState({ canChat: true, activeRun })],
    activeRun,
    runEventsByRunId: { run_shared_busy: [] },
    runsByRunId: { run_shared_busy: activeRun },
    sessionMessagesBySessionId: { ses_shared_readonly: [] }
  });

  await page.goto("/s/shr_busy", { waitUntil: "domcontentloaded" });
  const stop = page.getByRole("button", { name: "停止执行" });
  await expect(stop).toBeVisible();
  await expect(stop).toBeDisabled();
  await expect(stop).toHaveAttribute("title", "仅会话所属人或本次消息发送人可以停止");
  await expect(page.getByRole("button", { name: "发送" })).toHaveCount(0);
});

test("session share sender can edit and resend their last message", async ({ page }) => {
  const runResendRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    runResendRequests,
    authUser: { userId: "usr_writer", username: "协作者", unifiedAuthId: "ucid_writer", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_resend", name: "共享撤回工作区" }],
    sessions: [{
      ...session(),
      sessionId: "ses_shared_resend",
      workspaceId: "wrk_shared_resend",
      title: "共享撤回权限会话"
    }],
    sessionShareAccess: sessionShareAccess({
      shareId: "shr_resend_member",
      actorUserId: "usr_writer",
      actorUnifiedAuthId: "ucid_writer",
      actorUsername: "协作者",
      sessionId: "ses_shared_resend",
      workspaceId: "wrk_shared_resend",
      canChat: true,
      ownerAccess: false
    }),
    sessionShareRuntimeStates: [sessionShareRuntimeState({
      shareId: "shr_resend_member",
      sessionId: "ses_shared_resend",
      workspaceId: "wrk_shared_resend",
      canChat: true
    })],
    sessionMessagesBySessionId: {
      ses_shared_resend: [{
        messageId: "msg_shared_member_source",
        remoteMessageId: "msg_remote_shared_member_source",
        sessionId: "ses_shared_resend",
        role: "USER",
        content: "协作者发送的最后一条消息",
        senderUserId: "usr_writer",
        senderUnifiedAuthId: "ucid_writer",
        sentBySharedUser: true,
        createdAt: "2026-08-09T01:00:00Z",
        runId: "run_history"
      }]
    },
    historyRun: {
      runId: "run_history",
      sessionId: "ses_shared_resend",
      workspaceId: "wrk_shared_resend",
      status: "FAILED",
      createdAt: "2026-08-09T01:00:00Z",
      updatedAt: "2026-08-09T01:01:00Z",
      messageSenderUserId: "usr_writer"
    },
    runEventsByRunId: { run_resend_replacement: [] }
  });

  await page.goto("/s/shr_resend_member", { waitUntil: "domcontentloaded" });
  await expect(page.getByText("协作者发送的最后一条消息")).toBeVisible();
  await page.getByRole("button", { name: "撤销重发最后一条消息" }).click();
  const resendComposer = page.getByPlaceholder("修改上一条消息后发送");
  await expect(resendComposer).toHaveValue("协作者发送的最后一条消息");
  await resendComposer.fill("协作者修改后的新消息");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runResendRequests.length).toBe(1);
  expect(runResendRequests[0]).toMatchObject({
    expectedRemoteMessageId: "msg_remote_shared_member_source",
    expectedRunId: "run_history",
    editedPrompt: "协作者修改后的新消息"
  });
  await expect(page.getByText("协作者修改后的新消息")).toBeVisible();
  await expect(page.getByText("协作者发送的最后一条消息")).toHaveCount(0);
  await expect(page.getByTestId("oc-user-message")).toHaveCount(1);
});

test("session share reconciles a disappeared active run without requiring refresh", async ({ page }) => {
  const runDetailRequests: string[] = [];
  const activeRun = {
    runId: "run_shared_terminal_sync",
    sessionId: "ses_shared_terminal_sync",
    workspaceId: "wrk_shared_terminal_sync",
    status: "RUNNING",
    triggeredByUserId: "usr_owner",
    messageSenderUserId: "usr_owner",
    createdAt: "2026-08-10T01:00:00Z",
    updatedAt: "2026-08-10T01:00:01Z"
  };
  const runtimeStates: Array<Record<string, unknown>> = [sessionShareRuntimeState({
    shareId: "shr_terminal_sync",
    sessionId: "ses_shared_terminal_sync",
    workspaceId: "wrk_shared_terminal_sync",
    canChat: true,
    activeRun,
    generatedAt: "2026-08-10T01:00:02Z"
  })];
  const runsByRunId: Record<string, Record<string, unknown>> = {
    run_shared_terminal_sync: activeRun
  };
  await mockBackendApi(page, {
    authUser: { userId: "usr_reader", username: "阅读者", unifiedAuthId: "ucid_reader", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_terminal_sync", name: "共享终态工作区" }],
    sessions: [{
      ...session(),
      sessionId: "ses_shared_terminal_sync",
      workspaceId: "wrk_shared_terminal_sync",
      title: "共享终态同步会话"
    }],
    sessionShareAccess: sessionShareAccess({
      shareId: "shr_terminal_sync",
      sessionId: "ses_shared_terminal_sync",
      workspaceId: "wrk_shared_terminal_sync",
      canChat: true
    }),
    sessionShareRuntimeStates: runtimeStates,
    activeRun,
    runEventsByRunId: { run_shared_terminal_sync: [] },
    runsByRunId,
    runDetailRequests,
    sessionMessagesBySessionId: {
      ses_shared_terminal_sync: [
        {
          messageId: "msg_shared_terminal_user",
          remoteMessageId: "msg_remote_shared_terminal_user",
          sessionId: "ses_shared_terminal_sync",
          role: "USER",
          content: "开始共享任务",
          senderUserId: "usr_owner",
          createdAt: "2026-08-10T01:00:00Z",
          runId: "run_shared_terminal_sync"
        },
        {
          messageId: "msg_shared_terminal_answer",
          remoteMessageId: "msg_remote_shared_terminal_answer",
          sessionId: "ses_shared_terminal_sync",
          role: "ASSISTANT",
          content: "共享回答已经输出",
          createdAt: "2026-08-10T01:00:01Z",
          runId: "run_shared_terminal_sync",
          parts: [{
            partId: "part_shared_terminal_answer",
            type: "text",
            text: "共享回答已经输出",
            status: "running"
          }]
        }
      ]
    }
  });

  await page.goto("/s/shr_terminal_sync", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("button", { name: "停止执行" })).toBeVisible();
  await expect(page.getByText("共享回答已经输出")).toBeVisible();
  await expect(page.getByText("生成中", { exact: true })).toBeVisible();

  runsByRunId.run_shared_terminal_sync = {
    ...activeRun,
    status: "SUCCEEDED",
    updatedAt: "2026-08-10T01:00:04Z"
  };
  runtimeStates.splice(0, runtimeStates.length, sessionShareRuntimeState({
    shareId: "shr_terminal_sync",
    sessionId: "ses_shared_terminal_sync",
    workspaceId: "wrk_shared_terminal_sync",
    canChat: true,
    activeRun: null,
    generatedAt: "2026-08-10T01:00:05Z"
  }));

  await expect.poll(() => runDetailRequests).toContain("run_shared_terminal_sync");
  await expect(page.getByRole("button", { name: "停止执行" })).toHaveCount(0);
  await expect(page.getByText("生成中", { exact: true })).toHaveCount(0);
  await expect(page.getByText("共享回答已经输出")).toBeVisible();
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("继续共享任务");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled({ timeout: 10_000 });
});

test("session share refreshes a compacted summary when the session revision changes", async ({ page }) => {
  const sessionMessageRequests: string[] = [];
  const runtimeStates: Array<Record<string, unknown>> = [sessionShareRuntimeState({
    shareId: "shr_compaction_sync",
    sessionId: "ses_shared_compaction_sync",
    workspaceId: "wrk_shared_compaction_sync",
    canChat: true,
    sessionUpdatedAt: "2026-08-10T02:00:00Z",
    generatedAt: "2026-08-10T02:00:01Z"
  })];
  const sessionMessagesBySessionId: Record<string, Array<Record<string, unknown>>> = {
    ses_shared_compaction_sync: []
  };
  await mockBackendApi(page, {
    authUser: { userId: "usr_reader", username: "阅读者", unifiedAuthId: "ucid_reader", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_compaction_sync", name: "共享压缩工作区" }],
    sessions: [{
      ...session(),
      sessionId: "ses_shared_compaction_sync",
      workspaceId: "wrk_shared_compaction_sync",
      title: "共享压缩同步会话"
    }],
    sessionShareAccess: sessionShareAccess({
      shareId: "shr_compaction_sync",
      sessionId: "ses_shared_compaction_sync",
      workspaceId: "wrk_shared_compaction_sync",
      canChat: true
    }),
    sessionShareRuntimeStates: runtimeStates,
    sessionMessagesBySessionId,
    sessionMessageRequests
  });

  await page.goto("/s/shr_compaction_sync", { waitUntil: "domcontentloaded" });
  await expect(page.getByTestId("compaction-part-prt-shared-compaction")).toHaveCount(0);
  await expect.poll(() => sessionMessageRequests.length).toBeGreaterThanOrEqual(1);
  const initialMessageRequestCount = sessionMessageRequests.length;

  const compactionPrelude = [
    {
      messageId: "msg_shared_compaction",
      sessionId: "ses_shared_compaction_sync",
      role: "ASSISTANT",
      content: "",
      createdAt: "2026-08-10T02:00:02Z",
      parts: [{
        partId: "prt-shared-compaction",
        type: "compaction",
        auto: false,
        overflow: false
      }]
    },
    {
      messageId: "msg_shared_compaction_envelope",
      sessionId: "ses_shared_compaction_sync",
      role: "USER",
      content: "",
      createdAt: "2026-08-10T02:00:02.500Z",
      parts: []
    }
  ];
  sessionMessagesBySessionId.ses_shared_compaction_sync = compactionPrelude;
  runtimeStates.splice(0, runtimeStates.length, sessionShareRuntimeState({
    shareId: "shr_compaction_sync",
    sessionId: "ses_shared_compaction_sync",
    workspaceId: "wrk_shared_compaction_sync",
    canChat: true,
    sessionUpdatedAt: "2026-08-10T02:00:03Z",
    generatedAt: "2026-08-10T02:00:03Z"
  }));

  await expect.poll(() => sessionMessageRequests.length).toBeGreaterThan(initialMessageRequestCount);
  await expect(page.getByTestId("compaction-part-prt-shared-compaction")).toHaveCount(0);
  await expect(page.getByText("上下文压缩中", { exact: true })).toHaveCount(0);

  sessionMessagesBySessionId.ses_shared_compaction_sync = [
    ...compactionPrelude,
    {
      messageId: "msg_shared_compaction_summary",
      sessionId: "ses_shared_compaction_sync",
      role: "ASSISTANT",
      content: "## Objective\n继续共享任务\n## Next Move\n验证同步",
      createdAt: "2026-08-10T02:00:03Z",
      parts: [{
        partId: "prt-shared-compaction-step-start",
        type: "step-start"
      }, {
        partId: "prt-shared-compaction-reasoning",
        type: "reasoning",
        text: "整理共享对话",
        status: "completed"
      }, {
        partId: "prt-shared-compaction-summary",
        type: "text",
        text: "## Objective\n继续共享任务\n## Next Move\n验证同步"
      }, {
        partId: "prt-shared-compaction-step-finish",
        type: "step-finish",
        reason: "stop"
      }]
    }
  ];
  runtimeStates.splice(0, runtimeStates.length, sessionShareRuntimeState({
    shareId: "shr_compaction_sync",
    sessionId: "ses_shared_compaction_sync",
    workspaceId: "wrk_shared_compaction_sync",
    canChat: true,
    sessionUpdatedAt: "2026-08-10T02:00:04Z",
    generatedAt: "2026-08-10T02:00:05Z"
  }));

  const compaction = page.getByTestId("compaction-part-prt-shared-compaction");
  await expect(compaction).toBeVisible({ timeout: 10_000 });
  await expect(compaction.getByRole("button", { name: "展开上下文压缩详情" })).toContainText("上下文已手动压缩");
  await expect(compaction).not.toContainText("当前目标");
  await expect(page.getByText("继续共享任务", { exact: true })).toHaveCount(0);
  await compaction.getByRole("button", { name: "展开上下文压缩详情" }).click();
  await expect(compaction).toContainText("当前目标");
  await expect(compaction).toContainText("继续共享任务");
  await expect(compaction).toContainText("下一步");
  await expect(compaction).not.toContainText("这不是新的回答");
});

test("session share synchronizes an edited resend after the backend commits its message change", async ({ page }) => {
  const sessionMessageRequests: string[] = [];
  const sessionTreeRequests: string[] = [];
  const activeRun = {
    runId: "run_shared_resend_replacement",
    sessionId: "ses_shared_resend_revision",
    workspaceId: "wrk_shared_resend_revision",
    status: "RUNNING",
    triggeredByUserId: "usr_owner",
    messageSenderUserId: "usr_wr",
    messageSenderUnifiedAuthId: "wr",
    messageSentBySharedUser: true,
    resend: {
      resendId: "rsd_shared_resend_revision",
      trigger: "MANUAL",
      totalAttempt: 1,
      automaticAttempt: 0,
      automaticLimit: 3,
      status: "DISPATCHED",
      executeAt: "2026-08-10T03:00:02Z",
      sourceRunId: "run_shared_resend_source",
      replacementRunId: "run_shared_resend_replacement",
      requesterUserId: "usr_wr",
      requesterUsername: "wr",
      requesterUnifiedAuthId: "wr",
      requestedBySharedUser: true
    },
    createdAt: "2026-08-10T03:00:02Z",
    updatedAt: "2026-08-10T03:00:03Z"
  };
  const runtimeStates: Array<Record<string, unknown>> = [sessionShareRuntimeState({
    shareId: "shr_resend_revision",
    sessionId: "ses_shared_resend_revision",
    workspaceId: "wrk_shared_resend_revision",
    canChat: true,
    sessionUpdatedAt: "2026-08-10T03:00:00Z",
    generatedAt: "2026-08-10T03:00:01Z"
  })];
  const earlierMessages = Array.from({ length: 12 }, (_, index) => ([{
    messageId: `msg_shared_resend_history_user_${index}`,
    sessionId: "ses_shared_resend_revision",
    role: "USER",
    content: `历史问题 ${index + 1}`,
    createdAt: `2026-08-10T02:${String(index).padStart(2, "0")}:00Z`,
    runId: `run_shared_resend_history_${index}`
  }, {
    messageId: `msg_shared_resend_history_answer_${index}`,
    sessionId: "ses_shared_resend_revision",
    role: "ASSISTANT",
    content: `历史回答 ${index + 1}`,
    createdAt: `2026-08-10T02:${String(index).padStart(2, "0")}:01Z`,
    runId: `run_shared_resend_history_${index}`
  }])).flat();
  const sessionMessagesBySessionId: Record<string, Array<Record<string, unknown>>> = {
    ses_shared_resend_revision: [...earlierMessages, {
      messageId: "msg_shared_resend_old",
      remoteMessageId: "msg_remote_shared_resend_old",
      sessionId: "ses_shared_resend_revision",
      role: "USER",
      content: "仅答复 OK",
      senderUserId: "usr_wr",
      senderUsername: "wr",
      senderUnifiedAuthId: "wr",
      sentBySharedUser: true,
      createdAt: "2026-08-10T03:00:00Z",
      runId: "run_shared_resend_source"
    }, {
      messageId: "msg_shared_resend_old_answer",
      remoteMessageId: "msg_remote_shared_resend_old_answer",
      sessionId: "ses_shared_resend_revision",
      role: "ASSISTANT",
      content: "OK",
      createdAt: "2026-08-10T03:00:01Z",
      runId: "run_shared_resend_source"
    }]
  };
  const runsByRunId: Record<string, Record<string, unknown>> = {
    run_shared_resend_source: {
      ...activeRun,
      runId: "run_shared_resend_source",
      status: "SUCCEEDED",
      createdAt: "2026-08-10T03:00:00Z",
      updatedAt: "2026-08-10T03:00:01Z"
    },
    run_shared_resend_replacement: activeRun
  };
  await mockBackendApi(page, {
    authUser: { userId: "usr_reader", username: "观察者", unifiedAuthId: "ucid_reader", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_resend_revision", name: "共享重发工作区" }],
    sessions: [{
      ...session(),
      sessionId: "ses_shared_resend_revision",
      workspaceId: "wrk_shared_resend_revision",
      title: "共享重发同步会话"
    }],
    sessionShareAccess: sessionShareAccess({
      shareId: "shr_resend_revision",
      actorUserId: "usr_reader",
      actorUnifiedAuthId: "ucid_reader",
      actorUsername: "观察者",
      executionOwnerUserId: "usr_owner",
      sessionId: "ses_shared_resend_revision",
      workspaceId: "wrk_shared_resend_revision",
      canChat: true,
      ownerAccess: false,
      participants: [{
        userId: "usr_owner",
        unifiedAuthId: "DEV_888888888",
        username: "888888888",
        owner: true,
        canChat: true,
        status: "OWNER"
      }, {
        userId: "usr_wr",
        unifiedAuthId: "wr",
        username: "wr",
        owner: false,
        canChat: true,
        status: "ACTIVE"
      }, {
        userId: "usr_reader",
        unifiedAuthId: "ucid_reader",
        username: "观察者",
        owner: false,
        canChat: true,
        status: "ACTIVE"
      }]
    }),
    sessionShareRuntimeStates: runtimeStates,
    sessionMessagesBySessionId,
    sessionMessageRequests,
    sessionTreeRequests,
    runsByRunId
  });

  await page.goto("/s/shr_resend_revision", { waitUntil: "domcontentloaded" });
  await expect(page.getByText("仅答复 OK", { exact: true })).toBeVisible();
  await expect.poll(() => sessionMessageRequests.length).toBeGreaterThanOrEqual(1);
  const initialMessageRequestCount = sessionMessageRequests.length;
  const initialSessionTreeRequestCount = sessionTreeRequests.length;
  await startHistoryLoadingObservation(page);
  const scrollTopBeforeResend = await page.locator(".figma-chat-scroll").evaluate((element) => {
    const viewport = element as HTMLElement;
    viewport.scrollTop = Math.min(160, Math.max(0, viewport.scrollHeight - viewport.clientHeight));
    return viewport.scrollTop;
  });
  expect(scrollTopBeforeResend).toBeGreaterThan(0);

  // 后端只在替代 USER 与 Session 修订提交后发出变化信号，前端无需猜测落库时机。
  sessionMessagesBySessionId.ses_shared_resend_revision = [...earlierMessages, {
    messageId: "msg_shared_resend_new",
    remoteMessageId: "msg_remote_shared_resend_new",
    sessionId: "ses_shared_resend_revision",
    role: "USER",
    content: "仅答复 123",
    senderUserId: "usr_wr",
    senderUsername: "wr",
    senderUnifiedAuthId: "wr",
    sentBySharedUser: true,
    createdAt: "2026-08-10T03:00:02Z",
    runId: "run_shared_resend_replacement",
    resend: activeRun.resend
  }];
  runtimeStates.splice(0, runtimeStates.length, sessionShareRuntimeState({
    shareId: "shr_resend_revision",
    sessionId: "ses_shared_resend_revision",
    workspaceId: "wrk_shared_resend_revision",
    canChat: true,
    activeRun,
    sessionUpdatedAt: "2026-08-10T03:00:03Z",
    generatedAt: "2026-08-10T03:00:04Z"
  }));

  await expect.poll(() => sessionMessageRequests.length).toBeGreaterThan(initialMessageRequestCount);
  expect(sessionMessageRequests.slice(initialMessageRequestCount)).toEqual([
    "/api/internal/platform/opencode-runtime/sessions/ses_shared_resend_revision/messages?page=1&size=100&refresh=false"
  ]);

  await expect(page.getByText("仅答复 OK", { exact: true })).toHaveCount(0);
  await expect(page.getByText("仅答复 123", { exact: true })).toHaveCount(1);
  await expect(page.getByText("wr", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "停止执行" })).toBeVisible();
  expect(sessionTreeRequests).toHaveLength(initialSessionTreeRequestCount);
  expect(await historyLoadingObserved(page)).toBe(false);
  await expect.poll(() => page.locator(".figma-chat-scroll").evaluate((element) =>
    (element as HTMLElement).scrollTop
  )).toBe(scrollTopBeforeResend);

  runsByRunId.run_shared_resend_replacement = {
    ...activeRun,
    status: "SUCCEEDED",
    updatedAt: "2026-08-10T03:00:05Z"
  };
  runtimeStates.splice(0, runtimeStates.length, sessionShareRuntimeState({
    shareId: "shr_resend_revision",
    sessionId: "ses_shared_resend_revision",
    workspaceId: "wrk_shared_resend_revision",
    canChat: true,
    activeRun: null,
    sessionUpdatedAt: "2026-08-10T03:00:03Z",
    generatedAt: "2026-08-10T03:00:06Z"
  }));

  await expect(page.getByRole("button", { name: "停止执行" })).toHaveCount(0);
  await expect(page.getByText("生成中", { exact: true })).toHaveCount(0);
  await expect(page.getByText("仅答复 123", { exact: true })).toBeVisible();
});

test("session share participant receives the owner's authoritative user message without an empty bubble", async ({ page }) => {
  const activeRun = {
    runId: "run_shared_owner_live",
    sessionId: "ses_shared_owner_live",
    workspaceId: "wrk_shared_owner_live",
    status: "RUNNING",
    triggeredByUserId: "usr_owner",
    messageSenderUserId: "usr_owner",
    messageSenderUnifiedAuthId: "ucid_owner",
    messageSentBySharedUser: false,
    createdAt: "2026-08-10T01:10:00Z",
    updatedAt: "2026-08-10T01:10:01Z"
  };
  await installAuthenticatedRunEventFetchStream(page, {
    run_shared_owner_live: [
      {
        delayMs: 20,
        events: [{
          eventId: "evt_shared_empty_envelope",
          seq: 0,
          type: "message.updated",
          payload: {
            message: {
              id: "msg_shared_owner_live",
              role: "user"
            }
          }
        }]
      },
      {
        releaseKey: "shared-platform-input",
        events: [{
          eventId: "evt_shared_platform_input",
          seq: 0,
          type: "message.updated",
          payload: {
            messageId: "msg_shared_owner_live",
            platformMessageId: "smsg_shared_owner_live",
            message: {
              id: "msg_shared_owner_live",
              platformMessageId: "smsg_shared_owner_live",
              role: "user",
              text: "A 发出的多人同步消息",
              senderUserId: "usr_owner",
              senderUnifiedAuthId: "ucid_owner",
              sentBySharedUser: false
            }
          }
        }]
      }
    ]
  });
  await mockBackendApi(page, {
    authUser: { userId: "usr_reader", username: "阅读者", unifiedAuthId: "ucid_reader", roles: ["USER"] },
    workspaces: [{ ...workspace(), workspaceId: "wrk_shared_owner_live", name: "共享同步工作区" }],
    sessions: [{
      ...session(),
      sessionId: "ses_shared_owner_live",
      workspaceId: "wrk_shared_owner_live",
      title: "多人绝对同步会话"
    }],
    sessionShareAccess: sessionShareAccess({
      shareId: "shr_owner_live",
      actorUserId: "usr_reader",
      actorUnifiedAuthId: "ucid_reader",
      actorUsername: "阅读者",
      sessionId: "ses_shared_owner_live",
      workspaceId: "wrk_shared_owner_live",
      canChat: true
    }),
    sessionShareRuntimeStates: [sessionShareRuntimeState({
      shareId: "shr_owner_live",
      sessionId: "ses_shared_owner_live",
      workspaceId: "wrk_shared_owner_live",
      canChat: true,
      activeRun
    })],
    activeRun,
    runsByRunId: { run_shared_owner_live: activeRun },
    sessionMessagesBySessionId: { ses_shared_owner_live: [] }
  });

  await page.goto("/s/shr_owner_live", { waitUntil: "domcontentloaded" });
  await expect.poll(() => page.evaluate(() => (
    (window as Window & { __titleWatchRunStreams?: Array<{ runId: string }> })
      .__titleWatchRunStreams?.some((item) => item.runId === "run_shared_owner_live") ?? false
  ))).toBe(true);
  await expect.poll(() => page.evaluate(() => (
    (window as Window & {
      __titleWatchRunStreams?: Array<{ runId: string; emittedEventIds: string[] }>;
    }).__titleWatchRunStreams
      ?.find((item) => item.runId === "run_shared_owner_live")
      ?.emittedEventIds.includes("evt_shared_empty_envelope") ?? false
  ))).toBe(true);
  await expect(page.locator('[data-oc-turn-id="msg_shared_owner_live"]')).toHaveCount(0);

  await expect.poll(() => page.evaluate(() => (
    (window as Window & { __releaseRunEventBatch?: (releaseKey: string) => boolean })
      .__releaseRunEventBatch?.("shared-platform-input") ?? false
  ))).toBe(true);

  const ownerTurn = page.locator('[data-oc-turn-id="msg_shared_owner_live"]');
  await expect(ownerTurn).toBeVisible({ timeout: 5_000 });
  await expect(ownerTurn.locator(".oc-user-message__sender")).toHaveText("会话所属人");
  await expect(ownerTurn.locator(".oc-user-message__bubble")).toHaveText("A 发出的多人同步消息");
  await expect(page.locator('[data-oc-turn-id="msg_shared_owner_live"]')).toHaveCount(1);
});

test("session share invalid page and owner link redirect remain isolated", async ({ page, context }) => {
  await mockBackendApi(page, {
    missingSessionsNotFound: true,
    sessionShareAccessFailure: {
      status: 410,
      code: "SESSION_SHARE_EXPIRED",
      message: "会话分享已失效",
      details: { reason: "REMOVED" }
    }
  });
  await page.goto("/s/shr_removed", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("heading", { name: "无法打开分享会话" })).toBeVisible();
  await expect(page.getByText("你已不在该分享会话中")).toBeVisible();

  const ownerPage = await context.newPage();
  const ownerHeaderRequests: Array<{ method: string; path: string; shareId: string }> = [];
  await mockBackendApi(ownerPage, {
    sessions: [{ ...session(), sessionId: "ses_owner_link", title: "所属人普通会话" }],
    sessionShareAccess: sessionShareAccess({
      actorUserId: "usr_owner",
      actorUnifiedAuthId: "ucid_owner",
      actorUsername: "会话所属人",
      delegated: false,
      ownerAccess: true,
      canChat: true,
      sessionId: "ses_owner_link",
      workspaceId: "wrk_1234567890abcdef"
    }),
    sessionShareHeaderRequests: ownerHeaderRequests,
    sessionMessagesBySessionId: { ses_owner_link: [] }
  });
  await ownerPage.goto("/s/shr_owner", { waitUntil: "domcontentloaded" });
  await expect(ownerPage).toHaveURL(/\/?sessionId=ses_owner_link$/);
  await expect(ownerPage.getByTestId("header-fixed-share-context")).toHaveCount(0);
  await expect(ownerPage.getByTestId("header-context-rail")).toBeVisible();
  expect(ownerHeaderRequests.filter((request) => request.path.endsWith("/session-shares/access"))).toHaveLength(1);
  expect(ownerHeaderRequests.filter((request) => request.path.includes("/sessions/ses_owner_link"))).toHaveLength(0);
});

test("workbench opens a workspace file with mocked backend api", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "tests/checkout.spec.ts": "// nonempty workspace file\nexport const checkout = true;\n"
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);

  await expect(page.getByText("MIMO测试智能体")).toBeVisible();
  await expect(page.getByRole("button", { name: "关闭运行与终端" })).toBeVisible();
  const workspaceTreeRow = page.getByRole("button", { name: "tests", exact: true });
  await expect(workspaceTreeRow).toBeVisible();
  await expect.poll(() => workspaceTreeRow.evaluate((el) => getComputedStyle(el).height)).toBe("22px");
  await expect.poll(() => workspaceTreeRow.evaluate((el) => getComputedStyle(el).fontSize)).toBe("13px");
  const agentRootRow = page.locator(".agent-root-row").first();
  await expect(agentRootRow).toBeVisible();
  await expect.poll(() => agentRootRow.evaluate((el) => getComputedStyle(el).height)).toBe("22px");
  await page.getByRole("button", { name: "tests", exact: true }).click();
  await page.getByRole("button", { name: "checkout.spec.ts", exact: true }).click();
  await expect(page.getByRole("tab", { name: /checkout\.spec\.ts/ })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("textbox", { name: "Editor content" })).toBeVisible();
  await expect(page.locator(".monaco-editor")).toContainText("nonempty workspace file", { timeout: 10_000 });
  expect(fileReadRequests).toEqual([
    { workspaceId: "wrk_personal_default", path: "tests/checkout.spec.ts", attempt: 1 }
  ]);
});

test("workspace directory move keeps expanded descendants and reloads a pending child tab before reverse undo", async ({ page }) => {
  const workspaceMoveRequests: Array<{ workspaceId: string; sourcePath: string; targetPath: string }> = [];
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  const response = (entries: Array<Record<string, unknown>>) => ({ entries, warnings: [], truncated: false });
  await mockBackendApi(page, {
    workspaceMoveRequests,
    fileReadRequests,
    fileContents: { "src/nested/guide.md": "moved guide content" },
    fileReadDelays: { "src/nested/guide.md": [3000] },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    },
    workspaceViewLists: {
      "COMPOSITE::": response([
        workspaceViewDirectoryEntry("workspace:src-old", "src"),
        workspaceViewDirectoryEntry("workspace:archive", "archive")
      ]),
      "WORKSPACE::src": response([
        workspaceViewDirectoryEntry("workspace:src-nested-old", "src/nested")
      ]),
      "WORKSPACE::src/nested": response([
        workspaceViewFileEntry("workspace:src-guide-old", "src/nested/guide.md")
      ])
    },
    workspaceViewListsAfterMoves: [
      {
        "COMPOSITE::": response([workspaceViewDirectoryEntry("workspace:archive", "archive")]),
        "WORKSPACE::archive": response([
          workspaceViewDirectoryEntry("workspace:archive-src-new", "archive/src")
        ]),
        "WORKSPACE::archive/src": response([
          workspaceViewDirectoryEntry("workspace:archive-src-nested-new", "archive/src/nested")
        ]),
        "WORKSPACE::archive/src/nested": response([
          workspaceViewFileEntry("workspace:archive-src-guide-new", "archive/src/nested/guide.md")
        ])
      },
      {
        "COMPOSITE::": response([
          workspaceViewDirectoryEntry("workspace:src-restored", "src"),
          workspaceViewDirectoryEntry("workspace:archive", "archive")
        ]),
        "WORKSPACE::archive": response([]),
        "WORKSPACE::src": response([
          workspaceViewDirectoryEntry("workspace:src-nested-restored", "src/nested")
        ]),
        "WORKSPACE::src/nested": response([
          workspaceViewFileEntry("workspace:src-guide-restored", "src/nested/guide.md")
        ])
      }
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "src", exact: true }).click();
  await page.getByRole("button", { name: "nested", exact: true }).click();
  await page.getByRole("button", { name: "guide.md", exact: true }).click();
  await expect.poll(() => fileReadRequests.some((request) => request.path === "src/nested/guide.md")).toBe(true);

  const source = page.getByRole("button", { name: "src", exact: true });
  const target = page.getByRole("button", { name: "archive", exact: true });
  const dataTransfer = await page.evaluateHandle(() => new DataTransfer());
  await source.dispatchEvent("dragstart", { dataTransfer });
  await target.dispatchEvent("dragover", { dataTransfer });
  await target.dispatchEvent("drop", { dataTransfer });
  await source.dispatchEvent("dragend", { dataTransfer });

  await expect.poll(() => workspaceMoveRequests).toEqual([
    { workspaceId: "wrk_personal_default", sourcePath: "src", targetPath: "archive/src" }
  ]);
  await expect(page.getByRole("button", { name: "src", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "nested", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "guide.md", exact: true })).toBeVisible();
  await expect(page.locator(".monaco-editor")).toContainText("moved guide content", { timeout: 10_000 });

  await page.getByRole("button", { name: "src", exact: true }).click({ button: "right" });
  await page.getByRole("menuitem", { name: /撤销/ }).click();
  await expect.poll(() => workspaceMoveRequests).toEqual([
    { workspaceId: "wrk_personal_default", sourcePath: "src", targetPath: "archive/src" },
    { workspaceId: "wrk_personal_default", sourcePath: "archive/src", targetPath: "src" }
  ]);
  await expect(page.getByRole("button", { name: "src", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "nested", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "guide.md", exact: true })).toBeVisible();
});

test("workspace tree merges references with source colors and exposes non-merged aliases", async ({ page }) => {
  await mockBackendApi(page, {
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    },
    workspaceViewLists: {
      "COMPOSITE::": {
        entries: [
          {
            id: "composite:docs",
            path: "docs",
            name: "docs",
            directory: true,
            size: 0,
            locator: { kind: "COMPOSITE", path: "docs" },
            source: "MIXED",
            merged: true,
            collision: false,
            readonly: false,
            workspacePath: "docs",
            referenceAliases: ["docs-assets"]
          },
          {
            id: "reference:spec-assets",
            path: "spec-assets",
            name: "spec-assets",
            directory: true,
            size: 0,
            locator: { kind: "REFERENCE", path: "", referenceAlias: "spec-assets" },
            source: "REFERENCE",
            merged: false,
            collision: false,
            readonly: true,
            referenceAliases: ["spec-assets"]
          }
        ],
        warnings: [],
        truncated: false
      },
      "COMPOSITE::docs": {
        entries: [
          {
            id: "workspace:docs/local.md",
            path: "docs/local.md",
            name: "local.md",
            directory: false,
            size: 5,
            locator: { kind: "WORKSPACE", path: "docs/local.md" },
            source: "WORKSPACE",
            merged: false,
            collision: false,
            readonly: false,
            workspacePath: "docs/local.md",
            referenceAliases: []
          },
          {
            id: "reference:docs-assets:guide.md",
            path: "docs/guide.md",
            name: "guide.md",
            directory: false,
            size: 15,
            locator: { kind: "REFERENCE", path: "guide.md", referenceAlias: "docs-assets" },
            source: "REFERENCE",
            merged: true,
            collision: false,
            readonly: true,
            referenceAliases: ["docs-assets"]
          },
          {
            id: "reference:docs-assets:same.md",
            path: "docs/same.md",
            name: "same.md",
            directory: false,
            size: 9,
            locator: { kind: "REFERENCE", path: "same.md", referenceAlias: "docs-assets" },
            source: "REFERENCE",
            merged: true,
            collision: true,
            readonly: true,
            referenceAliases: ["docs-assets"]
          }
        ],
        warnings: [],
        truncated: false
      },
      "REFERENCE:spec-assets:": {
        entries: [
          {
            id: "reference:spec-assets:spec.md",
            path: "spec-assets/spec.md",
            name: "spec.md",
            directory: false,
            size: 13,
            locator: { kind: "REFERENCE", path: "spec.md", referenceAlias: "spec-assets" },
            source: "REFERENCE",
            merged: false,
            collision: false,
            readonly: true,
            referenceAliases: ["spec-assets"]
          }
        ],
        warnings: [],
        truncated: false
      }
    },
    workspaceViewContents: {
      "REFERENCE:docs-assets:guide.md": "reference guide",
      "REFERENCE:docs-assets:same.md": "collision",
      "REFERENCE:spec-assets:spec.md": "standalone spec"
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  const docs = page.getByRole("button", { name: "docs", exact: true });
  const alias = page.getByRole("button", { name: "spec-assets", exact: true });
  await expect(docs).toBeVisible();
  await expect(alias).toBeVisible();
  await expect(docs).not.toHaveClass(/is-reference-merged/);
  await expect(alias).not.toHaveClass(/is-reference-merged/);

  await docs.click();
  const guide = page.getByRole("button", { name: "guide.md", exact: true });
  await expect(guide).toHaveClass(/is-reference-merged/);
  await expect(page.getByRole("button", { name: "same.md", exact: true }))
    .toHaveClass(/is-reference-collision/);
  await guide.click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("reference guide");
  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("End");
  await page.keyboard.type(" must remain readonly");
  await expect(page.locator(".monaco-editor")).not.toContainText("must remain readonly");

  await alias.click();
  const standalone = page.getByRole("button", { name: "spec.md", exact: true });
  await expect(standalone).toBeVisible();
  await expect(standalone).not.toHaveClass(/is-reference-merged/);
});

test("Agent files open through the parent loader for public and workspace scopes", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    worktreeId?: string;
    attempt?: number;
    content?: string;
  }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "PUBLIC:agents/public-agent.md": "# public Agent content\n",
      "WORKSPACE:agents/workspace-agent.md": "# workspace Agent content\n"
    },
    agentFileReadDelays: {
      "PUBLIC:agents/public-agent.md": [500]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);
  const publicAgentsDirectory = page.getByRole("button", { name: "agents", exact: true });
  await expect(publicAgentsDirectory).toHaveCount(1);
  await publicAgentsDirectory.click();
  await page.getByRole("button", { name: "public-agent.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");
  await expect(page.locator(".monaco-editor")).toContainText("public Agent content", { timeout: 10_000 });

  await page.getByRole("button", { name: /应用级/ }).click();
  const agentDirectories = page.getByRole("button", { name: "agents", exact: true });
  await expect(agentDirectories).toHaveCount(2);
  await agentDirectories.nth(1).click();
  await page.getByRole("button", { name: "workspace-agent.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("workspace Agent content", { timeout: 10_000 });

  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.read")).toEqual([
    {
      op: "agent-config.read",
      scope: "PUBLIC",
      path: "agents/public-agent.md",
      workspaceId: undefined,
      worktreeId: undefined,
      attempt: 1
    },
    {
      op: "agent-config.read",
      scope: "WORKSPACE",
      path: "agents/workspace-agent.md",
      workspaceId: "wrk_personal_default",
      worktreeId: undefined,
      attempt: 1
    }
  ]);

  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("# workspace Agent updated");
  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => agentFileFrames.find((frame) => frame.op === "agent-config.write")).toMatchObject({
    op: "agent-config.write",
    scope: "WORKSPACE",
    path: "agents/workspace-agent.md",
    workspaceId: "wrk_personal_default",
    worktreeId: undefined
  });
});

test("application Agent update merges the feature commit even when the runtime is not ready", async ({ page }) => {
  const runtimeReloadRequests: string[] = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    processStatus: "NEEDS_INITIALIZATION",
    runtimeReloadRequests
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "Agents", exact: true }).click();
  await page.getByRole("button", { name: "Agent 配置更新（应用）" }).click();

  await expect.poll(() => runtimeReloadRequests).toEqual([
    "sync:psw_default"
  ]);
  await expect(page.getByText("应用个人配置已同步", { exact: true })).toBeVisible();
});

test("Agent loading distinguishes empty files, retries failures, and reuses loaded tab cache", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    attempt?: number;
  }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "WORKSPACE:agents/empty-agent.md": "",
      "WORKSPACE:agents/retry-agent.md": "# retry Agent succeeded"
    },
    agentFileReadFailureAttempts: {
      "WORKSPACE:agents/retry-agent.md": [1]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);
  await page.getByRole("button", { name: /应用级/ }).click();
  await page.getByRole("button", { name: "agents", exact: true }).last().click();
  await page.getByRole("button", { name: "empty-agent.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");

  const retryRow = page.getByRole("button", { name: "retry-agent.md", exact: true });
  await retryRow.click();
  await expect(page.getByText("读取文件失败", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "重试读取文件" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("retry Agent succeeded", { timeout: 10_000 });
  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("End");
  await page.keyboard.type(" and remains editable");
  await expect(page.locator(".monaco-editor")).toContainText("and remains editable");
  await page.locator(".ta-workbench-footer-save").click();
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);
  const savedMessage = page.getByRole("alert").filter({ hasText: "文件已保存" });
  await expect(savedMessage).toBeVisible();
  await savedMessage.locator(".el-message__closeBtn").click();
  await expect(savedMessage).toBeHidden();

  const retryReads = () => agentFileFrames.filter((frame) => (
    frame.op === "agent-config.read" && frame.path === "agents/retry-agent.md"
  ));
  await expect.poll(retryReads).toHaveLength(2);
  await page.getByRole("tab").filter({ hasText: "empty-agent.md" }).click();
  await page.getByRole("tab").filter({ hasText: "retry-agent.md" }).click();
  await page.waitForTimeout(50);
  expect(retryReads()).toHaveLength(2);

  // 文件树重复点击表示显式刷新，clean tab 应重新读取；顶部 tab 激活则只使用缓存。
  await retryRow.click();
  await expect.poll(retryReads).toHaveLength(3);
});

test("late Agent responses update only their own tab and same-path stale responses are discarded", async ({ page }) => {
  const agentFileFrames: Array<{ op: string; scope: string; path: string; attempt?: number }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "PUBLIC:agents/agent-a.md": "# Agent A fallback",
      "PUBLIC:agents/agent-b.md": "# Agent B response",
      "PUBLIC:agents/agent-same.md": "# Agent same fallback",
      "PUBLIC:agents/agent-closing.md": "# Agent must stay closed"
    },
    agentFileReadDelays: {
      "PUBLIC:agents/agent-a.md": [250],
      "PUBLIC:agents/agent-b.md": [20],
      "PUBLIC:agents/agent-same.md": [250, 20],
      "PUBLIC:agents/agent-closing.md": [250]
    },
    agentFileReadResponses: {
      "PUBLIC:agents/agent-a.md": ["# Agent A response"],
      "PUBLIC:agents/agent-same.md": ["# stale Agent response", "# newest Agent response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);
  await page.getByRole("button", { name: "agents", exact: true }).click();
  await page.getByRole("button", { name: "agent-a.md", exact: true }).click();
  await page.getByRole("tab").filter({ hasText: "agent-a.md" }).click();
  await page.getByRole("button", { name: "agent-b.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("Agent B response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).toContainText("Agent B response");
  await page.getByRole("tab").filter({ hasText: "agent-a.md" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("Agent A response");

  const sameRow = page.getByRole("button", { name: "agent-same.md", exact: true });
  await sameRow.click();
  await sameRow.click();
  await expect(page.locator(".monaco-editor")).toContainText("newest Agent response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale Agent response");

  await page.getByRole("button", { name: "agent-closing.md", exact: true }).click();
  const closingTab = page.getByRole("tab").filter({ hasText: "agent-closing.md" });
  await closingTab.getByRole("button", { name: "关闭标签" }).click();
  await page.waitForTimeout(300);
  await expect(closingTab).toHaveCount(0);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.read" && frame.path === "agents/agent-a.md")).toHaveLength(1);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.read" && frame.path === "agents/agent-same.md")).toHaveLength(2);
});

test("dirty Agent tabs and edits made during refresh are never overwritten", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    attempt?: number;
    content?: string;
  }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "WORKSPACE:agents/dirty-agent.md": "initial Agent disk content"
    },
    agentFileReadDelays: {
      "WORKSPACE:agents/dirty-agent.md": [0, 350]
    },
    agentFileReadResponses: {
      "WORKSPACE:agents/dirty-agent.md": ["initial Agent disk content", "stale Agent refresh response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);
  await page.getByRole("button", { name: /应用级/ }).click();
  await page.getByRole("button", { name: "agents", exact: true }).last().click();
  const row = page.getByRole("button", { name: "dirty-agent.md", exact: true });
  await row.click();
  await expect(page.locator(".monaco-editor")).toContainText("initial Agent disk content", { timeout: 10_000 });

  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("local dirty Agent content");
  await row.click();
  await page.waitForTimeout(50);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.read" && frame.path === "agents/dirty-agent.md")).toHaveLength(1);

  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(1);
  await row.click();
  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("saved while Agent refresh is pending");
  await expect(page.locator(".monaco-editor")).toContainText("saved while Agent refresh is pending");
  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(2);
  await page.waitForTimeout(400);
  await expect(page.locator(".monaco-editor")).toContainText("saved while Agent refresh is pending");
  await expect(page.locator(".monaco-editor")).not.toContainText("stale Agent refresh response");
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);
});

test("Agent refresh failures preserve cache and a missing clean file closes its tab", async ({ page }) => {
  const agentFileFrames: Array<{ op: string; scope: string; path: string; attempt?: number }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    agentFileFrames,
    agentFileContents: {
      "PUBLIC:agents/cached-agent.md": "stable Agent cache",
      "PUBLIC:agents/removed-agent.md": "Agent file before removal"
    },
    agentFileReadDelays: {
      // 第二次读取故意保持在途，确保第三次失败先返回，再验证迟到成功响应不会覆盖缓存。
      "PUBLIC:agents/cached-agent.md": [0, 2_000, 20]
    },
    agentFileReadFailureAttempts: {
      "PUBLIC:agents/cached-agent.md": [3]
    },
    agentFileReadNotFoundAttempts: {
      "PUBLIC:agents/removed-agent.md": [2]
    },
    agentFileReadResponses: {
      "PUBLIC:agents/cached-agent.md": ["stable Agent cache", "stale Agent cache refresh"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);
  await page.getByRole("button", { name: "agents", exact: true }).click();
  const cachedRow = page.getByRole("button", { name: "cached-agent.md", exact: true });
  const cachedReads = () => agentFileFrames.filter((frame) => (
    frame.op === "agent-config.read" && frame.path === "agents/cached-agent.md"
  ));
  await cachedRow.click();
  await expect(page.locator(".monaco-editor")).toContainText("stable Agent cache", { timeout: 10_000 });
  await expect.poll(cachedReads).toHaveLength(1);
  await cachedRow.click();
  await expect.poll(cachedReads).toHaveLength(2);
  await cachedRow.click();
  await expect.poll(cachedReads).toHaveLength(3);
  await expect(page.getByText(/刷新文件失败，已保留上次内容/)).toBeVisible();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("stable Agent cache");
  await page.waitForTimeout(2_050);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale Agent cache refresh");

  await page.getByRole("button", { name: "removed-agent.md", exact: true }).click();
  const removedTab = page.getByRole("tab").filter({ hasText: "removed-agent.md" });
  await expect(page.locator(".monaco-editor")).toContainText("Agent file before removal");
  await page.getByRole("button", { name: "刷新", exact: true }).click();
  await expect(removedTab).toHaveCount(0);
});

test("switching application context discards a loading Agent response", async ({ page }) => {
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      ...agentWorkspaceSetup().recentWorkspaces,
      app_coss: null
    },
    agentFileContents: {
      "WORKSPACE:agents/context-agent.md": "# stale Agent context response"
    },
    agentFileReadDelays: {
      "WORKSPACE:agents/context-agent.md": [300]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);
  await page.getByRole("button", { name: /应用级/ }).click();
  await page.getByRole("button", { name: "agents", exact: true }).last().click();
  await page.getByRole("button", { name: "context-agent.md", exact: true }).click();
  await page.getByRole("button", { name: "应用：F-GCMS", exact: true }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await page.waitForTimeout(350);
  await expect(page.getByRole("tab").filter({ hasText: "context-agent.md" })).toHaveCount(0);
  await expect(page.getByText("stale Agent context response")).toHaveCount(0);
});

test("switching public Agent routes settles an old load and allows retry after returning", async ({ page }) => {
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    authRoles: ["SUPER_ADMIN"],
    publicAgentRepositories: [
      publicAgentRepository("server-a", "backend-a"),
      publicAgentRepository("server-b", "backend-b")
    ],
    publicAgentWorktreesByServer: {
      "server-a": [publicAgentWorktree("server-a")],
      "server-b": [publicAgentWorktree("server-b")]
    },
    agentFileContents: {
      "PUBLIC:public-route.md": "# fallback public route"
    },
    agentFileReadDelays: {
      "PUBLIC:public-route.md": [2_000, 0]
    },
    agentFileReadResponses: {
      "PUBLIC:public-route.md": ["# stale server A response", "# fresh server A response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAgentsPanel(page);
  await page.getByRole("button", { name: "public-route.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");
  // 折叠目录可让路由切换只验证 tab 失效/重试，不等待新服务器的目录重载。
  await page.getByRole("button", { name: /公共级/ }).click();

  await page.getByRole("button", { name: "更多操作" }).hover();
  await expect(page.getByRole("button", { name: "创建公共 worktree" })).toBeVisible();
  await page.getByRole("button", { name: "切换公共 worktree" }).click();
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByLabel("服务器").selectOption("server-b");
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByRole("button", { name: "确定" }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "error");

  await page.getByRole("button", { name: "更多操作" }).hover();
  await page.getByRole("button", { name: "切换公共 worktree" }).click();
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByLabel("服务器").selectOption("server-a");
  await page.getByRole("dialog", { name: "切换公共 worktree" }).getByRole("button", { name: "确定" }).click();
  await page.getByRole("tab").filter({ hasText: "public-route.md" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("fresh server A response", { timeout: 10_000 });
  await page.waitForTimeout(2_050);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale server A response");
});

test("workspace file loading distinguishes an empty file and supports retry after an initial failure", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "docs/empty.md": "",
      "docs/retry.md": "# retry succeeded"
    },
    fileReadFailuresBeforeSuccess: { "docs/retry.md": 2 },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "empty.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");

  await page.getByRole("button", { name: "retry.md", exact: true }).click();
  await expect(page.getByText("读取文件失败", { exact: true })).toBeVisible();
  await page.getByRole("tab").filter({ hasText: "empty.md" }).click();
  await page.getByRole("tab").filter({ hasText: "retry.md" }).click();
  await expect(page.getByText("读取文件失败", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "重试读取文件" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("retry succeeded", { timeout: 10_000 });
  expect(fileReadRequests.filter((item) => item.path === "docs/retry.md")).toHaveLength(3);
});

test("initial file loading is not editable and applies the response readonly state", async ({ page }) => {
  const readonlyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_readonly_history",
    name: "只读历史工作区",
    rootPath: "/Users/huang/workspace/readonly-history",
    appId: "app_coss",
    versionId: "awv_readonly_history",
    applicationWorkspaceId: "awp_readonly_history"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileContents: { "docs/initial-readonly.md": "disk readonly content" },
    fileReadDelays: { "docs/initial-readonly.md": [250] },
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    workspaces: [workspace(), readonlyWorkspace],
    markRecentWorkspaces: { wrk_readonly_history: readonlyWorkspace },
    sessions: [{
      sessionId: "ses_readonly_history",
      workspaceId: "wrk_readonly_history",
      title: "只读历史会话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-08T08:00:00Z",
      updatedAt: "2026-07-08T09:00:00Z",
      workspaceContext: {
        appId: "app_coss",
        appName: "F-COSS",
        applicationWorkspaceId: "awp_readonly_history",
        workspaceName: "只读历史工作区",
        versionId: "awv_readonly_history",
        version: "20260708"
      }
    }],
    sessionMessages: [{
      messageId: "msg_readonly_history",
      sessionId: "ses_readonly_history",
      role: "USER",
      content: "只读历史会话",
      createdAt: "2026-07-08T08:00:00Z"
    }]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, "只读历史会话").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click({ force: true });
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect(page.getByRole("button", { name: "docs", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "initial-readonly.md", exact: true }).click();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");
  await expect(page.locator(".monaco-editor")).toHaveCount(0);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("disk readonly content");

  await page.getByRole("textbox", { name: "Editor content" }).focus();
  await page.keyboard.press("End");
  await page.keyboard.type(" must remain readonly");
  await expect(page.locator(".monaco-editor")).not.toContainText("must remain readonly");
});

test("late file responses update only their own tab and same-path stale responses are discarded", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "docs/a.md": "# A response",
      "docs/b.md": "# B response",
      "docs/same.md": "# fallback"
    },
    fileReadDelays: {
      "docs/a.md": [250],
      "docs/b.md": [20],
      "docs/same.md": [250, 20]
    },
    fileReadResponses: {
      "docs/same.md": ["# stale same-path response", "# newest same-path response"]
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "a.md", exact: true }).click();
  await page.getByRole("tab").filter({ hasText: "a.md" }).click();
  expect(fileReadRequests.filter((item) => item.path === "docs/a.md")).toHaveLength(1);
  await page.getByRole("button", { name: "b.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("B response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).toContainText("B response");
  await page.getByRole("tab").filter({ hasText: "a.md" }).click();
  await expect(page.locator(".monaco-editor")).toContainText("A response");
  expect(fileReadRequests.filter((item) => item.path === "docs/a.md")).toHaveLength(1);

  await page.getByRole("button", { name: "same.md", exact: true }).click();
  await page.getByRole("button", { name: "same.md", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("newest same-path response", { timeout: 10_000 });
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale same-path response");
  expect(fileReadRequests.filter((item) => item.path === "docs/same.md")).toHaveLength(2);
});

test("dirty tabs are never reread or overwritten while a read is pending", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: { "docs/dirty.md": "initial disk content" },
    fileReadDelays: { "docs/dirty.md": [0, 250] },
    fileReadResponses: { "docs/dirty.md": ["initial disk content", "new disk content"] },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const dirtyRow = page.getByRole("button", { name: "dirty.md", exact: true });
  await dirtyRow.click();
  await expect(page.locator(".monaco-editor")).toContainText("initial disk content", { timeout: 10_000 });

  await dirtyRow.click();
  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("local unsaved content");
  await expect(page.locator(".monaco-editor")).toContainText("local unsaved content");
  await page.waitForTimeout(300);
  await expect(page.locator(".monaco-editor")).toContainText("local unsaved content");

  const readsBeforeDirtyReopen = fileReadRequests.filter((item) => item.path === "docs/dirty.md").length;
  await dirtyRow.click();
  await page.waitForTimeout(50);
  expect(fileReadRequests.filter((item) => item.path === "docs/dirty.md")).toHaveLength(readsBeforeDirtyReopen);
});

test("a stale read cannot overwrite content edited and saved during refresh", async ({ page }) => {
  const fileWriteRequests: Array<{ workspaceId: string; path: string; content: string }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileWriteRequests,
    fileContents: { "docs/save-during-refresh.md": "base disk content" },
    fileReadDelays: { "docs/save-during-refresh.md": [0, 1_000] },
    fileReadResponses: {
      "docs/save-during-refresh.md": ["base disk content", "stale refresh response"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const row = page.getByRole("button", { name: "save-during-refresh.md", exact: true });
  await row.click();
  await expect(page.locator(".monaco-editor")).toContainText("base disk content", { timeout: 10_000 });

  await row.click();
  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("saved while refresh is pending");
  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => fileWriteRequests.length).toBe(1);
  expect(fileWriteRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    path: "docs/save-during-refresh.md"
  });
  expect(fileWriteRequests[0]?.content).toContain("saved while refresh is pending");
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);

  await page.waitForTimeout(1_100);
  await expect(page.locator(".monaco-editor")).toContainText("saved while refresh is pending");
  await expect(page.locator(".monaco-editor")).not.toContainText("stale refresh response");
  // 迟到响应后仍保持 clean，结合 Monaco 正文可证明 savedContent 仍是刚保存的本地版本。
  await expect(page.locator(".ta-workbench-footer-save")).toHaveCount(0);
});

test("overlapping refresh failure preserves the previously loaded cache", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileReadRequests,
    fileContents: { "docs/overlap.md": "stable cached content" },
    fileReadDelays: { "docs/overlap.md": [0, 2_000, 20] },
    fileReadFailureAttempts: { "docs/overlap.md": [3] },
    fileReadResponses: {
      "docs/overlap.md": ["stable cached content", "stale first refresh content"]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const row = page.getByRole("button", { name: "overlap.md", exact: true });
  const overlapReads = () => fileReadRequests.filter((item) => item.path === "docs/overlap.md");
  await row.click();
  await expect(page.locator(".monaco-editor")).toContainText("stable cached content", { timeout: 10_000 });
  await expect.poll(overlapReads).toHaveLength(1);

  await row.click();
  await expect.poll(overlapReads).toHaveLength(2);
  await row.click();
  await expect.poll(overlapReads).toHaveLength(3);
  await expect(page.getByText(/刷新文件失败，已保留上次内容/)).toBeVisible();
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  await expect(page.locator(".monaco-editor")).toContainText("stable cached content");
  await page.waitForTimeout(2_050);
  await expect(page.locator(".monaco-editor")).not.toContainText("stale first refresh content");
  expect(overlapReads()).toHaveLength(3);
});

test("closing a loading file tab discards its late response", async ({ page }) => {
  await mockBackendApi(page, {
    fileContents: { "docs/closing.md": "# must stay closed" },
    fileReadDelays: { "docs/closing.md": [250] },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "closing.md", exact: true }).click();
  const tab = page.getByRole("tab").filter({ hasText: "closing.md" });
  await tab.getByRole("button", { name: "关闭标签" }).click();
  await page.waitForTimeout(300);
  await expect(tab).toHaveCount(0);
});

test("search results and conversation file entries reuse the workspace file loader", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileReadRequests,
    fileContents: {
      "docs/search-entry.md": "# opened from search",
      "docs/conversation-entry.md": "# opened from conversation"
    },
    runEvents: [
      event(1, "diff.proposed", {
        files: [{
          path: "docs/conversation-entry.md",
          patch: "@@ -0,0 +1 @@",
          additions: 1,
          deletions: 0,
          status: "modified"
        }]
      }),
      event(2, "run.succeeded", {})
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("tablist", { name: "工作区面板" }).getByRole("button", { name: "搜索" }).click();
  await page.getByPlaceholder("搜索工作区文件").fill("search-entry");
  await page.getByRole("button", { name: /search-entry.md/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("opened from search", { timeout: 10_000 });

  await page.getByRole("button", { name: "新建对话" }).click();
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("生成文件");
  await page.getByRole("button", { name: "发送" }).click();
  await page.getByRole("button", { name: "文件修改 1 文件总增减行" }).click();
  const conversationFile = page.locator(".oc-diff-file").filter({ hasText: "conversation-entry.md" });
  await expect(conversationFile).toBeVisible();
  await conversationFile.click();
  await expect(page.locator(".monaco-editor")).toContainText("opened from conversation", { timeout: 10_000 });

  expect(fileReadRequests.map((item) => item.path)).toEqual([
    "docs/search-entry.md",
    "docs/conversation-entry.md"
  ]);
});

test("switching workspace discards a loading file response from the previous workspace", async ({ page }) => {
  await mockBackendApi(page, {
    fileContents: { "docs/switching.md": "# stale previous workspace" },
    fileReadDelays: { "docs/switching.md": [250] },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      },
      app_coss: null
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: "switching.md", exact: true }).click();
  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await page.waitForTimeout(300);
  await expect(page.getByRole("tab").filter({ hasText: "switching.md" })).toHaveCount(0);
  await expect(page.getByText("stale previous workspace")).toHaveCount(0);
});

test("an old refresh loop stops before reading the next file in a new workspace", async ({ page }) => {
  test.setTimeout(45_000);
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  const diffFiles = [
    { path: "docs/refresh-a.md", status: "modified", staged: false, patch: "@@ -1 +1 @@", additions: 1, deletions: 1 },
    { path: "docs/shared.md", status: "modified", staged: false, patch: "@@ -1 +1 @@", additions: 1, deletions: 1 }
  ];
  const cossPersonalWorkspace = {
    ...defaultPersonalWorkspace("awv_coss_refresh"),
    appId: "app_coss",
    applicationWorkspaceId: "awp_coss_refresh",
    runtimeWorkspace: {
      ...workspace(),
      workspaceId: "wrk_coss_personal",
      name: "coss-default",
      rootPath: "/Users/huang/workspace/coss-personal",
      appId: "app_coss",
      versionId: "awv_coss_refresh",
      applicationWorkspaceId: "awp_coss_refresh"
    }
  };
  await mockBackendApi(page, {
    fileReadRequests,
    fileContents: {
      "docs/refresh-a.md": "refresh A",
      "docs/shared.md": "shared content"
    },
    fileReadDelays: {
      "docs/refresh-a.md": [0, 800],
      "docs/shared.md": [0, 0]
    },
    historyDiffFiles: diffFiles,
    authRoles: ["SUPER_ADMIN"],
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      },
      app_coss: {
        ...workspace(),
        workspaceId: "wrk_coss_replica",
        appId: "app_coss",
        versionId: "awv_coss_refresh",
        applicationWorkspaceId: "awp_coss_refresh"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")],
      awv_coss_refresh: [cossPersonalWorkspace]
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: /^refresh-a\.md(?:\s|$)/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("refresh A", { timeout: 10_000 });
  await page.getByRole("button", { name: /^shared\.md(?:\s|$)/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("shared content");

  await page.getByRole("button", { name: "变更" }).click();
  diffFiles.length = 0;
  page.once("dialog", (dialog) => dialog.accept());
  await page.getByRole("button", { name: "丢弃全部应用工作空间改动" }).click();
  await expect.poll(() => fileReadRequests.filter((item) => (
    item.workspaceId === "wrk_personal_default" && item.path === "docs/refresh-a.md"
  )).length).toBe(2);

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await page.getByRole("tablist", { name: "工作区面板" }).getByRole("button", { name: "文件树" }).click();
  await page.getByRole("button", { name: "docs", exact: true }).click();
  await page.getByRole("button", { name: /^shared\.md(?:\s|$)/ }).click();
  await expect.poll(() => fileReadRequests.filter((item) => (
    item.workspaceId === "wrk_coss_personal" && item.path === "docs/shared.md"
  )).length).toBeGreaterThanOrEqual(1);

  await page.waitForTimeout(900);
  expect(fileReadRequests.filter((item) => (
    item.workspaceId === "wrk_coss_personal" && item.path === "docs/shared.md"
  ))).toHaveLength(1);
});

test("renaming while the source file is loading reloads the target without stale overwrite", async ({ page }) => {
  const fileReadRequests: Array<{ workspaceId: string; path: string; attempt: number }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileReadRequests,
    fileContents: {
      "docs/race.md": "stable renamed content"
    },
    fileReadDelays: {
      "docs/race.md": [0, 0, 900]
    },
    fileReadNotFoundAttempts: {
      "docs/race.md": [3]
    },
    workspaceMutationDelays: {
      "workspace.rename": 600
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "docs", exact: true }).click();
  const sourceRow = page.getByRole("button", { name: "race.md", exact: true });
  await sourceRow.dblclick();
  await expect.poll(() => fileReadRequests.filter((request) => request.path === "docs/race.md")).toHaveLength(2);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded");
  // 正式重命名入口是右键菜单；双击仅用于制造两次并发读取。
  await sourceRow.click({ button: "right" });
  await page.getByRole("menuitem", { name: "重命名" }).click();
  const renameInput = page.getByRole("textbox", { name: "重命名工作区条目" });
  await expect(renameInput).toBeVisible();
  await renameInput.fill("renamed.md");
  await renameInput.press("Enter");
  await sourceRow.dispatchEvent("click");
  await expect.poll(() => fileReadRequests.filter((request) => request.path === "docs/race.md")).toHaveLength(3);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loading");

  const renamedTab = page.getByRole("tab").filter({ hasText: "renamed.md" });
  await expect(renamedTab).toHaveCount(1);
  await expect(page.getByTestId("file-load-state")).toHaveAttribute("data-state", "loaded", { timeout: 10_000 });
  await expect(page.locator(".monaco-editor")).toContainText("stable renamed content");
  await page.waitForTimeout(950);
  await expect(page.locator(".monaco-editor")).toContainText("stable renamed content");
});

test("application workspace mutation entries follow member and super administrator permissions", async ({ page, context }) => {
  const managedWorkspaceSetup = {
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    },
    workspaceTemplates: {
      app_gcms: [{
        workspaceId: "awp_1",
        workspaceName: permissionFixture.application.appName,
        appId: "app_gcms",
        repositoryId: "repo_1",
        defaultBranch: permissionFixture.application.featureBranch,
        createdAt: "2026-07-15T00:00:00Z",
        updatedAt: "2026-07-15T00:00:00Z"
      }]
    },
    workspaceVersions: {
      "app_gcms:awp_1": [{
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: "20260715",
        branch: permissionFixture.application.featureBranch,
        repoRootPath: "/tmp/test-agent/appworkspace/20260715/repo_1",
        workspaceRootPath: "/tmp/test-agent/appworkspace/20260715/repo_1/F-GCMS/workspace",
        runtimeWorkspace: {
          ...workspace(),
          workspaceId: permissionFixture.application.featureWorkspaceId
        },
        status: "ACTIVE",
        createdAt: "2026-07-15T00:00:00Z",
        updatedAt: "2026-07-15T00:00:00Z"
      }]
    }
  };

  await mockBackendApi(page, { ...managedWorkspaceSetup, authRoles: [...permissionFixture.roles.member] });
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "tests", exact: true }).hover();
  await expect(page.getByRole("button", { name: "新建或上传到此目录" }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "新建或上传应用配置" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "创建应用 worktree" })).toHaveCount(0);

  const superPage = await context.newPage();
  await mockBackendApi(superPage, { ...managedWorkspaceSetup, authRoles: [...permissionFixture.roles.superAdmin] });
  await superPage.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await gotoWorkbench(superPage, { selectConversation: false });
  await openAgentsPanel(superPage);

  await expect(superPage.getByRole("button", { name: "新建或上传公共配置" })).toBeVisible();
  await expect(superPage.getByRole("button", { name: "新建或上传应用配置" })).toBeVisible();
  await expect(superPage.getByRole("button", { name: "创建应用 worktree" })).toHaveCount(0);
  await superPage.close();
});

test("deleting an open workspace file or directory closes every affected tab", async ({ page }) => {
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileContents: {
      "files/delete-me.md": "delete this file",
      "docs/nested.md": "delete this directory"
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "files", exact: true }).click();
  const deleteMeRow = page.getByRole("button", { name: "delete-me.md", exact: true });
  await deleteMeRow.click();
  const deleteMeTab = page.getByRole("tab").filter({ hasText: "delete-me.md" });
  await expect(deleteMeTab).toHaveCount(1);
  await deleteMeRow.hover();
  await page.getByRole("button", { name: "删除 delete-me.md" }).click();
  await page.getByRole("dialog", { name: "删除文件" }).getByRole("button", { name: "确认删除" }).click();
  await expect(deleteMeTab).toHaveCount(0);

  const deletedFileMessage = page.getByRole("alert").filter({ hasText: "文件已删除" });
  await expect(deletedFileMessage).toBeVisible();
  await deletedFileMessage.locator(".el-message__closeBtn").click();
  await expect(deletedFileMessage).toBeHidden();

  const docsRow = page.getByRole("button", { name: "docs", exact: true });
  await docsRow.click();
  await page.getByRole("button", { name: "nested.md", exact: true }).click();
  const nestedTab = page.getByRole("tab").filter({ hasText: "nested.md" });
  await expect(nestedTab).toHaveCount(1);
  await docsRow.hover();
  await page.getByRole("button", { name: "删除 docs" }).click();
  await page.getByRole("dialog", { name: "删除文件夹" }).getByRole("button", { name: "确认删除" }).click();
  await expect(nestedTab).toHaveCount(0);
});

test("workbench home opens the embedded user manual", async ({ page }) => {
  await mockBackendApi(page, {
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });

  await gotoWorkbench(page, { selectConversation: false });

  const manualEntry = page.getByTestId("workbench-home-help");
  await expect(manualEntry).toBeVisible();
  await manualEntry.click();

  await expect(page.getByTestId("help-center-dialog")).toBeVisible();
  await expect(page.getByTestId("help-center-frame")).toHaveAttribute(
    "src",
    /\/help\/guide\/getting-started\.html$/
  );
  const directoryTopic = page.getByRole("button", { name: /开发与测试目录/ });
  await expect(directoryTopic).toBeVisible();
  await directoryTopic.click();
  await expect(page.getByTestId("help-center-frame")).toHaveAttribute(
    "src",
    /\/help\/guide\/directory-mapping\.html$/
  );
  const manualFrame = page.frameLocator('[data-testid="help-center-frame"]');
  await expect(manualFrame.getByRole("button", { name: "测试目录" })).toHaveCount(0);
  await expect(manualFrame.getByText("公共 Git", { exact: true }).first()).toBeVisible();
  await expect(manualFrame.getByText("应用 Git", { exact: true }).first()).toBeVisible();
  const sharedArchive = manualFrame.getByRole("treeitem", { name: /archive\// });
  const localSpec = manualFrame.getByRole("treeitem", { name: /^spec\// });
  const agentsRoot = manualFrame.getByRole("treeitem", { name: /^agents\// });
  const developmentAgent = manualFrame.getByRole("treeitem", { name: /01_需求智能体\// });
  const testingAgent = manualFrame.getByRole("treeitem", { name: /04_测试智能体\// });
  await expect(sharedArchive).toBeVisible();
  await expect(localSpec).toBeVisible();
  await expect(manualFrame.getByRole("treeitem", { name: /2601\// })).toHaveCount(0);
  await expect(sharedArchive.locator(".scope-badge")).toHaveText("开发 + 测试");
  await expect(sharedArchive.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(localSpec.locator(".scope-badge")).toHaveText("个人本地");
  await expect(localSpec.locator(".physical-badge")).toHaveText("应用 Git 个人分支 · 仅本地提交");
  await expect(agentsRoot.locator(".scope-badge")).toHaveText("开发 + 测试");
  await expect(agentsRoot.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await expect(developmentAgent.locator(".scope-badge")).toHaveText("开发");
  await expect(developmentAgent.locator(".role-badge")).toHaveText("Agent");
  await expect(developmentAgent.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(testingAgent.locator(".scope-badge")).toHaveText("测试");
  await expect(testingAgent.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await manualFrame.getByRole("button", { name: "全部展开" }).click();
  const developmentAsset = manualFrame.getByRole("treeitem", { name: "工程概览_A.md" });
  const testingAsset = manualFrame.getByRole("treeitem", { name: "测试概述.md" });
  const testDesignAgent = manualFrame.getByRole("treeitem", { name: /^01_测试设计\// });
  const testAnalysisWorkagent = manualFrame.getByRole("treeitem", { name: /^001 Test Analysis（测试分析）\// });
  const testGenerationWorkagent = manualFrame.getByRole("treeitem", { name: /^002 Test Case Generation（测试案例生成）\// });
  const testReviewWorkagent = manualFrame.getByRole("treeitem", { name: /^003 Test Case Review（测试案例审核）\// });
  const testExecutionAgent = manualFrame.getByRole("treeitem", { name: /^02_测试执行\// });
  const apiExecutionWorkagent = manualFrame.getByRole("treeitem", { name: /^001 API Test Execution（接口测试执行）\// });
  const applicationTestDesignAgent = manualFrame.getByRole("treeitem", { name: "<应用测试设计 Agent>.md" });
  const applicationTestDesignWorkagent = manualFrame.getByRole("treeitem", { name: "<应用测试设计 workagent>.md" });
  const applicationTestExecutionAgent = manualFrame.getByRole("treeitem", { name: "<应用测试执行 Agent>.md" });
  const applicationTestExecutionWorkagent = manualFrame.getByRole("treeitem", { name: "<应用测试执行 workagent>.md" });
  const testingRuleGroups = manualFrame.locator(".tree-row.testing").filter({ hasText: "测试公共规约与应用测试规约" });
  const publicTestRule = manualFrame.getByRole("treeitem", { name: /^测试设计公共规约\// });
  const applicationTestRules = manualFrame.getByRole("treeitem", { name: /^测试设计应用规约\// });
  const developmentSkills = manualFrame.getByRole("treeitem", { name: /^coding\// });
  const testingSkills = manualFrame.getByRole("treeitem", { name: /^test\// });
  const plannedCodeReviewSkill = manualFrame.getByRole("treeitem", { name: /^code-review-skill\// });
  const applicationTestSkills = manualFrame.getByRole("treeitem", { name: /^<应用专属测试 Skill>\// });
  const sharedDocsNodes = ["应用架构/", "功能模块/", "数据架构/"].map((name) =>
    manualFrame.getByRole("treeitem", { name: new RegExp(`^${name}`) })
  );
  const technicalArchitecture = manualFrame.getByRole("treeitem", { name: /^技术架构\// });
  await expect(developmentAsset).toBeVisible();
  await expect(testingAsset).toBeVisible();
  await expect(developmentAsset.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(testingAsset.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(testDesignAgent.locator(".role-badge")).toHaveText("Agent");
  await expect(testDesignAgent.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await expect(testDesignAgent.locator(".implementation-badge")).toHaveText("已实现");
  await expect(testAnalysisWorkagent.locator(".role-badge")).toHaveText("workagent");
  await expect(testAnalysisWorkagent.locator(".physical-badge")).toHaveText("公共 Git");
  for (const implementedAgent of [testAnalysisWorkagent, testGenerationWorkagent, testReviewWorkagent, testExecutionAgent, apiExecutionWorkagent]) {
    await expect(implementedAgent.locator(".implementation-badge")).toHaveText("已实现");
  }
  await expect(testingRuleGroups).toHaveCount(2);
  for (let index = 0; index < 2; index += 1) {
    await expect(testingRuleGroups.nth(index).locator(".scope-badge")).toHaveText("测试");
  }
  for (const [applicationAgent, role] of [
    [applicationTestDesignAgent, "Agent"],
    [applicationTestDesignWorkagent, "workagent"],
    [applicationTestExecutionAgent, "Agent"],
    [applicationTestExecutionWorkagent, "workagent"]
  ] as const) {
    await expect(applicationAgent.locator(".role-badge")).toHaveText(role);
    await expect(applicationAgent.locator(".physical-badge")).toHaveText("应用 Git");
    await expect(applicationAgent.locator(".implementation-badge")).toHaveText("未实现");
    await expect(applicationAgent).toHaveClass(/planned/);
    await expect(applicationAgent).toHaveAttribute("aria-level", "6");
  }
  await expect(manualFrame.getByRole("treeitem", { name: /^<应用专属测试 Agent>\// })).toHaveCount(0);
  await expect(manualFrame.getByRole("treeitem", { name: /^<应用专属测试 workagent>\// })).toHaveCount(0);
  await expect(publicTestRule.locator(".physical-badge")).toHaveText("公共 Git");
  await expect(applicationTestRules.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(applicationTestRules).toHaveAttribute("aria-expanded", "true");
  for (const applicationRule of [
    "接口测试设计应用规约.md",
    "UI测试设计应用规约.md",
    "异步任务测试设计应用规约.md",
    "批量任务测试设计应用规约.md",
    "其他测试设计应用规约.md"
  ]) {
    const row = manualFrame.getByRole("treeitem", { name: applicationRule });
    await expect(row).toBeVisible();
    await expect(row.locator(".physical-badge")).toHaveText("应用 Git");
    await expect(row).toHaveAttribute("aria-level", "8");
  }
  await expect(developmentSkills.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(developmentSkills).toHaveAttribute("aria-level", "4");
  await expect(testingSkills.locator(".physical-badge")).toHaveText("公共 Git + 应用 Git");
  await expect(testingSkills).toHaveAttribute("aria-level", "4");
  await expect(plannedCodeReviewSkill.locator(".implementation-badge")).toHaveText("未实现");
  await expect(plannedCodeReviewSkill).toHaveClass(/planned/);
  await expect(applicationTestSkills.locator(".physical-badge")).toHaveText("应用 Git");
  await expect(applicationTestSkills.locator(".implementation-badge")).toHaveText("未实现");
  await expect(applicationTestSkills).toHaveClass(/planned/);
  const publicRuleGitBadge = manualFrame.getByRole("treeitem", { name: "接口测试设计规约.md" }).locator(".physical-badge");
  await expect.poll(() => publicRuleGitBadge.evaluate((element) => element.getBoundingClientRect().width)).toBeLessThan(340);
  for (const agentFile of [
    "test-design-orchestrator.md",
    "test-design-analysis.md",
    "test-design-generation.md",
    "test-design-review.md",
    "test-execution-agent.md",
    "test-execution-api.md"
  ]) {
    const row = manualFrame.getByRole("treeitem", { name: agentFile });
    await expect(row).toBeVisible();
    await expect(row.locator(".physical-badge")).toHaveText("公共 Git");
  }
  for (const skillDirectory of [
    "test-design/",
    "test-design-api/",
    "test-design-augment/",
    "test-design-direct/",
    "test-design-equivalence/",
    "test-design-orthogonal/",
    "test-design-path/",
    "test-design-scenario/",
    "api-execute-case/",
    "generate-api-automation-markdown/",
    "generate-test-messages/",
    "validate-automation-script-format/"
  ]) {
    const row = manualFrame.getByRole("treeitem", { name: new RegExp(`^${skillDirectory}`) });
    await expect(row).toBeVisible();
    await expect(row.locator(".physical-badge")).toHaveText("公共 Git");
    await expect(row.locator(".implementation-badge")).toHaveText("已实现");
    await expect(row).not.toHaveClass(/planned/);
    await expect(row).toHaveAttribute("aria-level", "5");
  }
  for (const sharedNode of sharedDocsNodes) {
    await expect(sharedNode).toBeVisible();
    await expect(sharedNode.locator(".scope-badge")).toHaveText("开发 + 测试");
    await expect(sharedNode.locator(".physical-badge")).toHaveText("应用 Git");
  }
  await expect(technicalArchitecture.locator(".scope-badge")).toHaveText("开发");
  await expect(technicalArchitecture.locator(".physical-badge")).toHaveText("应用 Git");
  for (const applicationScenario of ["应用场景说明书_XXX.md", "应用场景说明书_YYY.md"]) {
    const row = manualFrame.getByRole("treeitem", { name: applicationScenario });
    await expect(row).toBeVisible();
    await expect(row.locator(".scope-badge")).toHaveText("测试");
    await expect(row.locator(".physical-badge")).toHaveText("应用 Git");
    await expect(row).toHaveAttribute("aria-level", "4");
  }
  await expect(manualFrame.getByRole("treeitem", { name: "测试概述.md" })).toHaveAttribute("aria-level", "4");
  await expect(manualFrame.getByRole("treeitem", { name: /场景测试说明书_/ })).toHaveCount(0);
  await expect(manualFrame.getByRole("treeitem", { name: "流程测试设计.md" })).toBeVisible();
  await expect(manualFrame.getByRole("treeitem", { name: "S000001_测试案例.md" })).toBeVisible();
  await expect(manualFrame.getByText("工作 Agent 统一称为 workagent")).toBeVisible();
  await expect(manualFrame.getByText(/供上层 Agent 编排调用/).first()).toBeVisible();
  await manualFrame.getByRole("button", { name: "内容与责任" }).click();
  await expect(manualFrame.getByRole("cell", { name: "公共能力建设团队" })).toBeVisible();
  await expect(manualFrame.getByRole("cell", { name: "所有角色仅本地提交，禁止发布", exact: true })).toBeVisible();
  await expect(manualFrame.getByRole("cell", { name: "docs/**", exact: true })).toBeVisible();
  await expect(manualFrame.getByRole("cell", { name: "具体研发阶段的个人输入输出产物" })).toBeVisible();
});

test("Markdown Mermaid Flowchart、Sequence 和 State 可视化编辑后复用保存链路", async ({ page }) => {
  test.setTimeout(60_000);
  const fileWriteRequests: Array<{ workspaceId: string; path: string; content: string }> = [];
  await mockBackendApi(page, {
    fileWriteRequests,
    fileContents: {
      "docs/mermaid.md": `# 可视化设计

\`\`\`mermaid
flowchart TD
A[开始] --> B[结束]
classDef important fill:red
\`\`\`

\`\`\`mermaid
sequenceDiagram
actor U as 用户
participant S as 服务
create participant W as 工作器
U->>+W: 请求
alt 成功
  W->>S: 执行
  par 记录
    Note over U,S: 保留说明
  and 通知
    S--)U: 完成
  end
else 失败
  W-->>U: 回退
end
deactivate W
destroy W
W-xU: 中断
\`\`\`

\`\`\`mermaid
stateDiagram-v2
[*] --> Idle
state "空闲" as Idle
Idle: 等待任务
Idle --> Running: 启动
state Running {
  direction LR
  [*] --> Frontend
  Frontend --> [*]
  --
  [*] --> Backend
  Backend --> [*]
}
Running --> [*]
note right of Idle: 可以启动
style Idle fill:#ABC,stroke:#123456,color:#FFF
\`\`\``
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page);
  await openAgentsPanel(page);
  await expect(page.getByText("MIMO测试智能体")).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toBeVisible();
  await page.getByRole("button", { name: /docs/ }).click();
  await page.getByRole("button", { name: /mermaid.md/ }).click();
  await expect(page.locator(".monaco-editor")).toContainText("可视化设计", { timeout: 10_000 });
  await page.getByTestId("footer-markdown-preview").click();

  const visualButtons = page.getByRole("button", { name: "可视化编辑" });
  await expect(visualButtons).toHaveCount(3);
  await visualButtons.nth(0).click();
  const dialog = page.getByRole("dialog", { name: "Mermaid 可视化编辑" });
  await expect(dialog).toBeVisible();
  await dialog.locator(".vue-flow__node").filter({ hasText: "开始" }).dblclick();
  await page.getByLabel("节点文字").fill("准备");
  await page.getByRole("button", { name: "完成" }).click();
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click({ force: true });

  await expect(visualButtons).toHaveCount(3);
  await visualButtons.nth(1).click();
  await dialog.getByLabel("选择消息 请求").click();
  await dialog.getByLabel("消息文本").fill("登录请求");
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click({ force: true });

  await expect(visualButtons).toHaveCount(3);
  await visualButtons.nth(2).click();
  await dialog.getByLabel("状态 Idle").click();
  await dialog.getByLabel("状态名称").fill("就绪");
  await dialog.getByLabel("状态说明").fill("第一行\n第二行");
  await dialog.getByRole("button", { name: "应用到 Markdown" }).click({ force: true });

  await page.locator(".ta-workbench-footer-save").click();
  await expect.poll(() => fileWriteRequests.length).toBe(1);
  expect(fileWriteRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    path: "docs/mermaid.md"
  });
  expect(fileWriteRequests[0]?.content).toContain('A@{ shape: rect, label: "准备" }');
  expect(fileWriteRequests[0]?.content).toContain("U->>+W: 登录请求");
  expect(fileWriteRequests[0]?.content).toContain("classDef important fill:red");
  expect(fileWriteRequests[0]?.content).toContain("alt 成功");
  expect(fileWriteRequests[0]?.content).toContain("par 记录");
  expect(fileWriteRequests[0]?.content).toContain("Note over U,S: 保留说明");
  expect(fileWriteRequests[0]?.content).toContain("destroy W");
  expect(fileWriteRequests[0]?.content).toContain('state "就绪" as Idle');
  expect(fileWriteRequests[0]?.content).toContain("Idle: 第一行");
  expect(fileWriteRequests[0]?.content).toContain("state Running {");
  expect(fileWriteRequests[0]?.content).toContain("note right of Idle: 可以启动");
  expect(fileWriteRequests[0]?.content).toContain("style Idle fill:#AABBCC,stroke:#123456,color:#FFFFFF");
});

test("switching to an application without recent workspace clears the previous file tree", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      },
      app_coss: null
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toBeVisible();

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();

  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toHaveCount(0);
  expect(fileRequests).toContainEqual({ workspaceId: "wrk_personal_default", path: "" });
  expect(personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  const switcher = page.locator(".ta-workbench-footer-branch");
  await expect(switcher).toBeVisible();
  await switcher.click();
  await expect(page.locator(".ta-workbench-cascade-panel")).toContainText("应用：F-COSS");
});

test("application source snapshot opens a logical workspace and enforces source capabilities", async ({ page }) => {
  const appSourceRequests: string[] = [];
  const clearedRecentAppSource: string[] = [];
  const appSourceMaterializationRequests: Array<{ key: string; payload: Record<string, unknown> }> = [];
  const gitDiffRequests: string[] = [];
  const fileWriteRequests: Array<{ workspaceId: string; path: string; content: string }> = [];
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_generation_9",
    name: "F-GCMS 源码快照",
    rootPath: "/srv/test-agent/app-source/repo-code/generation-9",
    appId: "app_gcms"
  };
  const updatedSourceWorkspace = {
    ...sourceWorkspace,
    workspaceId: "wrk_source_generation_10",
    name: "F-GCMS 源码快照 generation 10",
    rootPath: "/srv/test-agent/app-source/repo-code/generation-10"
  };
  await mockBackendApi(page, {
    appSourceRequests,
    clearedRecentAppSource,
    gitDiffRequests,
    appSourceMaterializationRequests,
    fileWriteRequests,
    fileContents: { "tests/checkout.spec.ts": "export const sourceGeneration = 9;" },
    workspaces: [workspace(), sourceWorkspace, updatedSourceWorkspace],
    appSourceRepositories: {
      app_gcms: [
        {
          repositoryId: "repo-code",
          name: "应用代码库",
          englishName: "application-code",
          downloadState: "DOWNLOADED_ACTIVE",
          generation: 9,
          purpose: "TEAM",
          branch: "main",
          targetCommit: "commit-9",
          selectedPaths: [{ path: "src", type: "DIRECTORY" }],
          expiresAt: "2026-07-30T00:00:00Z",
          occupied: false,
          openable: true,
          manageable: true,
          latestOperation: null,
          serverSummaries: []
        },
        {
          repositoryId: "repo-personal",
          name: "个人占用库",
          englishName: "personal-repository",
          downloadState: "PERSONAL_OCCUPIED",
          generation: 3,
          purpose: "PERSONAL",
          ownerName: "李四",
          ownerUnifiedAuthId: "UCID-1002",
          selectedPaths: [],
          occupied: true,
          openable: false,
          manageable: false,
          unavailableReason: "当前副本由其他成员占用",
          latestOperation: null,
          serverSummaries: []
        },
        {
          repositoryId: "repo-new",
          name: "尚未下载库",
          englishName: "not-downloaded-repository",
          downloadState: "NOT_DOWNLOADED",
          generation: null,
          purpose: null,
          selectedPaths: [],
          occupied: false,
          openable: false,
          manageable: true,
          latestOperation: null,
          serverSummaries: []
        }
      ]
    },
    appSourceOpenResults: {
      "app_gcms:repo-code:9": {
        appId: "app_gcms",
        repositoryId: "repo-code",
        generation: 9,
        purpose: "TEAM",
        workspaceId: "wrk_source_generation_9",
        linuxServerId: "10.8.0.12",
        expiresAt: "2026-07-30T00:00:00Z"
      },
      "app_gcms:repo-code:10": {
        appId: "app_gcms",
        repositoryId: "repo-code",
        generation: 10,
        purpose: "TEAM",
        workspaceId: "wrk_source_generation_10",
        linuxServerId: "10.8.0.12",
        expiresAt: "2026-07-31T00:00:00Z"
      }
    },
    appSourceBranches: { "app_gcms:repo-code": ["main", "release"] },
    appSourceTreeSnapshots: {
      "app_gcms:repo-code:main:.": {
        targetCommit: "commit-10",
        nodes: [{ name: "src", path: "src", type: "directory", children: [] }]
      }
    },
    appSourceMaterializationResults: {
      "app_gcms:repo-code": {
        operationId: "aso_materialize_10",
        appId: "app_gcms",
        repositoryId: "repo-code",
        sourceGeneration: 9,
        targetGeneration: 10,
        operationType: "UPDATE",
        status: "SUCCEEDED",
        purpose: "TEAM",
        branch: "main",
        targetCommit: "commit-10",
        selectedPaths: [{ path: "src", type: "DIRECTORY" }],
        expiresAt: "2026-07-31T00:00:00Z",
        traceId: "trace-materialize-10",
        acceptedAt: "2026-07-28T00:00:00Z",
        completedAt: "2026-07-28T00:01:00Z",
        globalSteps: [],
        serverSummaries: []
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  const fileExplorer = page.locator(".figma-file-explorer");
  await openAppSourceFromWorkspaceSwitch(page);
  const picker = page.getByRole("dialog", { name: "应用源码" });
  await expect(picker).toContainText("团队可用");
  await expect(picker).toContainText("李四 · UCID-1002");
  await expect(picker).not.toContainText("尚未下载库");
  await page.getByRole("button", { name: "打开应用代码库源码" }).click();

  await expect(fileExplorer.getByText("源码快照", { exact: true })).toBeVisible();
  await expect(fileExplorer.getByText("无 Git", { exact: true })).toBeVisible();
  await expect(fileExplorer.getByRole("button", { name: "变更" })).toHaveCount(0);
  await expect(fileExplorer.getByText("Agents", { exact: true })).toHaveCount(0);
  const sourceWorkspaceSwitch = fileExplorer.getByRole("button", { name: "切换应用代码库或测试工作空间" });
  await expect(sourceWorkspaceSwitch).toBeVisible();
  await sourceWorkspaceSwitch.click();
  await expect(page.getByRole("menu").locator(".ta-workbench-cascade-source-title").getByText("应用代码库", { exact: true }))
    .toBeVisible();
  await expect(page.getByRole("menu").getByRole("button", { name: "打开应用代码库源码" })).toBeVisible();
  await expect(page.getByRole("menu").getByRole("button", { name: "测试工作空间", exact: true })).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByTestId("robot-visibility-toggle").click();
  await expect(page.getByTestId("figma-robot")).toBeVisible();
  await page.getByTestId("figma-robot").click();
  await expect(page.getByRole("button", { name: "重载应用个人配置" })).toHaveCount(0);
  await page.getByRole("button", { name: "关闭宠物旁路问答" }).click();
  expect(appSourceRequests).toContain("open:app_gcms:repo-code:9");
  const sourceGitDiffCount = gitDiffRequests.length;
  await page.waitForTimeout(100);
  expect(gitDiffRequests).toHaveLength(sourceGitDiffCount);

  await fileExplorer.getByRole("button", { name: "tests", exact: true }).click();
  await fileExplorer.getByRole("button", { name: "checkout.spec.ts", exact: true }).click();
  await expect(page.locator(".monaco-editor")).toContainText("sourceGeneration", { timeout: 10_000 });
  await page.locator(".monaco-editor .view-line").first().click();
  await page.keyboard.press("ControlOrMeta+A");
  await page.keyboard.type("export const sourceGeneration = 9; // edited");
  await page.locator(".ta-workbench-footer-save").click();
  await fileExplorer.getByRole("button", { name: "新建或上传到工作区根目录" }).click();
  const createDialog = page.getByRole("dialog", { name: "新建或上传文件" });
  await createDialog.getByLabel("文件名").fill("source-note.md");
  await createDialog.getByRole("button", { name: "创建", exact: true }).click();
  await expect.poll(() => fileWriteRequests).toEqual(expect.arrayContaining([
    expect.objectContaining({
      workspaceId: "wrk_source_generation_9",
      path: "tests/checkout.spec.ts",
      content: expect.stringContaining("// edited")
    }),
    {
      workspaceId: "wrk_source_generation_9",
      path: "source-note.md",
      content: ""
    }
  ]));

  await fileExplorer.locator(".app-source-mode-banner").getByRole("button", { name: "返回应用工作区" }).click();
  await expect(fileExplorer.getByRole("button", { name: "变更" })).toBeVisible();
  await expect(fileExplorer.getByText("源码快照", { exact: true })).toHaveCount(0);
  expect(clearedRecentAppSource).toEqual(["DELETE"]);

  await openAppSourceManagementForRepository(page, "尚未下载库");
  const dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await expect(dialog).toContainText("尚未下载库");
  await expect(dialog).toContainText("当前配置 · 尚未下载库");
  await expect(dialog).toContainText("李四 · UCID-1002");
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await dialog.getByRole("button", { name: "下一步：选择分支与目录" }).click();
  await expect(dialog.locator(".app-source-branch-select")).toContainText("main");
  await expect(dialog.getByLabel("选择路径 src")).toBeChecked();
  await dialog.getByRole("button", { name: "下一步：用途与保留时间" }).click();
  await expect(dialog.getByLabel("个人源码")).toBeDisabled();
  await expect(dialog.getByLabel("保留小时数")).toHaveValue("48");
  await dialog.getByLabel("确认覆盖当前源码").check();
  await dialog.getByRole("button", { name: "提交源码物化" }).click();

  await expect(fileExplorer.getByText("源码快照", { exact: true })).toBeVisible();
  await expect.poll(() => appSourceRequests).toContain("open:app_gcms:repo-code:10");
  expect(appSourceMaterializationRequests).toHaveLength(1);
  expect(appSourceMaterializationRequests[0]).toMatchObject({
    key: "app_gcms:repo-code",
    payload: {
      expectedGeneration: 9,
      expectedTreeCommit: "commit-10",
      selectedPaths: [{ path: "src", type: "DIRECTORY" }],
      confirmReplace: true
    }
  });
});

test("a drifting lazy child invalidates every concurrent child until a real root snapshot reloads", async ({ page }) => {
  let releaseDriftingChild!: () => void;
  let releaseOldSibling!: () => void;
  const driftingChildGate = new Promise<void>((resolve) => { releaseDriftingChild = resolve; });
  const oldSiblingGate = new Promise<void>((resolve) => { releaseOldSibling = resolve; });
  const appSourceRequests: string[] = [];
  await mockBackendApi(page, {
    appSourceRequests,
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ generation: 9, branch: "main", selectedPaths: [{ path: "src", type: "DIRECTORY" }] })]
    },
    appSourceBranches: { "app_gcms:repo-code": ["main"] },
    appSourceTreeGates: {
      "app_gcms:repo-code:main:src": driftingChildGate,
      "app_gcms:repo-code:main:docs": oldSiblingGate
    },
    appSourceTreeSnapshots: {
      "app_gcms:repo-code:main:.": {
        targetCommit: "commit-root",
        nodes: [
          { name: "src", path: "src", type: "directory", children: [] },
          { name: "docs", path: "docs", type: "directory", children: [] }
        ]
      },
      "app_gcms:repo-code:main:src": {
        targetCommit: "commit-drift",
        nodes: [{ name: "late.ts", path: "src/late.ts", type: "file", children: [] }]
      },
      // 旧 sibling 故意携带已选中的 src；错误实现会在 snapshot 被清空后把它安装成 root 并重新启用下一步。
      "app_gcms:repo-code:main:docs": {
        targetCommit: "commit-root",
        nodes: [{ name: "src", path: "src", type: "directory", children: [] }]
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "下载版本库" }).click();
  const dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await dialog.getByRole("button", { name: "下一步：选择分支与目录" }).click();
  await expect(dialog.getByText("固定提交：commit-root")).toBeVisible();

  const srcDirectory = dialog.getByRole("button", { name: "展开路径 src" });
  const docsDirectory = dialog.getByRole("button", { name: "展开路径 docs" });
  await expect(srcDirectory).toBeVisible();
  await expect(docsDirectory).toBeVisible();
  // 在同一个浏览器 task 内发出两次展开，避免首次 loading 重绘禁用同级按钮。
  await page.evaluate(() => {
    const src = document.querySelector('button[aria-label="展开路径 src"]') as HTMLButtonElement | null;
    const docs = document.querySelector('button[aria-label="展开路径 docs"]') as HTMLButtonElement | null;
    if (!src || !docs) throw new Error("源码目录展开按钮未挂载");
    src.click();
    docs.click();
  });
  await expect.poll(() => appSourceRequests).toEqual(expect.arrayContaining([
    "tree:app_gcms:repo-code:main:src",
    "tree:app_gcms:repo-code:main:docs"
  ]));
  releaseDriftingChild();
  await expect(dialog.getByText("固定提交已变化，请重新加载当前分支目录树")).toBeVisible();
  releaseOldSibling();

  await expect(dialog.getByRole("button", { name: "下一步：用途与保留时间" })).toBeDisabled();
  await expect(dialog.getByText("固定提交：commit-root")).toHaveCount(0);
  await dialog.getByRole("button", { name: "上一步" }).click();
  await dialog.getByRole("button", { name: "下一步：选择分支与目录" }).click();
  await expect(dialog.getByText("固定提交：commit-root")).toBeVisible();
  await expect(dialog.getByRole("button", { name: "下一步：用途与保留时间" })).toBeEnabled();
});

test("source progress reconnects with fresh snapshots and tickets without cancelling the task", async ({ page }) => {
  const appSourceRequests: string[] = [];
  const appSourceTicketRequests: string[] = [];
  const appSourceRetryRequests: Array<{ key: string; payload: Record<string, unknown> }> = [];
  const running = appSourceOperation("aso_running", "RUNNING", {
    sourceGeneration: 9,
    targetGeneration: 10
  });
  const firstFrame = appSourceOperation("aso_running", "RUNNING", {
    sourceGeneration: 9,
    targetGeneration: 10,
    globalSteps: [{
      stepCode: "RESOLVE_COMMIT",
      sequence: 1,
      status: "SUCCEEDED",
      safeSummary: "已锁定固定提交",
      updatedAt: "2026-07-28T10:00:01Z"
    }]
  });
  const reconnectedStep = appSourceOperation("aso_running", "RUNNING", {
    sourceGeneration: 9,
    targetGeneration: 10,
    serverSummaries: [{
      linuxServerId: "10.8.0.12",
      replicaStatus: "RUNNING",
      attemptCount: 1,
      steps: [{
        stepCode: "CHECKOUT",
        sequence: 1,
        status: "RUNNING",
        safeSummary: "重连后继续同步",
        updatedAt: "2026-07-28T10:00:02Z"
      }]
    }]
  });
  const partial = appSourceOperation("aso_running", "PARTIAL_FAILED", {
    sourceGeneration: 9,
    targetGeneration: 10,
    completedAt: "2026-07-28T10:01:00Z",
    serverSummaries: [{
      linuxServerId: "10.8.0.12",
      replicaStatus: "FAILED",
      attemptCount: 2,
      safeErrorCode: "REPLICA_FAILED",
      safeErrorMessage: "一个副本同步失败",
      steps: []
    }]
  });
  const retry = appSourceOperation("aso_retry", "RUNNING", {
    sourceGeneration: 10,
    targetGeneration: 10,
    operationType: "RETRY_REPLICAS"
  });
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_progress_10",
    name: "源码进度 generation 10",
    rootPath: "/srv/test-agent/app-source/progress-10",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    appSourceRequests,
    appSourceTicketRequests,
    appSourceRetryRequests,
    workspaces: [workspace(), sourceWorkspace],
    appSourceRepositories: {
      app_gcms: [appSourceRepository({
        generation: 9,
        branch: "main",
        latestOperation: running
      })]
    },
    appSourceOperationSnapshots: {
      aso_running: [running, running, running],
      aso_retry: retry
    },
    appSourceOpenResults: {
      "app_gcms:repo-code:10": appSourceOpenResult("wrk_source_progress_10", 10)
    },
    appSourceRetryResults: { "app_gcms:repo-code": retry },
    appSourceProgressSocketPlans: [
      {
        frames: [{ afterMs: 5, event: appSourceProgressEvent("snapshot", firstFrame) }],
        disconnectAfterMs: 30
      },
      {
        frames: [
          { afterMs: 5, event: appSourceProgressEvent("step", reconnectedStep) },
          // 关闭弹窗后仍投递旧连接终态，当前 authority 必须忽略。
          { afterMs: 350, event: appSourceProgressEvent("completed", partial) }
        ]
      },
      {
        frames: [{ afterMs: 20, event: appSourceProgressEvent("completed", partial) }]
      },
      { frames: [] }
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  const fileExplorer = page.locator(".figma-file-explorer");
  await openAppSourceFromWorkspaceSwitch(page);
  await expect(page.getByRole("dialog", { name: "应用源码" })).toContainText("应用代码库");
  await page.getByRole("button", { name: "下载版本库" }).click();
  let dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await expect(dialog).toContainText("已锁定固定提交");
  await expect(dialog).toContainText("重连后继续同步", { timeout: 10_000 });
  await expect.poll(() => appSourceTicketRequests).toEqual(["aso_running", "aso_running"]);

  await dialog.getByRole("button", { name: "关闭源码弹窗" }).click();
  expect(appSourceRequests.some((request) => request.includes("cancel"))).toBe(false);
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "下载版本库" }).click();
  dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await expect(dialog).toContainText("PARTIAL_FAILED", { timeout: 10_000 });
  await expect.poll(() => appSourceTicketRequests).toEqual(["aso_running", "aso_running", "aso_running"]);
  await expect.poll(() => appSourceRequests.filter((request) => request === "open:app_gcms:repo-code:10")).toHaveLength(1);

  await dialog.getByRole("button", { name: "重试失败或缺失副本" }).click();
  await expect.poll(() => appSourceRetryRequests).toHaveLength(1);
  expect(appSourceRetryRequests[0]).toMatchObject({
    key: "app_gcms:repo-code",
    payload: { expectedGeneration: 10 }
  });
  await expect.poll(() => appSourceTicketRequests).toEqual([
    "aso_running",
    "aso_running",
    "aso_running",
    "aso_retry"
  ]);
  await dialog.getByRole("button", { name: "关闭源码弹窗" }).click();
  expect(appSourceRequests.some((request) => request.includes("cancel"))).toBe(false);
});

test("a stale socket epoch cannot fail or complete the replacement progress connection", async ({ page }) => {
  const appSourceRequests: string[] = [];
  const appSourceTicketRequests: string[] = [];
  const running = appSourceOperation("aso_epoch", "RUNNING", { targetGeneration: 2 });
  const socket2Running = appSourceOperation("aso_epoch", "RUNNING", {
    targetGeneration: 2,
    globalSteps: [{
      stepCode: "SOCKET_2",
      sequence: 1,
      status: "RUNNING",
      safeSummary: "新连接仍在工作",
      updatedAt: "2026-07-28T10:00:02Z"
    }]
  });
  const completed = appSourceOperation("aso_epoch", "SUCCEEDED", {
    targetGeneration: 2,
    completedAt: "2026-07-28T10:01:00Z"
  });
  const sourceWorkspace = { ...workspace(), workspaceId: "wrk_source_epoch", name: "epoch 源码", appId: "app_gcms" };
  await mockBackendApi(page, {
    recentWorkspaces: { app_gcms: null },
    appSourceRequests,
    appSourceTicketRequests,
    workspaces: [sourceWorkspace],
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ generation: 1, latestOperation: running })]
    },
    appSourceOperationSnapshots: { aso_epoch: [running, running] },
    appSourceOpenResults: {
      "app_gcms:repo-code:2": appSourceOpenResult("wrk_source_epoch", 2)
    },
    appSourceProgressSocketPlans: [
      {
        disconnectAfterMs: 20,
        frames: [
          {
            afterMs: 380,
            event: {
              type: "failed",
              operationId: "aso_epoch",
              status: "FAILED",
              errorCode: "OLD_SOCKET_FAILED",
              errorMessage: "旧连接迟到失败"
            }
          },
          { afterMs: 420, event: appSourceProgressEvent("completed", completed) }
        ]
      },
      {
        frames: [
          { afterMs: 5, event: appSourceProgressEvent("step", socket2Running) },
          { afterMs: 520, event: appSourceProgressEvent("completed", completed) }
        ]
      }
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "下载版本库" }).click();
  const dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await expect.poll(() => appSourceTicketRequests).toEqual(["aso_epoch", "aso_epoch"]);
  await expect(dialog.getByText("新连接仍在工作")).toBeVisible();
  // 当前进度弹窗用安全摘要作为稳定可见状态，状态标签可能在慢机器上已进入下一帧，不再依赖固定 220ms 的 RUNNING 文案窗口。
  await expect(dialog).not.toContainText("旧连接迟到失败");
  expect(appSourceRequests.filter((request) => request === "open:app_gcms:repo-code:2")).toHaveLength(0);
  await expect.poll(() => appSourceRequests.filter((request) => request === "open:app_gcms:repo-code:2"), {
    timeout: 2_000
  }).toHaveLength(1);
});

test("a failed socket epoch is invalid throughout reconnect backoff and replacement snapshot loading", async ({ page }) => {
  let releaseReplacementSnapshot!: () => void;
  const replacementSnapshotGate = new Promise<void>((resolve) => {
    releaseReplacementSnapshot = resolve;
  });
  const appSourceRequests: string[] = [];
  const appSourceTicketRequests: string[] = [];
  const running = appSourceOperation("aso_epoch_gap", "RUNNING", { targetGeneration: 2 });
  const staleBackoffStep = appSourceOperation("aso_epoch_gap", "RUNNING", {
    targetGeneration: 2,
    globalSteps: [{
      stepCode: "STALE_BACKOFF",
      sequence: 1,
      status: "RUNNING",
      safeSummary: "旧连接在退避期迟到",
      updatedAt: "2026-07-28T10:00:01Z"
    }]
  });
  const replacementStep = appSourceOperation("aso_epoch_gap", "RUNNING", {
    targetGeneration: 2,
    globalSteps: [{
      stepCode: "REPLACEMENT",
      sequence: 1,
      status: "RUNNING",
      safeSummary: "新连接已恢复",
      updatedAt: "2026-07-28T10:00:02Z"
    }]
  });
  const staleCompleted = appSourceOperation("aso_epoch_gap", "SUCCEEDED", {
    targetGeneration: 2,
    completedAt: "2026-07-28T10:01:00Z"
  });
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_epoch_gap",
    name: "epoch gap 源码",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    recentWorkspaces: { app_gcms: null },
    appSourceRequests,
    appSourceTicketRequests,
    workspaces: [sourceWorkspace],
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ generation: 1, latestOperation: running })]
    },
    appSourceOperationSnapshots: { aso_epoch_gap: [running, running] },
    appSourceOperationRequestGates: {
      aso_epoch_gap: [Promise.resolve(), replacementSnapshotGate]
    },
    appSourceOpenResults: {
      "app_gcms:repo-code:2": appSourceOpenResult("wrk_source_epoch_gap", 2)
    },
    appSourceProgressSocketPlans: [
      {
        disconnectAfterMs: 20,
        frames: [
          { afterMs: 100, event: appSourceProgressEvent("step", staleBackoffStep) },
          {
            afterMs: 330,
            event: {
              type: "failed",
              operationId: "aso_epoch_gap",
              status: "FAILED",
              errorCode: "OLD_SOCKET_FAILED",
              errorMessage: "旧连接在 snapshot 等待期迟到失败"
            }
          },
          { afterMs: 360, event: appSourceProgressEvent("completed", staleCompleted) }
        ]
      },
      {
        frames: [{ afterMs: 5, event: appSourceProgressEvent("step", replacementStep) }]
      }
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "下载版本库" }).click();
  const dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await expect.poll(() => appSourceTicketRequests).toEqual(["aso_epoch_gap"]);

  // 当前 socket 失败后必须立刻作废 epoch，退避期的旧 step 不能再投影到弹窗。
  await page.waitForTimeout(150);
  await expect(dialog.getByText("旧连接在退避期迟到")).toHaveCount(0);
  await expect.poll(() => appSourceRequests.filter((request) => request === "operation:aso_epoch_gap"), {
    timeout: 2_000
  }).toHaveLength(2);

  // 第二次 snapshot 尚未返回时仍没有 replacement epoch，旧 socket 的失败和终态也必须被拒绝。
  await page.waitForTimeout(180);
  expect(appSourceTicketRequests).toEqual(["aso_epoch_gap"]);
  expect(appSourceRequests.filter((request) => request === "open:app_gcms:repo-code:2")).toHaveLength(0);
  await expect(dialog.getByText("RUNNING", { exact: true })).toBeVisible();

  releaseReplacementSnapshot();
  await expect.poll(() => appSourceTicketRequests).toEqual(["aso_epoch_gap", "aso_epoch_gap"]);
  await expect(dialog.getByText("新连接已恢复")).toBeVisible();
  expect(appSourceRequests.filter((request) => request === "open:app_gcms:repo-code:2")).toHaveLength(0);
});

test("progress reconnect keeps exponential backoff across sockets that open without a valid frame", async ({ page }) => {
  const ticketRequests: string[] = [];
  const ticketTimes: number[] = [];
  const running = appSourceOperation("aso_backoff", "RUNNING");
  await mockBackendApi(page, {
    recentWorkspaces: { app_gcms: null },
    appSourceTicketRequests: ticketRequests,
    appSourceTicketRequestTimes: ticketTimes,
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ latestOperation: running })]
    },
    appSourceOperationSnapshots: { aso_backoff: running },
    appSourceProgressSocketPlans: [
      { disconnectAfterMs: 5 },
      { disconnectAfterMs: 5 },
      { disconnectAfterMs: 5 },
      { frames: [{ afterMs: 5, event: appSourceProgressEvent("snapshot", running) }] }
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "下载版本库" }).click();
  const dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await expect.poll(() => ticketRequests, { timeout: 5_000 }).toHaveLength(4);

  const delays = ticketTimes.slice(1).map((time, index) => time - (ticketTimes[index] ?? time));
  expect(delays[0]).toBeGreaterThanOrEqual(200);
  expect(delays[1]).toBeGreaterThanOrEqual(430);
  expect(delays[2]).toBeGreaterThanOrEqual(850);
  await dialog.getByRole("button", { name: "关闭源码弹窗" }).click();
});

test("closing the source dialog releases a connecting progress socket without retrying", async ({ page }) => {
  const appSourceRequests: string[] = [];
  const ticketRequests: string[] = [];
  const running = appSourceOperation("aso_connecting", "RUNNING");
  await mockBackendApi(page, {
    recentWorkspaces: { app_gcms: null },
    appSourceRequests,
    appSourceTicketRequests: ticketRequests,
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ latestOperation: running })]
    },
    appSourceOperationSnapshots: { aso_connecting: running },
    appSourceProgressSocketPlans: [{ openAfterMs: 1_500 }]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "下载版本库" }).click();
  const dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择应用代码库版本库" }).click();
  await expect.poll(() => ticketRequests).toEqual(["aso_connecting"]);
  await dialog.getByRole("button", { name: "关闭源码弹窗" }).click();

  await expect.poll(() => page.evaluate(() => (
    window as Window & { __taClosedAppSourceTickets?: string[] }
  ).__taClosedAppSourceTickets ?? [])).toContain("ast_1");
  await page.waitForTimeout(350);
  expect(ticketRequests).toEqual(["aso_connecting"]);
  expect(appSourceRequests.some((request) => request.includes("cancel"))).toBe(false);
});

test("a stale terminal apply and its repository refresh cannot invalidate a newer source open", async ({ page }) => {
  let releaseTerminalOpenA!: () => void;
  let releaseWorkspaceB!: () => void;
  const terminalOpenAGate = new Promise<void>((resolve) => { releaseTerminalOpenA = resolve; });
  const workspaceBGate = new Promise<void>((resolve) => { releaseWorkspaceB = resolve; });
  const appSourceRequests: string[] = [];
  const workspaceRequests: string[] = [];
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const runningA = appSourceOperation("aso_terminal_a", "RUNNING", {
    repositoryId: "repo-a",
    sourceGeneration: 1,
    targetGeneration: 2
  });
  const completedA = appSourceOperation("aso_terminal_a", "SUCCEEDED", {
    repositoryId: "repo-a",
    sourceGeneration: 1,
    targetGeneration: 2,
    completedAt: "2026-07-28T10:01:00Z"
  });
  const workspaceA = { ...workspace(), workspaceId: "wrk_source_a2", name: "终态源码 A", appId: "app_gcms" };
  const workspaceB = { ...workspace(), workspaceId: "wrk_source_b5", name: "当前源码 B", appId: "app_gcms" };
  await mockBackendApi(page, {
    recentWorkspaces: { app_gcms: null },
    appSourceRequests,
    workspaceRequests,
    fileRequests,
    workspaceRequestGates: { wrk_source_b5: workspaceBGate },
    workspaces: [workspaceA, workspaceB],
    appSourceRepositories: {
      app_gcms: [
        appSourceRepository({ repositoryId: "repo-a", name: "源码 A", generation: 1, latestOperation: runningA }),
        appSourceRepository({ repositoryId: "repo-b", name: "源码 B", generation: 5, latestOperation: null })
      ]
    },
    appSourceOperationSnapshots: { aso_terminal_a: runningA },
    appSourceOpenGates: { "app_gcms:repo-a:2": terminalOpenAGate },
    appSourceOpenResults: {
      "app_gcms:repo-a:2": { ...appSourceOpenResult("wrk_source_a2", 2), repositoryId: "repo-a" },
      "app_gcms:repo-b:5": { ...appSourceOpenResult("wrk_source_b5", 5), repositoryId: "repo-b" }
    },
    appSourceProgressSocketPlans: [{
      frames: [{ afterMs: 10, event: appSourceProgressEvent("completed", completedA) }]
    }]
  });

  await gotoWorkbench(page, { selectConversation: false });
  const fileExplorer = page.locator(".figma-file-explorer");
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "下载版本库" }).click();
  let dialog = page.getByRole("dialog", { name: "下载应用源码" });
  await dialog.getByRole("button", { name: "选择源码 A版本库" }).click();
  await expect.poll(() => appSourceRequests).toContain("open:app_gcms:repo-a:2");
  await dialog.getByRole("button", { name: "关闭源码弹窗" }).click();

  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "打开源码 B源码" }).click();
  await expect.poll(() => workspaceRequests).toContain("wrk_source_b5");
  releaseTerminalOpenA();
  await page.waitForTimeout(50);
  releaseWorkspaceB();

  await expect(fileExplorer.getByText("源码快照", { exact: true })).toBeVisible();
  await expect.poll(() => fileRequests.filter((request) => request.path === "").at(-1)?.workspaceId)
    .toBe("wrk_source_b5");
  expect(appSourceRequests).toContain("list:app_gcms");
});

test("invalid recent application source falls back to the managed application workspace", async ({ page }) => {
  const appSourceRequests: string[] = [];
  const clearedRecentAppSource: string[] = [];
  await mockBackendApi(page, {
    appSourceRequests,
    clearedRecentAppSource,
    recentAppSource: {
      appId: "app_gcms",
      repositoryId: "repo-expired",
      generation: 4,
      purpose: "TEAM",
      workspaceId: "wrk_expired_source",
      linuxServerId: "10.8.0.12",
      expiresAt: "2026-07-27T00:00:00Z"
    },
    appSourceOpenResults: {}
  });

  await gotoWorkbench(page, { selectConversation: false });

  await expect(page.locator(".figma-file-explorer").getByRole("button", { name: "变更" })).toBeVisible();
  await expect(page.locator(".figma-file-explorer").getByText("源码快照", { exact: true })).toHaveCount(0);
  expect(appSourceRequests).toContain("recent:get");
  expect(appSourceRequests).toContain("open:app_gcms:repo-expired:4");
  expect(clearedRecentAppSource).toEqual(["DELETE"]);
});

test("a transient recent source validation failure keeps the current source workspace and recent intent", async ({ page }) => {
  const clearedRecentAppSource: string[] = [];
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_transient",
    name: "暂时校验源码",
    rootPath: "/srv/test-agent/app-source/transient",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    clearedRecentAppSource,
    appSourceRecentGetResponses: [
      { value: null },
      { failure: { status: 503, code: "INTERNAL", message: "源码校验服务暂时不可用" } }
    ],
    workspaces: [workspace(), sourceWorkspace],
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ generation: 6, branch: "main" })]
    },
    appSourceOpenResults: {
      "app_gcms:repo-code:6": appSourceOpenResult("wrk_source_transient", 6)
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  const fileExplorer = page.locator(".figma-file-explorer");
  await openAppSourceRepositoryFromWorkspaceSwitch(page, "应用代码库");
  await expect(fileExplorer.getByText("源码快照", { exact: true })).toBeVisible();

  await page.evaluate(() => window.dispatchEvent(new Event("focus")));

  await expect(fileExplorer.getByText("源码快照", { exact: true })).toBeVisible();
  await expect(page.getByText("源码工作区暂时无法校验")).toBeVisible();
  expect(clearedRecentAppSource).toEqual([]);
});

test("a null recent source on focus invalidates the current source capability and falls back", async ({ page }) => {
  const clearedRecentAppSource: string[] = [];
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_missing_recent",
    name: "失效源码",
    rootPath: "/srv/test-agent/app-source/missing-recent",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    clearedRecentAppSource,
    appSourceRecentGetResponses: [{ value: null }, { value: null }],
    workspaces: [workspace(), sourceWorkspace],
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ generation: 7, branch: "main" })]
    },
    appSourceOpenResults: {
      "app_gcms:repo-code:7": appSourceOpenResult("wrk_source_missing_recent", 7)
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  const fileExplorer = page.locator(".figma-file-explorer");
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "打开应用代码库源码" }).click();
  await expect(fileExplorer.getByText("源码快照", { exact: true })).toBeVisible();

  await page.evaluate(() => window.dispatchEvent(new Event("focus")));

  await expect(fileExplorer.getByText("源码快照", { exact: true })).toHaveCount(0);
  await expect(fileExplorer.getByRole("button", { name: "变更" })).toBeVisible();
  expect(clearedRecentAppSource).toEqual(["DELETE"]);
});

test("late source repository and workspace responses cannot overwrite a newer application selection", async ({ page }) => {
  let releaseListA!: () => void;
  const listAGate = new Promise<void>((resolve) => { releaseListA = resolve; });
  let releaseSourceWorkspace!: () => void;
  const sourceWorkspaceGate = new Promise<void>((resolve) => { releaseSourceWorkspace = resolve; });
  let releaseRepositoryBBranches!: () => void;
  const repositoryBBranchesGate = new Promise<void>((resolve) => { releaseRepositoryBBranches = resolve; });
  let releaseRepositoryB2Tree!: () => void;
  const repositoryB2TreeGate = new Promise<void>((resolve) => { releaseRepositoryB2Tree = resolve; });
  const workspaceRequests: string[] = [];
  const appSourceRequests: string[] = [];
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_race_a",
    name: "迟到源码 A",
    rootPath: "/srv/test-agent/app-source/race-a",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    managedApplications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    workspaces: [workspace(), sourceWorkspace],
    workspaceRequests,
    workspaceRequestGates: { wrk_source_race_a: sourceWorkspaceGate },
    appSourceRequests,
    appSourceRepositoryListGates: { app_gcms: listAGate },
    appSourceBranchGates: { "app_coss:repo-b": repositoryBBranchesGate },
    appSourceTreeGates: { "app_coss:repo-b2:release-b:.": repositoryB2TreeGate },
    appSourceRepositories: {
      app_gcms: [appSourceRepository({ name: "迟到仓库 A", generation: 8 })],
      app_coss: [
        appSourceRepository({ repositoryId: "repo-b", name: "当前仓库 B", generation: 2 }),
        appSourceRepository({ repositoryId: "repo-b2", name: "备用仓库 B", generation: 1, branch: "release-b" })
      ]
    },
    appSourceBranches: {
      "app_coss:repo-b": ["main-b"],
      "app_coss:repo-b2": ["release-b"]
    },
    appSourceTreeSnapshots: {
      "app_coss:repo-b2:release-b:.": {
        targetCommit: "commit-b2",
        nodes: [{ name: "src-b2", path: "src-b2", type: "directory", children: [] }]
      }
    },
    appSourceOpenResults: {
      "app_gcms:repo-code:8": appSourceOpenResult("wrk_source_race_a", 8)
    },
    recentWorkspaces: { app_gcms: null, app_coss: null }
  });

  await gotoWorkbench(page, { selectConversation: false });
  const fileExplorer = page.locator(".figma-file-explorer");
  await openAppSourceFromWorkspaceSwitch(page);
  await expect.poll(() => appSourceRequests).toContain("list:app_gcms");
  await page.getByRole("button", { name: "关闭源码列表" }).click();
  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await openAppSourceFromWorkspaceSwitch(page);
  await expect(page.getByRole("dialog", { name: "应用源码" })).toContainText("当前仓库 B");
  releaseListA();
  await page.waitForTimeout(100);
  await expect(page.getByRole("dialog", { name: "应用源码" })).not.toContainText("迟到仓库 A");
  await page.getByRole("button", { name: "下载版本库" }).click();
  const sourceDialog = page.getByRole("dialog", { name: "下载应用源码" });
  await sourceDialog.getByRole("button", { name: "选择当前仓库 B版本库" }).click();
  await expect.poll(() => appSourceRequests).toContain("branches:app_coss:repo-b");
  await sourceDialog.getByRole("button", { name: "选择备用仓库 B版本库" }).click();
  await sourceDialog.getByRole("button", { name: "下一步：选择分支与目录" }).click();
  await expect(sourceDialog.locator(".app-source-branch-select")).toContainText("release-b");
  await expect(sourceDialog.getByLabel("源码分支")).toBeEnabled();
  releaseRepositoryBBranches();
  await page.waitForTimeout(100);
  await expect(sourceDialog.locator(".app-source-branch-select")).toContainText("release-b");
  releaseRepositoryB2Tree();
  await expect(sourceDialog.getByText("固定提交：commit-b2")).toBeVisible();
  await sourceDialog.getByRole("button", { name: "关闭源码弹窗" }).click();

  await page.getByRole("button", { name: "F-COSS" }).click();
  await page.getByRole("option", { name: /F-GCMS/ }).click();
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "打开迟到仓库 A源码" }).click();
  await expect.poll(() => workspaceRequests).toContain("wrk_source_race_a");
  await page.getByRole("button", { name: "关闭源码列表" }).click();
  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  releaseSourceWorkspace();
  await page.waitForTimeout(150);

  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect(fileExplorer.getByText("源码快照", { exact: true })).toHaveCount(0);
});

test("a pending managed version cannot reclaim the workspace after a newer source snapshot activates", async ({ page }) => {
  let releaseManagedWorkspace!: () => void;
  const managedWorkspaceGate = new Promise<void>((resolve) => { releaseManagedWorkspace = resolve; });
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_newer",
    name: "较新的源码 B",
    rootPath: "/srv/test-agent/app-source/newer",
    appId: "app_gcms"
  };
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    recentWorkspaces: { app_gcms: null },
    workspaces: [workspace(), sourceWorkspace],
    fileRequests,
    workspaceRequestGates: { wrk_personal_default: managedWorkspaceGate },
    appSourceRepositories: { app_gcms: [appSourceRepository({ generation: 8, name: "源码 B" })] },
    appSourceOpenResults: {
      "app_gcms:repo-code:8": appSourceOpenResult("wrk_source_newer", 8)
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.locator(".ta-workbench-footer-branch").click();
  await page.getByRole("menuitem", { name: /F-GCMS/ }).hover();
  await page.getByRole("menuitem", { name: /2026年7月/ }).click();
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "打开源码 B源码" }).click();
  await expect(page.locator(".figma-file-explorer").getByText("源码快照", { exact: true })).toBeVisible();
  await expect.poll(() => fileRequests.filter((request) => request.path === "").at(-1)?.workspaceId)
    .toBe("wrk_source_newer");

  releaseManagedWorkspace();
  await page.waitForTimeout(150);

  await expect(page.locator(".figma-file-explorer").getByText("源码快照", { exact: true })).toBeVisible();
  expect(fileRequests.filter((request) => request.path === "").at(-1)?.workspaceId).toBe("wrk_source_newer");
});

test("starting a managed version immediately invalidates a source open that has not activated yet", async ({ page }) => {
  let releaseSourceOpen!: () => void;
  const sourceOpenGate = new Promise<void>((resolve) => { releaseSourceOpen = resolve; });
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_old",
    name: "迟到源码 A",
    rootPath: "/srv/test-agent/app-source/old",
    appId: "app_gcms"
  };
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    recentWorkspaces: { app_gcms: null },
    workspaces: [
      workspace(),
      { ...workspace(), workspaceId: "wrk_personal_default", name: "托管版本 B", appId: "app_gcms" },
      sourceWorkspace
    ],
    fileRequests,
    appSourceRepositories: { app_gcms: [appSourceRepository({ generation: 7, name: "源码 A" })] },
    appSourceOpenGates: { "app_gcms:repo-code:7": sourceOpenGate },
    appSourceOpenResults: {
      "app_gcms:repo-code:7": appSourceOpenResult("wrk_source_old", 7)
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "打开源码 A源码" }).click();
  await callAgentWorkbenchHandler(page, "handleSelectVersion", [{
      template: {
        workspaceId: "awp_1",
        workspaceName: "F-GCMS",
        appId: "app_gcms",
        repositoryId: "repo_1",
        defaultBranch: "main",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      },
      version: {
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: "2026年7月",
        branch: "feature_testagent_20260715",
        repoRootPath: "/Users/huang/workspace/app-feature",
        workspaceRootPath: "/Users/huang/workspace/app-feature/F-GCMS/workspace",
        status: "ACTIVE",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }
    }]);
  await expect(page.locator(".figma-file-explorer").getByRole("button", { name: "变更" })).toBeVisible();
  await expect.poll(() => fileRequests.filter((request) => request.path === "").at(-1)?.workspaceId)
    .toBe("wrk_personal_default");

  releaseSourceOpen();
  await page.waitForTimeout(150);

  await expect(page.locator(".figma-file-explorer").getByText("源码快照", { exact: true })).toHaveCount(0);
  await expect(page.locator(".figma-file-explorer").getByRole("button", { name: "变更" })).toBeVisible();
  expect(fileRequests.filter((request) => request.path === "").at(-1)?.workspaceId).toBe("wrk_personal_default");
});

test("DiffViewer save emit reaches the managed Agent writer behind the capability guard", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    content?: string;
  }> = [];
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    authRoles: ["SUPER_ADMIN", "APP_ADMIN"],
    agentFileFrames,
    historyDiffFiles: [{
      path: "tests/managed.ts",
      status: "modified",
      staged: false,
      patch: "@@ -1 +1 @@\n-old\n+new",
      additions: 1,
      deletions: 1
    }]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.locator(".figma-file-explorer").getByRole("button", { name: "变更" }).click();
  await page.locator('.git-file-row[title="tests/managed.ts"]').click();
  await expect(page.getByText("基线版本（只读）")).toBeVisible();
  await emitDiffViewerSave(page,
    "agent-workspace:wrk_personal_default:::agents%2Fmanaged.md",
    "# managed workspace Agent"
  );
  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(1);
  await emitDiffViewerSave(page,
    "agent-public:::agents%2Fpublic.md",
    "# managed public Agent"
  );
  await expect.poll(() => agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(2);
});

test("source Run Diff save dispatch blocks Agent writers and writes an ordinary source file", async ({ page }) => {
  const agentFileFrames: Array<{
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    content?: string;
  }> = [];
  const fileWriteRequests: Array<{ workspaceId: string; path: string; content: string }> = [];
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_diff_save",
    name: "Diff 保存源码",
    rootPath: "/srv/test-agent/app-source/diff-save",
    appId: "app_gcms"
  };
  const repository = appSourceRepository({ generation: 6, name: "Diff 保存源码库" });
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    authRoles: ["SUPER_ADMIN", "APP_ADMIN"],
    agentFileFrames,
    fileWriteRequests,
    fileContents: { "src/source.ts": "export const source = 'old';\n" },
    workspaces: [workspace(), sourceWorkspace],
    appSourceRepositories: { app_gcms: [repository] },
    appSourceOpenResults: {
      "app_gcms:repo-code:6": appSourceOpenResult("wrk_source_diff_save", 6)
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await openAppSourceFromWorkspaceSwitch(page);
  await page.getByRole("button", { name: "打开Diff 保存源码库源码" }).click();
  await expect(page.locator(".figma-file-explorer").getByText("源码快照", { exact: true })).toBeVisible();

  // 真实 RunEvent 投影让 source 模式挂载 DiffViewer；后续直接走组件 save-file -> 父级保存调度。
  await callAgentWorkbenchHandler(page, "loadDiffSource", ["run"]);
  await callAgentWorkbenchHandler(page, "handleRunEvent", [event(1, "diff.proposed", {
    files: [{
      path: "src/source.ts",
      status: "modified",
      patch: "@@ -1 +1 @@\n-export const source = 'old';\n+export const source = 'new';",
      additions: 1,
      deletions: 1
    }]
  })]);
  await expect(page.getByText("Run Diff", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "M source.ts" })).toBeVisible();

  await emitDiffViewerSave(page,
    "agent-workspace:wrk_source_diff_save:::agents%2Fsource-workspace.md",
    "# source workspace Agent"
  );
  await emitDiffViewerSave(page,
    "agent-public:::agents%2Fsource-public.md",
    "# source public Agent"
  );
  await page.waitForTimeout(100);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(0);
  expect(fileWriteRequests).toHaveLength(0);

  await emitDiffViewerSave(page, "src/source.ts", "export const source = 'saved';\n");
  await expect.poll(() => fileWriteRequests).toEqual([{
    workspaceId: "wrk_source_diff_save",
    path: "src/source.ts",
    content: "export const source = 'saved';\n"
  }]);
  expect(agentFileFrames.filter((frame) => frame.op === "agent-config.write")).toHaveLength(0);
});

test("entering source mode closes an already-open managed personal pull dialog", async ({ page }) => {
  const sourceWorkspace = {
    ...workspace(),
    workspaceId: "wrk_source_pull",
    name: "Pull 源码",
    rootPath: "/srv/test-agent/app-source/pull",
    appId: "app_gcms"
  };
  const repository = appSourceRepository({ generation: 4, name: "Pull 源码库" });
  await mockBackendApi(page, {
    ...agentWorkspaceSetup(),
    workspaces: [workspace(), sourceWorkspace],
    appSourceRepositories: { app_gcms: [repository] },
    appSourceOpenResults: {
      "app_gcms:repo-code:4": appSourceOpenResult("wrk_source_pull", 4)
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByLabel("更多工作空间操作").click();
  await page.getByRole("button", { name: "拉取远程", exact: true }).click();
  await expect(page.getByText("确认拉取远程更新", { exact: true })).toBeVisible();

  await callAgentWorkbenchHandler(page, "openAppSourceRepository", [repository]);

  await expect(page.locator(".figma-file-explorer").getByText("源码快照", { exact: true })).toBeVisible();
  await expect(page.getByText("确认拉取远程更新", { exact: true })).toHaveCount(0);
});

test("workbench does not read a workspace file tree before an application is selected", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  await mockBackendApi(page, {
    applications: [],
    authRoles: ["USER"],
    fileRequests
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "未选择应用" })).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toHaveCount(0);
  expect(fileRequests).toEqual([]);
});

test("revoking the selected application hides its retained workspace after membership refresh", async ({ page }) => {
  const managedApplications = [
    { appId: "app_gcms", appName: "F-GCMS", enabled: true },
    { appId: "app_coss", appName: "F-COSS", enabled: true }
  ];
  await mockBackendApi(page, {
    applications: [...managedApplications],
    managedApplications,
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      },
      app_coss: null
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toBeVisible();

  managedApplications.splice(0, 1);
  await page.evaluate(() => window.dispatchEvent(new Event("visibilitychange")));

  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect(page.getByRole("button", { name: /tests/ })).toHaveCount(0);
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await expect(page.getByText(/原工作区已从工作台隐藏/)).toBeVisible();

  await page.getByRole("button", { name: "F-COSS" }).click();
  await expect(page.getByRole("option", { name: /F-GCMS/ })).toHaveCount(0);
});

test("application recent workspace loads existing default personal worktree before loading files", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page);

  await expect.poll(() => personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  await expect.poll(() => fileRequests).toContainEqual({ workspaceId: "wrk_personal_default", path: "" });
  await expect(page.getByRole("button", { name: /worktree: feature_testagent_20260715_usr_admin_default/ }).first()).toBeVisible();
});

test("switch application forbidden feedback renders loading context details", async ({ page }) => {
  await mockBackendApi(page, {
    forbiddenRecentWorkspaces: {
      app_gcms: {
        code: "FORBIDDEN",
        message: "无该应用工作区权限",
        details: {
          appId: "app_gcms",
          appName: "F-GCMS",
          versionId: "awv_20260715",
          version: "20260715",
          workspaceKind: "default 私人工作区",
          workspaceName: "default",
          workspaceId: "wrk_personal_default",
          personalWorkspaceId: "psw_default"
        }
      }
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByText("切换应用失败")).toBeVisible();
  await expect(page.getByText(/应用 F-GCMS\(app_gcms\)/)).toBeVisible();
  await expect(page.getByText(/版本 20260715/)).toBeVisible();
  await expect(page.getByText(/工作区 default 私人工作区:default/)).toBeVisible();
  await expect(page.getByText(/workspaceId: wrk_personal_default/)).toBeVisible();
});

test("user avatar menu keeps the logout action hidden", async ({ page }) => {
  const logoutRequests: string[] = [];
  const processStatusRequests: string[] = [];
  await mockBackendApi(page, { logoutRequests, authRoles: ["APP_ADMIN"], processStatusRequests });

  await gotoWorkbench(page);
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(1);

  await page.getByRole("button", { name: "当前用户 admin" }).click();
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(2);
  await expect(page.getByText("运行中(server-a / 10.8.0.12:4096)")).toBeVisible();
  // 灰显的「应用管理员」角色行应在菜单顶部，且在用户名之前出现。
  const roleRow = page.locator(".figma-user-menu-role");
  await expect(roleRow).toBeVisible();
  await expect(roleRow).toHaveText("应用管理员");
  await expect(page.getByRole("button", { name: "通用问答" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "长程任务工作台" })).toHaveCount(0);
  await expect(page.getByRole("menuitem", { name: "退出登录" })).toHaveCount(0);
  expect(logoutRequests).toEqual([]);
});

test("login redirects to workbench and clears the initial opencode process checking state", async ({ page }) => {
  const loginRequests: Array<{ username?: string; password?: string }> = [];
  const processStatusRequests: string[] = [];
  await mockBackendApi(page, {
    skipInitialAuthToken: true,
    loginRequests,
    processStatusRequests
  });

  await page.goto("/985211", { waitUntil: "domcontentloaded" });
  await page.getByPlaceholder("用户名").fill("888888888");
  await page.getByPlaceholder("密码").fill("123456");
  await page.getByRole("button", { name: "登录" }).click();

  await expect.poll(() => loginRequests).toEqual([{ username: "888888888", password: "123456" }]);
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(1);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("登录后发送任务");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  await expect(page.getByText("正在检查 TestAgent 进程")).toHaveCount(0);
});

test("application switch menu excludes unjoined apps and keeps them in join dialog", async ({ page }) => {
  let releaseAuthMe!: () => void;
  const configurationApplicationRequests: string[] = [];
  const authMeGate = new Promise<void>((resolve) => {
    releaseAuthMe = resolve;
  });
  await mockBackendApi(page, {
    authRoles: ["SUPER_ADMIN"],
    authMeGate,
    configurationApplicationRequests,
    applications: [{ appId: "app_gcms", appName: "F-GCMS", enabled: true }],
    managedApplications: []
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "未选择应用" })).toBeVisible();

  releaseAuthMe();

  await expect(page.getByRole("button", { name: "未选择应用" })).toBeVisible();
  expect(configurationApplicationRequests).toContain("GET /api/internal/platform/configuration-management/applications");
  await page.getByRole("button", { name: "未选择应用" }).click();
  await expect(page.getByRole("option", { name: /F-GCMS/ })).toHaveCount(0);
  await page.getByRole("option", { name: /加入其他应用/ }).click();
  await expect(page.locator(".figma-add-app-card")).toContainText("加入其他应用");
  await page.locator(".figma-add-app-select").click();
  await expect(page.getByRole("option", { name: "F-GCMS" })).toBeVisible();
});

test("release-disabled question and workflow entries stay hidden for super admin", async ({ page }) => {
  await mockBackendApi(page, { authRoles: ["SUPER_ADMIN"] });

  await gotoWorkbench(page);
  await expect(page.getByRole("button", { name: "通用问答" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "长程任务工作台" })).toHaveCount(0);
  await page.getByRole("button", { name: "系统管理" }).click();

  await expect(page.getByRole("navigation", { name: "系统管理导航" })).toBeVisible();
  await expect(page.getByRole("button", { name: "通用参数管理" })).toBeVisible();
});

test("left activity pages expose stable URIs and restore through browser history", async ({ page }) => {
  await mockBackendApi(page, { authRoles: ["SUPER_ADMIN"] });

  await page.goto("/", { waitUntil: "domcontentloaded" });
  await expect(page).toHaveURL(/\/workbench$/);
  await expect(page.locator(".figma-app")).toBeVisible();

  await page.getByRole("button", { name: "工具盒子" }).click();
  await expect(page).toHaveURL(/\/toolbox$/);

  await page.getByRole("button", { name: "长期记忆" }).click();
  await expect(page).toHaveURL(/\/memories$/);

  await page.getByRole("button", { name: "系统管理" }).click();
  await expect(page).toHaveURL(/\/system$/);
  await expect(page.getByRole("navigation", { name: "系统管理导航" })).toBeVisible();

  await page.getByRole("button", { name: "Agent、Skill、MCP 与 Tool Hub" }).click();
  await expect(page).toHaveURL(/\/hub$/);

  await page.getByRole("button", { name: "打开工作台" }).click();
  await expect(page).toHaveURL(/\/workbench$/);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.getByRole("dialog", { name: "设置" })).toBeVisible();
  await page.getByRole("dialog", { name: "设置" }).getByRole("button", { name: "关闭", exact: true }).click();
  await expect(page).toHaveURL(/\/workbench$/);

  await page.goBack();
  await expect(page).toHaveURL(/\/hub$/);
  await expect(page.getByTestId("agent-skill-hub-button")).toHaveClass(/figma-activity-btn--active/);
  await page.goForward();
  await expect(page).toHaveURL(/\/workbench$/);

  await page.goto("/settings", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("dialog", { name: "设置" })).toBeVisible();
  await page.getByRole("dialog", { name: "设置" }).getByRole("button", { name: "关闭", exact: true }).click();
  await expect(page).toHaveURL(/\/workbench$/);
});

test("ordinary user opens toolbox immersively and browser history restores panels", async ({ page }) => {
  await mockBackendApi(page, { authRoles: ["USER"] });

  await gotoWorkbench(page, { selectConversation: false });
  const editorButton = page.getByRole("button", { name: "打开工作台" });
  const toolboxButton = page.getByRole("button", { name: "工具盒子" });
  await expect(editorButton).toBeVisible();
  await expect(toolboxButton).toBeVisible();
  await expect(page.getByRole("button", { name: "通用问答" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "长程任务工作台" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "系统管理" })).toHaveCount(0);
  expect(await editorButton.evaluate((node) => node.compareDocumentPosition(
    document.querySelector('[data-testid="toolbox-activity-button"]')!
  ) & Node.DOCUMENT_POSITION_FOLLOWING)).toBeTruthy();

  const leftPanel = page.locator(".figma-panel-left");
  const rightPanel = page.locator(".figma-chat-panel-wrapper");
  const initialLeftWidth = await leftPanel.evaluate((node) => getComputedStyle(node).width);
  const initialRightWidth = await rightPanel.evaluate((node) => getComputedStyle(node).width);
  expect(parseFloat(initialLeftWidth)).toBeGreaterThan(0);
  expect(parseFloat(initialRightWidth)).toBeGreaterThan(0);

  await toolboxButton.click();
  await expect(page).toHaveURL(/\/toolbox$/);
  await expect(page.getByRole("heading", { name: "工具盒子", exact: true })).toHaveClass(/sr-only/);
  await expect.poll(() => leftPanel.evaluate((node) => Number.parseFloat(getComputedStyle(node).width))).toBeLessThanOrEqual(2);
  await expect.poll(() => rightPanel.evaluate((node) => Number.parseFloat(getComputedStyle(node).width))).toBeLessThanOrEqual(2);

  await page.goBack();
  await expect(page).toHaveURL(/\/workbench$/);
  await expect(leftPanel).toHaveCSS("width", initialLeftWidth);
  await expect(rightPanel).toHaveCSS("width", initialRightWidth);

  await page.goForward();
  await expect(page).toHaveURL(/\/toolbox$/);
  await expect(page.getByRole("heading", { name: "工具盒子", exact: true })).toHaveClass(/sr-only/);

  await page.reload({ waitUntil: "domcontentloaded" });
  await expect(page).toHaveURL(/\/toolbox$/);
  await expect(page.getByRole("heading", { name: "工具盒子", exact: true })).toHaveClass(/sr-only/);

  await page.goto("/toolbox/");
  await expect(page).toHaveURL(/\/toolbox\/$/);
  await expect(page.getByRole("heading", { name: "工具盒子", exact: true })).toHaveClass(/sr-only/);
  await expect.poll(() => leftPanel.evaluate((node) => Number.parseFloat(getComputedStyle(node).width))).toBeLessThanOrEqual(2);
  await expect.poll(() => rightPanel.evaluate((node) => Number.parseFloat(getComputedStyle(node).width))).toBeLessThanOrEqual(2);
});

test("ordinary user opens generic memories, inspects the source conversation and restores the workbench layout", async ({ page }, testInfo) => {
  testInfo.setTimeout(90_000);
  await mockBackendApi(page, {
    authRoles: ["USER"],
    personalMemories: [{
      memoryId: "mem_e2e_1",
      scope: "PERSONAL_GLOBAL",
      ownerUserId: "usr_admin",
      applicationId: null,
      status: "ACTIVE",
      source: "NATIVE",
      content: "测试案例必须覆盖异常场景和边界条件",
      contentAvailable: true,
      displaySummary: "测试案例必须覆盖异常场景和边界条件",
      version: 2,
      confirmedAt: null,
      createdAt: "2026-08-01T00:00:00Z",
      updatedAt: "2026-08-09T00:00:00Z"
    }],
    teamMemories: [{
      memoryId: "mem_e2e_team_1",
      scope: "TEAM_APPLICATION",
      ownerUserId: null,
      applicationId: "app_gcms",
      status: "CANDIDATE",
      source: "TEAM_PROPOSAL",
      content: "缺陷分析结论必须附带可复核证据",
      contentAvailable: true,
      displaySummary: "缺陷分析结论必须附带可复核证据",
      version: 1,
      confirmedAt: null,
      createdAt: "2026-08-02T00:00:00Z",
      updatedAt: "2026-08-09T00:00:00Z"
    }],
    memorySkillProposals: [{
      proposalId: "msp_e2e_1",
      memoryId: "mem_e2e_team_1",
      applicationId: "app_gcms",
      title: "证据驱动缺陷分析",
      skillMdDraft: "# 证据驱动缺陷分析\n",
      status: "PENDING_REVIEW",
      createdByUserId: "usr_tester",
      reviewedByUserId: null,
      publishedAssetId: null,
      version: 1,
      createdAt: "2026-08-09T00:00:00Z",
      updatedAt: "2026-08-09T00:00:00Z"
    }],
    memoryEvidence: [{
      evidenceId: "mge_e2e_1",
      memoryId: "mem_e2e_1",
      runId: "run_e2e_1",
      sessionId: "ses_e2e_1",
      sessionTitle: "边界条件偏好讨论",
      transcriptAvailable: true,
      source: "NATIVE",
      summary: "用户连续三个会话要求覆盖边界条件",
      observedAt: "2026-08-08T00:00:00Z"
    }]
  });

  await gotoWorkbench(page, { selectConversation: false });
  const leftPanel = page.locator(".figma-panel-left");
  const rightPanel = page.locator(".figma-chat-panel-wrapper");
  const initialLeftWidth = await leftPanel.evaluate((node) => getComputedStyle(node).width);
  const initialRightWidth = await rightPanel.evaluate((node) => getComputedStyle(node).width);

  await page.getByRole("button", { name: "长期记忆" }).click();
  await expect(page).toHaveURL(/\/memories$/);
  await expect(page.getByRole("heading", { name: "长期记忆" })).toBeVisible();
  await expect(page.getByTestId("memory-tab-personal")).toContainText("我的记忆");
  await expect(page.getByTestId("memory-tab-team")).toContainText("团队记忆");
  await expect(page.getByTestId("memory-tab-skills")).toContainText("Skill 提案");
  await expect(page.getByTestId("memory-card-mem_e2e_1")).toBeVisible();
  await expect.poll(() => leftPanel.evaluate((node) => Number.parseFloat(getComputedStyle(node).width))).toBeLessThanOrEqual(2);
  await expect.poll(() => rightPanel.evaluate((node) => Number.parseFloat(getComputedStyle(node).width))).toBeLessThanOrEqual(2);

  await page.getByTestId("memory-card-mem_e2e_1").getByText("查看证据").click();
  await expect(page.getByTestId("memory-evidence-rail")).toBeVisible();
  await expect(page.getByText("用户连续三个会话要求覆盖边界条件")).toBeVisible();
  await expect(page.getByText("边界条件偏好讨论")).toBeVisible();
  await expect(page.getByText(/会话 ID ses_e2e_1 · Run ID run_e2e_1/)).toBeVisible();
  await expect(page.getByRole("link", { name: "打开原始对话" })).toHaveAttribute("href", "/s/ses_e2e_1");
  await expect(page.getByTestId("memory-evidence-rail").getByText("使用", { exact: true })).toBeVisible();
  await expect.poll(async () => Math.round((await page.locator(".memory-detail-drawer").boundingBox())?.x ?? -1)).toBe(760);
  await page.screenshot({ path: testInfo.outputPath("memory-center-desktop.png"), fullPage: true });

  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await expect(page.locator(".memory-center")).toHaveCSS("background-color", "rgb(17, 19, 24)");
  await expect(page.locator(".memory-detail-drawer")).toHaveCSS("background-color", "rgb(23, 25, 31)");
  await page.screenshot({ path: testInfo.outputPath("memory-center-dark.png"), fullPage: true });
  await page.evaluate(() => document.documentElement.classList.remove("dark"));

  await page.keyboard.press("Escape");
  await expect(page.locator(".memory-detail-drawer")).toBeHidden();
  await expect.poll(async () => Math.round((await page.locator(".memory-center").boundingBox())?.width ?? 0)).toBeGreaterThan(1200);
  await page.getByTestId("memory-tab-team").click();
  await expect(page.getByTestId("memory-card-mem_e2e_team_1")).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("memory-center-team.png"), fullPage: true });
  await page.getByTestId("memory-tab-skills").click();
  await expect(page.getByText("证据驱动缺陷分析")).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("memory-center-skills.png"), fullPage: true });
  await page.getByTestId("memory-tab-personal").click();
  await page.getByTestId("memory-card-mem_e2e_1").getByText("查看证据").click();
  await expect(page.getByTestId("memory-evidence-rail")).toBeVisible();
  await expect.poll(async () => Math.round((await page.locator(".memory-detail-drawer").boundingBox())?.x ?? -1)).toBe(760);

  await page.setViewportSize({ width: 560, height: 820 });
  await expect.poll(() => page.locator(".memory-detail-drawer").evaluate((node) => Math.round(node.getBoundingClientRect().width))).toBe(560);
  await expect.poll(async () => Math.round((await page.locator(".memory-detail-drawer").boundingBox())?.x ?? -1)).toBe(0);
  await page.screenshot({ path: testInfo.outputPath("memory-center-narrow.png"), fullPage: true });
  await page.keyboard.press("Escape");
  await expect(page.locator(".memory-detail-drawer")).toBeHidden();
  await page.setViewportSize({ width: 1440, height: 900 });

  await page.goBack();
  await expect(page).toHaveURL(/\/workbench$/);
  await expect(leftPanel).toHaveCSS("width", initialLeftWidth);
  await expect(rightPanel).toHaveCSS("width", initialRightWidth);
});

test("super admin configures generic memory profiles and rollout without clipped controls", async ({ page }, testInfo) => {
  const memorySettingsRequests: Array<Record<string, unknown>> = [];
  const memoryWhitelistEnableRequests: string[] = [];
  const memoryWhitelistDisableRequests: string[] = [];
  const memoryDirectoryUserQueries: string[] = [];
  await mockBackendApi(page, {
    authRoles: ["SUPER_ADMIN"],
    memoryAdminHealth: {
      enabled: true,
      memoryService: {
        available: true,
        status: "UP",
        version: "2.0.17",
        profiles: [{
          profileKey: "enterprise:enterprise/embedding-only:768:enterprise-v1",
          provider: "ENTERPRISE",
          model: "enterprise/embedding-only",
          dimension: 768,
          fingerprint: "enterprise-v1",
          collection: "memory_enterprise_v1",
          primary: true,
          available: true
        }, {
          profileKey: "cpu:bge-small-zh-v1.5:512:fixed-revision",
          provider: "CPU",
          model: "memory-bge-small-zh-v1.5",
          dimension: 512,
          fingerprint: "fixed-revision",
          collection: "memory_cpu_v1",
          primary: false,
          available: true
        }],
        projectionBacklog: { pending: 3, processing: 1, dead: 0 }
      },
      primaryChatModelId: "enterprise/chat-model",
      primaryEmbeddingModelId: "enterprise/embedding-only",
      cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
      queuePending: 2,
      queueProcessing: 1,
      queueDead: 0
    },
    memorySettings: {
      primaryChatModelId: "enterprise/chat-model",
      primaryEmbeddingModelId: "enterprise/embedding-only",
      cpuEmbeddingModelId: "memory-bge-small-zh-v1.5",
      version: 3,
      updatedByUserId: "usr_admin",
      updatedAt: "2026-08-09T00:00:00Z"
    },
    memoryWhitelist: [{
      userId: "usr_tester",
      enabled: true,
      updatedByUserId: "usr_admin",
      createdAt: "2026-08-09T00:00:00Z",
      updatedAt: "2026-08-09T00:00:00Z"
    }],
    memorySettingsRequests,
    memoryWhitelistEnableRequests,
    memoryWhitelistDisableRequests,
    memoryDirectoryUserQueries,
    memoryInternalModelProviders: {
      providers: [{
        providerId: "enterprise",
        name: "企业内部模型",
        baseUrl: "https://models.example.test",
        enabled: true,
        sortOrder: 1,
        tokenConfigured: true
      }],
      tokenConfigured: true
    },
    memoryInternalModelsByProvider: {
      enterprise: [{
        providerId: "enterprise",
        modelId: "enterprise/chat-model",
        upstreamModelId: "chat-model",
        displayName: "企业聊天模型 V1",
        enabled: true,
        declaredCapabilities: ["CHAT"],
        probedCapabilities: ["CHAT"]
      }, {
        providerId: "enterprise",
        modelId: "enterprise/chat-model-v2",
        upstreamModelId: "chat-model-v2",
        displayName: "企业聊天模型 V2",
        enabled: true,
        declaredCapabilities: ["CHAT"],
        probedCapabilities: ["CHAT"]
      }, {
        providerId: "enterprise",
        modelId: "enterprise/embedding-only",
        upstreamModelId: "embedding-only",
        displayName: "仅向量模型",
        embeddingDimension: 768,
        enabled: true,
        declaredCapabilities: ["EMBEDDING"],
        probedCapabilities: ["EMBEDDING"]
      }]
    },
    memoryDirectoryUsers: [{
      userId: "usr_88",
      username: "测试用户 88",
      unifiedAuthId: "AUTH88",
      status: "ACTIVE",
      roles: ["USER"],
      createdAt: "2026-08-09T00:00:00Z"
    }, {
      userId: "usr_inactive_88",
      username: "停用用户 88",
      unifiedAuthId: "INACTIVE88",
      status: "DISABLED",
      roles: ["USER"],
      createdAt: "2026-08-09T00:00:00Z"
    }]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "系统管理" }).click();
  await page.getByRole("button", { name: "记忆能力", exact: true }).click();
  await expect(page.getByTestId("memory-health-mem0")).toContainText("就绪");
  await expect(page.getByTestId("memory-health-embedding")).toContainText("2 / 2 可用");
  await expect(page.getByTestId("memory-health-chat")).toContainText("enterprise/chat-model");
  await expect(page.getByTestId("memory-health-queue")).toContainText("2 待处理");
  await expect(page.getByTestId("memory-health-projection")).toContainText("3 待投影");
  await expect(page.getByText("usr_tester")).toBeVisible();

  const panel = page.getByTestId("memory-admin-panel");
  await expect.poll(() => panel.evaluate((node) => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(0);
  const cards = page.locator(".memory-admin-card");
  await expect.poll(async () => {
    const [policy, rollout] = await Promise.all([cards.nth(0).boundingBox(), cards.nth(1).boundingBox()]);
    return policy && rollout ? Math.round(Math.min(policy.width, rollout.width)) : -1;
  }).toBeGreaterThanOrEqual(340);

  const modelSelect = page.getByRole("combobox", { name: "选择固定内部 CHAT 模型" });
  await modelSelect.click();
  const modelListbox = page.getByRole("listbox", { name: "选择固定内部 CHAT 模型" });
  await expect(modelListbox.getByRole("option")).toHaveCount(2);
  await modelListbox.getByRole("option", { name: /企业聊天模型 V2.*enterprise\/chat-model-v2/ }).click();
  const embeddingSelect = page.getByRole("combobox", { name: "选择企业 Embedding 模型" });
  await embeddingSelect.click();
  await page.getByRole("option", { name: /仅向量模型.*768 维.*enterprise\/embedding-only/ }).click();
  await page.getByRole("button", { name: "保存策略", exact: true }).click();
  await expect.poll(() => memorySettingsRequests.length).toBe(1);
  expect(memorySettingsRequests[0]).toEqual({
    primaryChatModelId: "enterprise/chat-model-v2",
    primaryEmbeddingModelId: "enterprise/embedding-only",
    expectedVersion: 3
  });

  const modelWrapper = page.locator(".memory-model-select .el-select__wrapper").first();
  await modelWrapper.hover();
  await page.locator(".memory-model-select .el-select__clear").click();
  await page.getByRole("button", { name: "保存策略", exact: true }).click();
  await expect.poll(() => memorySettingsRequests.length).toBe(2);
  expect(memorySettingsRequests[1]).toEqual({
    primaryChatModelId: null,
    primaryEmbeddingModelId: "enterprise/embedding-only",
    expectedVersion: 4
  });

  await page.getByRole("button", { name: "添加用户", exact: true }).click();
  let whitelistDialog = page.getByRole("dialog", { name: "添加白名单用户" });
  let whitelistSelect = whitelistDialog.getByRole("combobox", { name: "选择白名单用户" });
  await whitelistSelect.fill("88");
  let whitelistListbox = page.getByRole("listbox", { name: "选择白名单用户" });
  await expect(whitelistListbox.getByRole("option")).toHaveCount(1);
  await expect(whitelistListbox.getByRole("option")).toContainText("usr_88");
  const [listboxBox, cancelBox] = await Promise.all([
    whitelistListbox.boundingBox(),
    whitelistDialog.getByRole("button", { name: "取消", exact: true }).boundingBox()
  ]);
  expect(listboxBox && cancelBox
    ? Math.max(0, Math.min(listboxBox.y + listboxBox.height, cancelBox.y + cancelBox.height) - Math.max(listboxBox.y, cancelBox.y))
    : -1).toBe(0);
  await whitelistDialog.getByRole("button", { name: "取消", exact: true }).click();
  await expect(page.locator(".memory-user-dialog")).toBeHidden();

  await page.getByRole("button", { name: "添加用户", exact: true }).click();
  whitelistDialog = page.getByRole("dialog", { name: "添加白名单用户" });
  whitelistSelect = whitelistDialog.getByRole("combobox", { name: "选择白名单用户" });
  await whitelistSelect.fill("88");
  whitelistListbox = page.getByRole("listbox", { name: "选择白名单用户" });
  await whitelistListbox.getByRole("option", { name: /测试用户 88.*AUTH88.*usr_88/ }).click();
  await whitelistDialog.getByRole("button", { name: "确认添加", exact: true }).click();
  await expect.poll(() => memoryWhitelistEnableRequests).toEqual(["usr_88"]);
  await expect(page.getByRole("button", { name: "移出 usr_88" })).toBeVisible();
  await page.getByRole("button", { name: "移出 usr_88" }).click();
  await page.getByRole("dialog", { name: "移出白名单" }).getByRole("button", { name: "移出", exact: true }).click();
  await expect.poll(() => memoryWhitelistDisableRequests).toEqual(["usr_88"]);
  expect(memoryDirectoryUserQueries).toContain("88");

  await page.getByRole("button", { name: "查看技术信息", exact: true }).click();
  await expect(page.locator("#embedding-technical-details")).toContainText("fixed-revision");
  await page.getByRole("button", { name: "收起技术信息", exact: true }).click();
  await page.screenshot({ path: testInfo.outputPath("memory-admin.png"), fullPage: true });

  await page.evaluate(() => document.documentElement.classList.add("dark"));
  await expect(panel).toHaveCSS("background-color", "rgb(17, 19, 24)");
  await page.screenshot({ path: testInfo.outputPath("memory-admin-dark.png"), fullPage: true });
  await page.evaluate(() => document.documentElement.classList.remove("dark"));

  await page.setViewportSize({ width: 560, height: 820 });
  await page.getByRole("button", { name: "添加用户", exact: true }).click();
  const narrowDialog = page.locator(".memory-user-dialog");
  await expect(narrowDialog).toBeVisible();
  const narrowBox = await narrowDialog.boundingBox();
  expect(narrowBox).not.toBeNull();
  expect(narrowBox!.x).toBeGreaterThanOrEqual(0);
  expect(narrowBox!.x + narrowBox!.width).toBeLessThanOrEqual(560);
  await expect.poll(() => page.locator("html").evaluate((node) => node.scrollWidth - node.clientWidth)).toBeLessThanOrEqual(0);
  await page.keyboard.press("Escape");
  await expect(narrowDialog).toBeHidden();

  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator(".embedding-technical__toggle svg")).toHaveCSS("transition-duration", "0s");
});

test("toolbox keeps search source and clear filters on one row at tablet width", async ({ page }) => {
  await page.setViewportSize({ width: 1000, height: 900 });
  await mockBackendApi(page, { authRoles: ["USER"] });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "工具盒子" }).click();

  const search = page.locator(".toolbox-search");
  const source = page.locator(".toolbox-source-filter");
  const clear = page.getByRole("button", { name: "清除筛选", exact: true });
  await expect(search).toBeVisible();
  await expect(source).toBeVisible();
  await expect(clear).toBeVisible();

  const controlTops = await Promise.all([search, source, clear].map((locator) => locator.evaluate((node) => node.getBoundingClientRect().top)));
  expect(controlTops[0]).toBe(controlTops[1]);
  expect(controlTops[1]).toBe(controlTops[2]);
});

test("toolbox controls stick to the panel scroll container on desktop", async ({ page, isMobile }) => {
  test.skip(isMobile, "当前项目没有移动端产品内容，工具盒子滚动容器只在桌面视口覆盖。");
  await page.setViewportSize({ width: 1280, height: 420 });
  await mockBackendApi(page, { authRoles: ["USER"], toolboxTools: toolboxScrollTools() });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "工具盒子" }).click();

  const panel = page.locator(".toolbox-panel");
  const controls = page.locator(".toolbox-controls");
  await expect(panel).toBeVisible();
  expect(await panel.evaluate((node) => node.scrollHeight > node.clientHeight)).toBe(true);

  await panel.evaluate((node) => { node.scrollTop = 240; });
  await expect.poll(() => panel.evaluate((node) => node.scrollTop)).toBeGreaterThan(0);
  await expect.poll(async () => {
    const [panelTop, controlsTop] = await Promise.all([
      panel.evaluate((node) => node.getBoundingClientRect().top),
      controls.evaluate((node) => node.getBoundingClientRect().top)
    ]);
    return Math.abs(panelTop - controlsTop);
  }).toBeLessThanOrEqual(1);
  expect(await page.evaluate(() => window.scrollY)).toBe(0);
});

test("toolbox category tags stay on one narrow viewport row and support keyboard activation", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 700 });
  await mockBackendApi(page, { authRoles: ["USER"] });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "工具盒子" }).click();

  const categories = page.locator(".toolbox-category-filter");
  await expect(categories).toBeVisible();
  const layout = await categories.evaluate((node) => {
    const style = getComputedStyle(node);
    return {
      flexWrap: style.flexWrap,
      overflowX: style.overflowX,
      scrollWidth: node.scrollWidth,
      clientWidth: node.clientWidth
    };
  });
  expect(layout.flexWrap).toBe("nowrap");
  expect(layout.overflowX).toBe("auto");
  expect(layout.scrollWidth).toBeGreaterThan(layout.clientWidth);

  const security = page.getByRole("button", { name: "安全与加密 1" });
  await security.focus();
  await expect(security).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(security).toHaveAttribute("aria-pressed", "true");

  const panel = page.locator(".toolbox-panel");
  const controls = page.locator(".toolbox-controls");
  await panel.evaluate((node) => { node.scrollTop = 320; });
  await expect.poll(async () => {
    const [panelTop, controlsTop] = await Promise.all([
      panel.evaluate((node) => node.getBoundingClientRect().top),
      controls.evaluate((node) => node.getBoundingClientRect().top)
    ]);
    return Math.abs(panelTop - controlsTop);
  }).toBeLessThanOrEqual(1);
});

test("settings dialog manages application context and SSH key metadata", async ({ page }) => {
  await mockBackendApi(page);

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(page.getByText("应用人员管理")).toBeVisible();
  // Element Plus 的 el-select 是自定义组件：选中值显示在 .el-select__placeholder 的 span 中，
  // readonly input 的 value 始终为空，不能用 toHaveValue 校验。这里改为校验选中应用名。
  await expect(page.locator(".el-select").filter({ has: page.getByRole("combobox", { name: "应用选择" }) }).getByText("F-GCMS")).toBeVisible();

  await page.getByRole("button", { name: "个人设置" }).click();
  await page.getByPlaceholder("SSH key 名称").fill("work");
  await page.getByPlaceholder("-----BEGIN OPENSSH PRIVATE KEY-----").fill("-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----");
  await page.getByRole("button", { name: "添加 SSH key" }).click();

  await expect(dialog.getByText("SHA256:abc")).toBeVisible();
  await expect(dialog.getByText("secret")).toHaveCount(0);
  await expect(dialog.locator("textarea")).toHaveCount(0);
});

test("settings dialog grants application context to super admin", async ({ page }) => {
  await mockBackendApi(page, { authRoles: ["SUPER_ADMIN"] });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByRole("button", { name: "应用管理" })).toBeVisible();
  await expect(page.getByText("应用人员管理")).toBeVisible();
  await expect(page.locator(".el-select").filter({ has: page.getByRole("combobox", { name: "应用选择" }) }).getByText("F-GCMS")).toBeVisible();
});

test("settings dialog loads application context after roles arrive while open", async ({ page }) => {
  let releaseAuthMe!: () => void;
  const configurationApplicationRequests: string[] = [];
  const authMeGate = new Promise<void>((resolve) => {
    releaseAuthMe = resolve;
  });
  await mockBackendApi(page, { authRoles: ["SUPER_ADMIN"], authMeGate, configurationApplicationRequests });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByRole("button", { name: "个人设置" })).toBeVisible();
  expect(configurationApplicationRequests.every((request) => request.startsWith("GET "))).toBe(true);

  releaseAuthMe();
  await expect(page.getByRole("button", { name: "应用管理" })).toBeVisible();
  // 角色异步到达只补充可用导航，不抢占用户已经打开的个人设置页。
  await expect(page.getByText("个人设置", { exact: true })).toBeVisible();
  expect(configurationApplicationRequests.every((request) => request.startsWith("GET "))).toBe(true);
});

test("settings dialog shows permission placeholder for non app admins", async ({ page }) => {
  const configurationApplicationRequests: string[] = [];
  await mockBackendApi(page, { authRoles: ["USER"], configurationApplicationRequests });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByRole("button", { name: "应用管理" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "个人设置" })).toBeVisible();
  expect(configurationApplicationRequests.every((request) => request.startsWith("GET "))).toBe(true);
});

test("settings dialog shows empty role placeholder for users without roles", async ({ page }) => {
  await mockBackendApi(page, { authRoles: [] });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "系统设置" }).click();
  await expect(page.getByRole("button", { name: "应用管理" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "个人设置" })).toBeVisible();
});

test("empty application workspace state does not expose local directory picker", async ({ page }) => {
  await mockBackendApi(page, { recentWorkspaces: { app_gcms: null } });

  await gotoWorkbench(page);

  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
});

test("application without recent version does not fallback to first template version", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    recentWorkspaces: { app_gcms: null },
    workspaceTemplates: {
      app_gcms: [
        {
          workspaceId: "awp_main",
          workspaceName: "F-GCMS 主服务",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    },
    workspaceVersions: {
      "app_gcms:awp_main": [
        {
          versionId: "awv_20260715",
          applicationWorkspaceId: "awp_main",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: "20260715",
          branch: "feature_testagent_20260715",
          repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
          status: "ACTIVE",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();
  await expect.poll(() => defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
});

test("version selection checks git access and prompts for repository permission before creating worktree", async ({ page }) => {
  const gitAccessRequests: string[] = [];
  const defaultPersonalRequests: string[] = [];
  await page.addInitScript(() => {
    window.open = ((url?: string | URL, target?: string, features?: string) => {
      Object.assign(window, {
        __testOpenedExternalUrl: [String(url), target, features]
      });
      return null;
    }) as typeof window.open;
  });
  await mockBackendApi(page, {
    recentWorkspaces: { app_gcms: null },
    gitAccessRequests,
    defaultPersonalRequests,
    gitAccessResults: {
      awv_20260715: {
        accessible: false,
        repositoryId: "repo_1",
        repositoryName: "F-GCMS 测试版本库",
        branch: "feature_testagent_20260715",
        reason: "REPOSITORY_PERMISSION_REQUIRED"
      }
    },
    workspaceTemplates: {
      app_gcms: [{
        workspaceId: "awp_main",
        workspaceName: "本地-测试",
        appId: "app_gcms",
        repositoryId: "repo_1",
        branch: "main",
        standard: true,
        directoryPath: "F-GCMS/workspace",
        createdAt: "2026-06-24T00:00:00Z",
        updatedAt: "2026-06-24T00:00:00Z"
      }]
    },
    workspaceVersions: {
      "app_gcms:awp_main": [{
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_main",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: "20260715",
        branch: "feature_testagent_20260715",
        repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
        workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
        status: "ACTIVE",
        createdAt: "2026-06-24T00:00:00Z",
        updatedAt: "2026-06-24T00:00:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.locator(".ta-workbench-footer-branch").click();
  await page.getByRole("menuitem", { name: /本地-测试/ }).hover();
  await page.getByRole("menuitem", { name: /20260715/ }).click();

  await expect(page.getByText("需要申请版本库权限")).toBeVisible();
  await expect(page.getByText(/F-GCMS 测试版本库/)).toBeVisible();
  await expect(page.getByText(/scm-gmp\.sdc\.cs\.icbc\/icbc\/gmp\/index\.jsp#@/)).toBeVisible();
  await page.getByRole("button", { name: "前往申请" }).click();
  await expect.poll(() => page.evaluate(() => (
    window as typeof window & { __testOpenedExternalUrl?: string[] }
  ).__testOpenedExternalUrl)).toEqual([
    "https://scm-gmp.sdc.cs.icbc/icbc/gmp/index.jsp#@",
    "_blank",
    "noopener,noreferrer"
  ]);
  expect(gitAccessRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
});

test("application recent version without default personal workspace stays empty", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  await mockBackendApi(page, {
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    personalWorkspaces: {
      awv_20260715: []
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page);

  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();
  await expect.poll(() => personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
});

test("model picker groups models by provider and updates run model", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), runRequests });

  await gotoWorkbench(page);
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();

  await page.getByRole("button", { name: "切换模型" }).click();
  await expect(page.getByRole("dialog", { name: "模型选择" })).toBeVisible();
  await expect(page.locator(".figma-chat-model-group-title", { hasText: "Anthropic" })).toBeVisible();
  await expect(page.locator(".figma-chat-model-group-title", { hasText: "Volcengine Ark" })).toBeVisible();
  await page.getByPlaceholder("搜索模型...").fill("glm");
  const glmOption = page.locator(".figma-chat-model-option-item").filter({ hasText: "GLM-5.2" });
  await expect(glmOption).toBeVisible();
  await glmOption.click();
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("GLM-5.2");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use selected model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use selected model",
    model: "volcengine/glm-5.2"
  });
});

test("model picker recovers automatically when the first catalog response after restart is empty", async ({ page }) => {
  await mockBackendApi(page, {
    modelResponses: [
      [],
      [{ id: "recovered-model", providerId: "anthropic", name: "Recovered Model" }]
    ],
    providers: [{ id: "anthropic", name: "Anthropic", status: "ready" }]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "切换模型" }).click();
  await expect(page.getByRole("dialog", { name: "模型选择" })).toContainText("暂无匹配模型");
  await expect(
    page.getByRole("dialog", { name: "模型选择" }).getByRole("button", { name: /Recovered Model/ }).first()
  ).toBeVisible({ timeout: 8_000 });
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("Recovered Model");
});

test("new runs use one in-memory conversation context and a client request id", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), runRequests, runContextRequests });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("context run");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runContextRequests).toEqual(["ses_1"]);
  expect(runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    contextToken: "ctx_e2e_1"
  });
  expect(String(runRequests[0]?.clientRequestId)).toMatch(/^req_/);
});

test("batch test cases start isolated runs, retry failures, and create isolated scheduled tasks", async ({ page }) => {
  const batchSessionRequests: Array<Record<string, unknown>> = [];
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  const nightTaskRequests: Array<Record<string, unknown>> = [];
  const nightTasks: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    batchSessionRequests,
    runRequests,
    runContextRequests,
    nightTaskRequests,
    nightTasks,
    runIds: ["run_batch_1", "run_batch_2"],
    runEventsByRunId: { run_batch_1: [], run_batch_2: [] },
    fileReadFailureAttempts: {
      "spec/支付需求/01-需求/退款/需求.md": [1]
    },
    fileContents: {
      "spec/账户需求/01-需求/密码重置/需求.md": "密码重置需求正文",
      "spec/账户需求/02-设计/密码重置/设计.md": "密码重置设计正文",
      "spec/支付需求/01-需求/退款/需求.md": "退款需求正文"
    }
  });

  await gotoWorkbench(page);
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("保留当前输入内容");
  await page.locator(".figma-chat-composer").hover();
  await page.getByTestId("batch-test-case-entry").click();

  const dialog = page.getByTestId("batch-test-case-dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText("密码重置");
  await expect(dialog).toContainText("退款");
  await dialog.getByTestId("batch-select-all").click();
  await dialog.getByTestId("batch-execute-now").click();
  await expect(dialog.getByTestId("batch-creation-progress")).toBeVisible();
  await expect(dialog.getByTestId("batch-requirement-input")).toHaveCount(0);

  await expect.poll(() => batchSessionRequests.length).toBe(1);
  await expect.poll(() => runRequests.length).toBe(1);
  await dialog.getByRole("button", { name: "重试", exact: true }).click();
  await expect.poll(() => batchSessionRequests.length).toBe(2);
  await expect.poll(() => runRequests.length).toBe(2);
  const immediateBatchIds = new Set(batchSessionRequests.map((request) => String(
    (request.batchContext as Record<string, unknown>)?.batchId
  )));
  expect(immediateBatchIds.size).toBe(1);
  expect(new Set(batchSessionRequests.map((request) => String(
    (request.batchContext as Record<string, unknown>)?.itemRequestId
  ))).size).toBe(2);
  expect(new Set(batchSessionRequests.map((request) => request.title))).toEqual(new Set([
    "账户需求 密码重置 测试案例",
    "支付需求 退款 测试案例"
  ]));
  expect(new Set(runRequests.map((request) => request.sessionId))).toEqual(
    new Set(["ses_batch_1", "ses_batch_2"])
  );
  expect(new Set(runContextRequests)).toEqual(new Set(["ses_batch_1", "ses_batch_2"]));
  expect(new Set(runRequests.map((request) => String(request.prompt)))).toEqual(new Set([
    "请生成子条目测试案例。\n\n需求项：账户需求\n子条目：密码重置",
    "请生成子条目测试案例。\n\n需求项：支付需求\n子条目：退款"
  ]));
  expect(new Set(runRequests.flatMap((request) => request.parts as Array<Record<string, unknown>>)
    .filter((part) => part.type === "file")
    .map((part) => part.content))).toEqual(new Set(["密码重置需求正文", "密码重置设计正文", "退款需求正文"]));

  await expect(dialog).toContainText("已创建会话 2");
  await dialog.getByTestId("batch-dialog-close").click();
  await expect(dialog).toHaveCount(0);

  await page.locator(".figma-chat-composer").hover();
  await page.getByTestId("batch-test-case-entry").click();
  const scheduledDialog = page.getByTestId("batch-test-case-dialog");
  await expect(scheduledDialog.getByTestId("batch-requirement-input")).toHaveValue("请生成子条目测试案例。");
  await expect(scheduledDialog.locator('input[data-testid="batch-item-checkbox"]:checked')).toHaveCount(0);
  await scheduledDialog.getByTestId("batch-select-all").click();
  await scheduledDialog.getByTestId("batch-open-schedule").click();
  await expect(scheduledDialog.getByTestId("batch-close-schedule")).toBeVisible();
  await expect(scheduledDialog.getByTestId("batch-execute-now")).toHaveCount(0);
  // 2 个子条目、单时段余量充足时自动推荐并选中该时段，直接展示定时执行与智能推荐提示。
  await expect(scheduledDialog.getByTestId("batch-schedule-recommend-hint")).toBeVisible();
  await expect(scheduledDialog.getByTestId("batch-execute-scheduled")).toBeVisible();
  await scheduledDialog.getByTestId("batch-execute-scheduled").click();
  await expect(scheduledDialog.getByTestId("batch-creation-progress")).toBeVisible();
  await expect.poll(() => nightTaskRequests.length).toBe(2);
  expect(nightTaskRequests.every((request) => request.sessionId == null)).toBe(true);
  const scheduledBatchIds = new Set(nightTaskRequests.map((request) => String(
    (request.batchContext as Record<string, unknown>)?.batchId
  )));
  expect(scheduledBatchIds.size).toBe(1);
  expect([...scheduledBatchIds][0]).not.toBe([...immediateBatchIds][0]);
  expect(new Set(nightTaskRequests.map((request) => String(
    (request.batchContext as Record<string, unknown>)?.itemRequestId
  ))).size).toBe(2);
  expect(new Set(nightTasks.map((task) => task.sessionId))).toEqual(
    new Set(["ses_night_created_1", "ses_night_created_2"])
  );
  await expect(scheduledDialog).toContainText("已创建会话 2");
  await scheduledDialog.getByTestId("batch-dialog-close").click();
  await expect(scheduledDialog).toHaveCount(0);
  await expect(composer).toHaveValue("保留当前输入内容");
  await expect(page.locator(".figma-chat-title")).toHaveText("");
});

test("a blank conversation schedules a night task and restores it from the pending tab", async ({ page }, testInfo) => {
  const nightTaskRequests: Array<Record<string, unknown>> = [];
  const nightTasks: Array<Record<string, unknown>> = [];
  const sessionMessagesBySessionId: Record<string, Array<Record<string, unknown>>> = {};
  const runsByRunId: Record<string, Record<string, unknown>> = {};
  const backendState = {
    ...runnableWorkspaceSetup(),
    nightTaskRequests,
    nightTasks,
    sessionMessagesBySessionId,
    runsByRunId,
    activeRun: null as Record<string, unknown> | null,
    runEventsByRunId: { run_night_e2e: [] }
  };
  await mockBackendApi(page, backendState);

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("夜间执行完整回归");
  await page.getByRole("button", { name: "定时执行" }).click();
  await expect(page.getByTestId("night-slot-picker")).toBeVisible();
  await expect(page.getByTestId("night-slot-picker")).not.toContainText("执行位置");
  await expect(page.getByTestId("night-schedule-confirm")).toBeEnabled();
  await page.getByTestId("night-schedule-confirm").click();

  await expect.poll(() => nightTaskRequests.length).toBe(1);
  expect(nightTaskRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    prompt: "夜间执行完整回归",
    scheduleMode: "NIGHT_WINDOW",
    slotStart: "2026-07-18T13:15:00Z"
  });
  expect(nightTaskRequests[0]?.sessionId).toBeUndefined();
  await expect(page.getByTestId("current-night-task-card")).toContainText("等待执行");
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();

  await page.reload({ waitUntil: "domcontentloaded" });
  await page.getByRole("button", { name: "会话列表" }).click();
  await expect(page.getByRole("dialog", { name: "会话列表" })).toHaveAttribute(
    "data-placement",
    testInfo.project.name === "mobile" ? "overlay" : "left"
  );
  await page.getByTestId("session-list-night-tasks-tab").click();
  await expect(page.getByTestId("night-task-list")).toContainText("夜间执行完整回归");

  // 模拟后台按时投递：待执行项消失，既有会话接口返回真实 USER 消息和现有 Run。
  const dispatchedTask = nightTasks[0]!;
  dispatchedTask.status = "DISPATCHED";
  dispatchedTask.runId = "run_night_e2e";
  dispatchedTask.updatedAt = "2026-07-18T13:16:00Z";
  sessionMessagesBySessionId.ses_night_created = [{
    messageId: "msg_night_e2e",
    sessionId: "ses_night_created",
    runId: "run_night_e2e",
    role: "USER",
    content: "夜间执行完整回归",
    sourceType: "SCHEDULED_TASK",
    sourceRefId: dispatchedTask.taskId,
    createdAt: "2026-07-18T13:16:00Z",
    updatedAt: "2026-07-18T13:16:00Z"
  }, {
    messageId: "msg_night_e2e_assistant",
    sessionId: "ses_night_created",
    runId: "run_night_e2e",
    role: "ASSISTANT",
    content: "夜间回归执行完成",
    parts: [{
      partId: "part_night_step_finish",
      type: "step-finish",
      reason: "stop",
      tokens: { total: 1_234 }
    }],
    createdAt: "2026-07-18T13:28:34Z",
    updatedAt: "2026-07-18T13:28:34Z"
  }];
  runsByRunId.run_night_e2e = {
    runId: "run_night_e2e",
    sessionId: "ses_night_created",
    workspaceId: "wrk_personal_default",
    status: "SUCCEEDED",
    sourceType: "SCHEDULED_TASK",
    sourceRefId: dispatchedTask.taskId,
    createdAt: "2026-07-18T13:16:00Z",
    updatedAt: "2026-07-18T13:28:34Z"
  };
  await page.getByRole("button", { name: "查看对话" }).click();
  await expect(page.getByRole("dialog", { name: "会话列表" })).toBeVisible();
  await expect(page.getByTestId("session-list-night-tasks-tab")).toHaveAttribute("aria-selected", "true");
  await expect(page.locator(".oc-user-message__source-badge")).toContainText("夜间定时执行");
  await expect(page.locator(".oc-user-message__source-badge")).toContainText("21:16");
  await expect(page.locator(".figma-chat-usage-value")).toContainText("12m 34s");
  await expect(page.locator(".figma-chat-usage-value")).toContainText("1.2k tokens");
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.getByTestId("current-night-task-card")).toHaveCount(0);
});

test("a super administrator can schedule a daytime 测试定时 to the minute", async ({ page }) => {
  const nightTaskRequests: Array<Record<string, unknown>> = [];
  const nightTasks: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    authRoles: ["SUPER_ADMIN"],
    nightTaskRequests,
    nightTasks
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("白天验证一分钟扫描");
  await page.getByRole("button", { name: "定时执行" }).click();
  await expect(page.getByTestId("schedule-mode-night")).toHaveClass(/is-active/);
  await page.getByTestId("schedule-mode-custom").click();
  await page.getByTestId("custom-schedule-plus-3").click();
  await page.getByTestId("night-schedule-confirm").click();

  await expect.poll(() => nightTaskRequests.length).toBe(1);
  expect(nightTaskRequests[0]).toMatchObject({
    workspaceId: "wrk_personal_default",
    prompt: "白天验证一分钟扫描",
    scheduleMode: "ADMIN_CUSTOM"
  });
  const slotStart = new Date(String(nightTaskRequests[0]?.slotStart));
  expect(slotStart.getUTCSeconds()).toBe(0);
  expect(slotStart.getUTCMilliseconds()).toBe(0);
  await expect(page.getByTestId("current-night-task-card")).toContainText("测试定时");
  await expect(page.getByTestId("current-night-task-card")).not.toContainText("–");
});

test("an existing-session night task locks only that conversation", async ({ page }) => {
  const nightTaskRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    nightTaskRequests,
    sessions: [session()]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await selectPetContextSession(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("已有会话夜间回归");
  await page.getByRole("button", { name: "定时执行" }).click();
  await expect(page.getByTestId("night-schedule-confirm")).toBeEnabled();
  await page.getByTestId("night-schedule-confirm").click();

  await expect.poll(() => nightTaskRequests.length).toBe(1);
  expect(nightTaskRequests[0]).toMatchObject({ sessionId: "ses_1", prompt: "已有会话夜间回归" });
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "新建对话" })).toBeEnabled();

  await page.getByRole("button", { name: "新建对话" }).click();
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("其他对话仍可发送");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  await expect(page.getByRole("button", { name: "定时执行" })).toBeEnabled();
});

test("expired conversation context retries once with the same client request id", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runContextRequests,
    runContextTokens: ["ctx_old", "ctx_new"],
    runFailures: ["CONVERSATION_CONTEXT_EXPIRED"]
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("retry context");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(2);
  expect(runContextRequests).toEqual(["ses_1", "ses_1"]);
  expect(runRequests.map((request) => request.contextToken)).toEqual(["ctx_old", "ctx_new"]);
  expect(runRequests[0]?.clientRequestId).toBe(runRequests[1]?.clientRequestId);
});

test("pending startRun does not poll active-run every 1.5 seconds", async ({ page }) => {
  let releaseRunRequest!: () => void;
  const runRequestGate = new Promise<void>((resolve) => {
    releaseRunRequest = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const activeRunRequests: string[] = [];
  const runEventRequests: string[] = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), runRequests, activeRunRequests, runEventRequests, runRequestGate });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("slow start");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  await page.waitForTimeout(1800);
  expect(activeRunRequests.length).toBeLessThanOrEqual(1);
  const fallbackCount = activeRunRequests.length;
  await page.waitForTimeout(1800);
  expect(activeRunRequests).toHaveLength(fallbackCount);
  releaseRunRequest();
  await expect.poll(() => runEventRequests).toContain("/api/internal/agent/opencode/runs/run_1/events");
});

test("runtime-state uses the SSE snapshot without a parallel HTTP read", async ({ page }) => {
  const runtimeStateHttpRequests: string[] = [];
  await mockBackendApi(page, { runtimeStateHttpRequests });

  await gotoWorkbench(page);
  await page.waitForTimeout(200);

  expect(runtimeStateHttpRequests).toEqual([]);
});

test("runtime-state reconciles a run completed while the page was disconnected and re-enables follow-up", async ({ page }) => {
  let releaseCompletedRuntimeSnapshot!: () => void;
  const completedRuntimeSnapshotGate = new Promise<void>((resolve) => {
    releaseCompletedRuntimeSnapshot = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runDetailRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runDetailRequests,
    runDetailFailuresBeforeSuccess: { run_1: 1 },
    runEvents: [],
    runtimeStateEventGate: completedRuntimeSnapshotGate,
    runtimeStateSummary: {
      runningCount: 0,
      questionCount: 0,
      sessions: [],
      generatedAt: "2026-07-10T00:00:00Z"
    },
    runsByRunId: {
      run_1: {
        runId: "run_1",
        sessionId: "ses_1",
        workspaceId: "wrk_1234567890abcdef",
        status: "SUCCEEDED",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T01:00:00Z"
      }
    }
  });

  await gotoWorkbench(page);
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("隔夜执行任务");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.getByRole("button", { name: "停止执行" })).toBeEnabled();
  releaseCompletedRuntimeSnapshot();
  await expect.poll(() => runDetailRequests.filter((runId) => runId === "run_1").length).toBe(2);
  await composer.fill("白天继续追问");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
});

test("runtime-state does not reconcile a newer run from an older snapshot", async ({ page }) => {
  let releaseOlderRuntimeSnapshot!: () => void;
  const olderRuntimeSnapshotGate = new Promise<void>((resolve) => {
    releaseOlderRuntimeSnapshot = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runDetailRequests: string[] = [];
  const runtimeStateEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runDetailRequests,
    runtimeStateEventRequests,
    runEvents: [],
    runtimeStateEventGate: olderRuntimeSnapshotGate,
    runtimeStateSummary: {
      runningCount: 0,
      questionCount: 0,
      sessions: [],
      generatedAt: "2026-06-18T23:59:59Z"
    }
  });

  await gotoWorkbench(page);
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("刚启动的新任务");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.getByRole("button", { name: "停止执行" })).toBeEnabled();
  releaseOlderRuntimeSnapshot();
  await expect.poll(() => runtimeStateEventRequests.length).toBeGreaterThanOrEqual(2);
  expect(runDetailRequests).toEqual([]);
  await expect(page.getByRole("button", { name: "停止执行" })).toBeEnabled();
});

test("run snapshot reset replaces stale live output with the materialized snapshot", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runEvents: [
      event(1, "message.part.delta", {
        messageId: "msg_runtime",
        partId: "part_text",
        partType: "text",
        delta: "即将被快照替换的旧增量"
      }),
      event(0, "run.snapshot.reset", {
        reason: "TRANSIENT_SNAPSHOT_RECOVERY",
        resetGeneration: 0,
        earliestSeq: 1,
        snapshot: {
          barrierSeq: 1,
          runtimeVersion: 4,
          events: [
            { ...event(0, "message.updated", {
              messageId: "msg_input",
              role: "user",
              text: "验证快照恢复",
              message: {
                id: "msg_input",
                role: "user",
                text: "验证快照恢复"
              }
            }), eventId: "evt_snapshot_0" },
            { ...event(0, "message.part.updated", {
              messageId: "msg_runtime",
              partId: "part_text",
              part: {
                id: "part_text",
                messageID: "msg_runtime",
                type: "text",
                text: "Redis 物化快照中的最终回答"
              }
            }), eventId: "evt_snapshot_1" }
          ]
        }
      })
    ]
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("验证快照恢复");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.getByText("Redis 物化快照中的最终回答")).toBeVisible();
  await expect(page.getByText("即将被快照替换的旧增量")).toHaveCount(0);
  await expect(page.getByText("验证快照恢复")).toBeVisible();
});

test("late session creation cannot replace a history switch", async ({ page }) => {
  let releaseSessionRequest!: () => void;
  const sessionRequestGate = new Promise<void>((resolve) => {
    releaseSessionRequest = resolve;
  });
  const sessionRequests: Array<Record<string, unknown>> = [];
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    sessionRequestGate,
    sessionRequests,
    runRequests,
    runContextRequests,
    sessionMessageRequests,
    sessions: [{
      sessionId: "ses_history_target",
      workspaceId: "wrk_1234567890abcdef",
      title: "目标历史会话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T01:00:00Z",
      updatedAt: "2026-07-10T01:01:00Z"
    }],
    sessionMessagesBySessionId: {
      ses_history_target: [{
        messageId: "msg_history_target",
        sessionId: "ses_history_target",
        role: "ASSISTANT",
        content: "目标历史正文",
        createdAt: "2026-07-10T01:01:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("等待创建会话");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => sessionRequests.length).toBe(1);

  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "目标历史会话").click();
  await expect(page.getByText("目标历史正文")).toBeVisible();
  releaseSessionRequest();
  await page.waitForTimeout(200);

  expect(runRequests).toEqual([]);
  expect(runContextRequests).toEqual(["ses_history_target"]);
  expect(sessionMessageRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_history_target/messages?page=1&size=100&refresh=false"
  );
  await expect(page.getByText("目标历史正文")).toBeVisible();
});

test("history drawer pins and unpins sessions through the existing session update API", async ({ page }) => {
  const sessionUpdateRequests: Array<{ sessionId: string; payload: Record<string, unknown> }> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    sessionUpdateRequests,
    sessionMessagesBySessionId: {
      ses_normal_latest: [{
        messageId: "msg_normal_latest",
        sessionId: "ses_normal_latest",
        role: "ASSISTANT",
        content: "最新普通会话正文",
        createdAt: "2026-07-10T12:00:00Z"
      }],
      ses_pin_target: [{
        messageId: "msg_pin_target",
        sessionId: "ses_pin_target",
        role: "ASSISTANT",
        content: "待置顶会话正文",
        createdAt: "2026-07-10T11:00:00Z"
      }]
    },
    sessions: [
      {
        sessionId: "ses_normal_latest",
        workspaceId: "wrk_1234567890abcdef",
        title: "最新普通会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T08:00:00Z",
        updatedAt: "2026-07-10T12:00:00Z"
      },
      {
        sessionId: "ses_pin_target",
        workspaceId: "wrk_1234567890abcdef",
        title: "待置顶会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T08:00:00Z",
        updatedAt: "2026-07-10T11:00:00Z"
      },
      {
        sessionId: "ses_pinned_old",
        workspaceId: "wrk_1234567890abcdef",
        title: "原置顶会话",
        status: "ACTIVE",
        pinned: true,
        createdAt: "2026-07-10T07:00:00Z",
        updatedAt: "2026-07-10T08:00:00Z"
      }
    ]
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.getByRole("button", { name: "会话列表" }).click();
  const titles = page.locator(".figma-chat-history-card-title");
  await expect(titles).toHaveText(["原置顶会话", "最新普通会话", "待置顶会话"]);

  await page.getByRole("button", { name: "置顶对话：待置顶会话" }).click();
  await expect.poll(() => sessionUpdateRequests).toEqual([
    { sessionId: "ses_pin_target", payload: { pinned: true } }
  ]);
  await expect(page.getByRole("button", { name: "取消置顶对话：待置顶会话" })).toBeVisible();
  await expect(titles).toHaveText(["待置顶会话", "原置顶会话", "最新普通会话"]);

  await historySessionButton(page, "最新普通会话").click();
  await expect(historySessionButton(page, "最新普通会话")).toHaveAttribute("aria-current", "true");
  await expect(historySessionButton(page, "待置顶会话")).not.toHaveAttribute("aria-current", "true");
  await expect(page.getByText("最新普通会话正文")).toBeVisible();
  await expect(page.getByText("待置顶会话正文")).toHaveCount(0);

  await page.getByRole("button", { name: "取消置顶对话：待置顶会话" }).click();
  await expect.poll(() => sessionUpdateRequests).toEqual([
    { sessionId: "ses_pin_target", payload: { pinned: true } },
    { sessionId: "ses_pin_target", payload: { pinned: false } }
  ]);
  await expect(page.getByRole("button", { name: "置顶对话：待置顶会话" })).toBeVisible();
  await expect(titles).toHaveText(["原置顶会话", "最新普通会话", "待置顶会话"]);
  await expect(historySessionButton(page, "最新普通会话")).toHaveAttribute("aria-current", "true");
  await expect(page.getByText("最新普通会话正文")).toBeVisible();

  await historySessionButton(page, "待置顶会话").click();
  await expect(historySessionButton(page, "待置顶会话")).toHaveAttribute("aria-current", "true");
  await expect(page.getByText("待置顶会话正文")).toBeVisible();
  await expect(page.getByText("最新普通会话正文")).toHaveCount(0);
});

test("agent picker updates the run agent", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const agentRequests: string[] = [];
  await mockBackendApi(page, {
    runRequests,
    agentRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    agents: [
      { id: "build", name: "Build", mode: "primary", description: "默认构建" },
      { id: "plan", name: "Plan", mode: "all", description: "可作为主 Agent" },
      { id: "review", name: "Review", mode: "subagent", description: "仅用于 @ 候选" }
    ]
  });

  await gotoWorkbench(page);
  await expect.poll(() => agentRequests.length).toBeGreaterThanOrEqual(1);
  await expect(page.getByRole("button", { name: "切换 Agent" })).toBeEnabled();

  await page.getByRole("button", { name: "切换 Agent" }).click();
  const agentDialog = page.getByRole("dialog", { name: "Agent 选择" });
  await expect(agentDialog).toBeVisible();
  await expect(agentDialog).toContainText("Build");
  await expect(agentDialog).toContainText("Plan");
  await expect(agentDialog).not.toContainText("Review");
  await agentDialog.getByRole("button", { name: /Plan/ }).click();
  await expect(page.getByRole("button", { name: "切换 Agent" })).toContainText("Plan");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use selected agent");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use selected agent",
    agent: "plan"
  });
});

test("agent picker retries after the catalog request fails", async ({ page }) => {
  const agentRequests: string[] = [];
  await mockBackendApi(page, {
    agentRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    agentResponses: [
      { status: 504, code: "OPENCODE_TIMEOUT", message: "Agent 目录加载超时" },
      { status: 504, code: "OPENCODE_TIMEOUT", message: "Agent 目录加载超时" },
      [{ id: "build", name: "Build", mode: "primary", description: "默认构建" }]
    ]
  });

  await gotoWorkbench(page);
  await expect.poll(() => agentRequests.length).toBe(1);

  await page.getByRole("button", { name: "切换 Agent" }).click();
  const agentDialog = page.getByRole("dialog", { name: "Agent 选择" });
  await expect(agentDialog).toBeVisible();
  await expect(agentDialog).toContainText("Agent 目录加载超时");

  await agentDialog.getByRole("button", { name: "重新加载 Agent" }).click();

  await expect.poll(() => agentRequests.length).toBeGreaterThanOrEqual(2);
  await expect(agentDialog).toContainText("Build");
});

test("agent catalog uses the current workspace when an older request finishes later", async ({ page }) => {
  let releaseGcmsAgents!: () => void;
  const gcmsAgentsGate = new Promise<void>((resolve) => {
    releaseGcmsAgents = resolve;
  });
  const agentRequests: string[] = [];
  await mockBackendApi(page, {
    agentRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_gcms",
        appId: "app_gcms",
        versionId: "awv_gcms",
        applicationWorkspaceId: "awp_gcms"
      },
      app_coss: {
        ...workspace(),
        workspaceId: "wrk_app_coss",
        appId: "app_coss",
        versionId: "awv_coss",
        applicationWorkspaceId: "awp_coss"
      }
    },
    personalWorkspaces: {
      awv_gcms: [
        {
          ...defaultPersonalWorkspace("awv_gcms"),
          applicationWorkspaceId: "awp_gcms",
          runtimeWorkspace: {
            ...workspace(),
            workspaceId: "wrk_gcms_default",
            name: "gcms-default",
            appId: "app_gcms",
            versionId: "awv_gcms",
            applicationWorkspaceId: "awp_gcms"
          }
        }
      ],
      awv_coss: [
        {
          ...defaultPersonalWorkspace("awv_coss"),
          appId: "app_coss",
          applicationWorkspaceId: "awp_coss",
          runtimeWorkspace: {
            ...workspace(),
            workspaceId: "wrk_coss_default",
            name: "coss-default",
            appId: "app_coss",
            versionId: "awv_coss",
            applicationWorkspaceId: "awp_coss"
          }
        }
      ]
    },
    agentGatesByWorkspace: {
      wrk_gcms_default: gcmsAgentsGate
    },
    agentsByWorkspace: {
      wrk_gcms_default: [{ id: "gcms", name: "GCMS Agent", mode: "primary" }],
      wrk_coss_default: [{ id: "coss", name: "COSS Agent", mode: "primary" }]
    }
  });

  await gotoWorkbench(page);
  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();

  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();
  await expect.poll(() => agentRequests.some((request) => request.includes("workspaceId=wrk_coss_default"))).toBe(true);
  await page.getByRole("button", { name: "切换 Agent" }).click();
  const agentDialog = page.getByRole("dialog", { name: "Agent 选择" });
  await expect(agentDialog).toContainText("COSS Agent");

  releaseGcmsAgents();

  await page.waitForTimeout(100);
  await expect(agentDialog).toContainText("COSS Agent");
  await expect(agentDialog).not.toContainText("GCMS Agent");
});

test("model picker keeps the selected model after page reload", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    runRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);

  await page.getByRole("button", { name: "切换模型" }).click();
  await page.getByPlaceholder("搜索模型").fill("north");
  await page.getByRole("dialog", { name: "模型选择" }).getByRole("button", { name: /North Mini Code Free/ }).click();
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("North Mini Code Free");

  await page.reload({ waitUntil: "domcontentloaded" });
  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("North Mini Code Free");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use persisted model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use persisted model",
    model: "opencode-zen/north-mini-code"
  });
});

test("context usage keeps payload.info remote root usage separate from the platform session", async ({ page, isMobile }) => {
  test.skip(isMobile, "当前项目没有移动端产品内容，桌面侧上下文抽屉单独覆盖。");
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    models: [{
      id: "deepseek-chat",
      providerID: "deepseek",
      name: "DeepSeek Chat",
      defaultModel: true,
      limit: { context: 1_000_000, output: 32_000 }
    }],
    providers: [{ id: "deepseek", name: "DeepSeek", status: "ready" }],
    runEvents: [
      event(1, "message.updated", {
        rootSessionId: "ses_0719c797fffeuNz55LEr5sU5bH",
        info: {
          id: "msg_context_live",
          sessionID: "ses_0719c797fffeuNz55LEr5sU5bH",
          role: "assistant",
          providerID: "deepseek",
          modelID: "deepseek-chat",
          tokens: {
            input: 9_518,
            output: 282,
            reasoning: 729,
            cache: { read: 43_008, write: 0 }
          }
        }
      }),
      event(2, "message.part.updated", {
        sessionID: "ses_0719c797fffeuNz55LEr5sU5bH",
        rootSessionId: "ses_0719c797fffeuNz55LEr5sU5bH",
        messageID: "msg_context_live",
        part: { id: "part_context_live", messageID: "msg_context_live", type: "text", text: "上下文统计完成" }
      }),
      event(3, "run.succeeded", {})
    ]
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("统计当前上下文");
  await page.getByRole("button", { name: "发送" }).click();

  const contextButton = page.getByRole("button", { name: "查看会话上下文" });
  await expect(contextButton).toBeVisible();
  await contextButton.focus();
  await page.keyboard.press("Shift+Tab");
  await page.keyboard.press("Tab");
  await expect(contextButton).toBeFocused();
  await expect(contextButton).toHaveCSS("outline-style", "solid");
  await expect(contextButton).toHaveCSS("outline-width", "2px");
  await expect(contextButton).toHaveCSS("outline-color", "rgb(164, 13, 188)");
  await contextButton.hover();
  const tooltip = page.locator(".session-context-tooltip");
  await expect(tooltip).toContainText("使用率5%");
  await expect(tooltip).toContainText("总上下文1,000,000");
  await expect(tooltip).toContainText("已使用53,537");
  await expect(tooltip).not.toContainText("费用");
  const progressRing = contextButton.locator(".session-context-ring-value");
  await expect(progressRing).toHaveCSS("stroke", "rgb(164, 13, 188)");
  expect(Number.parseFloat(await progressRing.getAttribute("stroke-dashoffset") ?? "NaN")).toBeCloseTo(Math.PI * 6 * 1.9, 5);

  await contextButton.click();
  const detail = page.getByRole("dialog", { name: "会话上下文" });
  await expect(detail).toContainText("E2E Session");
  await expect(detail).toContainText("DeepSeek");
  await expect(detail).toContainText("DeepSeek Chat");
  await expect(detail).toContainText("2 条");
  await expect(detail).toContainText("53,537");
  await expect(detail).toContainText("9,518");
  await expect(detail).toContainText("282");
  await expect(detail.getByTestId("context-breakdown")).toBeVisible();
  await detail.evaluate(async (element) => {
    await Promise.all(element.getAnimations().map((animation) => animation.finished));
  });

  const rootBox = await page.locator(".figma-chat-root").boundingBox();
  const drawerBox = await detail.boundingBox();
  expect(rootBox).not.toBeNull();
  expect(drawerBox).not.toBeNull();
  expect(Math.abs(drawerBox!.x + drawerBox!.width - rootBox!.x)).toBeLessThanOrEqual(1);
  expect(await detail.evaluate((element) => element.closest(".figma-chat-root") === null)).toBe(true);

  await contextButton.click();
  await expect(detail).toBeHidden();
  await expect(contextButton).toBeFocused();

  const resizeHandle = page.getByRole("separator", { name: "拖拽调整对话窗口宽度" });
  const resizeBox = await resizeHandle.boundingBox();
  expect(resizeBox).not.toBeNull();
  await page.mouse.move(resizeBox!.x + resizeBox!.width / 2, resizeBox!.y + 80);
  await page.mouse.down();
  await page.mouse.move(resizeBox!.x + 280, resizeBox!.y + 80);
  await page.mouse.up();
  await expect(page.locator(".figma-chat-panel-wrapper")).toHaveCSS("width", "240px");

  await contextButton.click();
  await detail.evaluate(async (element) => {
    await Promise.all(element.getAnimations().map((animation) => animation.finished));
  });
  const resizedRootBox = await page.locator(".figma-chat-root").boundingBox();
  const resizedDrawerBox = await detail.boundingBox();
  expect(resizedRootBox).not.toBeNull();
  expect(resizedDrawerBox).not.toBeNull();
  expect(Math.abs(resizedDrawerBox!.x + resizedDrawerBox!.width - resizedRootBox!.x)).toBeLessThanOrEqual(1);
});

test("workbench clears stale persisted model and sends catalog default", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    runRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    models: [
      {
        id: "DeepSeek-V4-Flash-W8A8",
        providerId: "enterprise-openai",
        name: "DeepSeek-V4-Flash-W8A8",
        defaultModel: true
      },
      { id: "Qwen3.6-27B", providerId: "enterprise-openai", name: "Qwen3.6-27B" }
    ],
    providers: [{ id: "enterprise-openai", providerId: "enterprise-openai", name: "Enterprise OpenAI", status: "ready" }]
  });
  await page.addInitScript(() => {
    localStorage.setItem("ta_selected_provider", "opencode-zen");
    localStorage.setItem("ta_selected_model", "opencode-zen/north-mini-code");
  });

  await gotoWorkbench(page);

  await expect(page.getByRole("button", { name: "切换模型" })).toContainText("DeepSeek-V4-Flash-W8A8");
  await expect.poll(() => page.evaluate(() => localStorage.getItem("ta_selected_model"))).toBe("enterprise-openai/DeepSeek-V4-Flash-W8A8");

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("use catalog default model");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    prompt: "use catalog default model",
    model: "enterprise-openai/DeepSeek-V4-Flash-W8A8"
  });
});

test("the first sent message becomes the new session title", async ({ page }) => {
  const sessionRequests: Array<Record<string, unknown>> = [];
  const processStatusRequests: string[] = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), sessionRequests, processStatusRequests });

  await gotoWorkbench(page);
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(1);
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.getByRole("button", { name: "发送" });
  await composer.fill("请生成登录测试案例");
  await expect(sendButton).toBeEnabled();
  await sendButton.click();

  await expect.poll(() => sessionRequests.length).toBe(1);
  expect(sessionRequests[0]).toEqual({
    workspaceId: "wrk_personal_default",
    title: "请生成登录测试案例"
  });
});

test("pet side-question streams progress, survives outside clicks, and calibrates replayed deltas", async ({ page }) => {
  const sideQuestionRequests: Array<Record<string, unknown>> = [];
  await installPetSideQuestionRunEventStream(page, {
    run_side_question_1: [
      streamEvent("evt_side_started", "run.started", {}, 20),
      streamEvent("evt_side_ready", "side_question.started", { sessionId: "ses_1" }, 30),
      streamEvent("evt_side_progress", "side_question.progress", { stage: "preparing_context" }, 80, true),
      // 模拟 Last-Event-ID 续传后 durable progress 与 transient delta 被重投。
      streamEvent("evt_side_progress", "side_question.progress", { stage: "preparing_context" }, 160),
      streamEvent("evt_side_delta_1", "side_question.delta", { delta: "增量片段" }, 2500),
      streamEvent("evt_side_delta_1", "side_question.delta", { delta: "增量片段" }, 2600),
      streamEvent("evt_side_delta_2", "side_question.delta", { delta: "（可能缺帧）" }, 2700),
      streamEvent("evt_side_terminal", "run.succeeded", { answer: "最终完整答案" }, 3200),
      streamEvent("evt_side_terminal", "run.succeeded", { answer: "最终完整答案" }, 3300)
    ]
  });
  await mockBackendApi(page, {
    sideQuestionRequests,
    sideQuestionRunIds: ["run_side_question_1"],
    recentWorkspaces: {
      app_gcms: { ...workspace(), appId: "app_gcms", versionId: "awv_20260715", applicationWorkspaceId: "awp_1" }
    },
    personalWorkspaces: { awv_20260715: [defaultPersonalWorkspace("awv_20260715")] },
    sessions: [session()],
    sessionMessages: [petContextMessage()]
  });
  await gotoWorkbench(page);

  await selectPetContextSession(page);
  await openPetSideQuestion(page);
  await page.getByTestId("robot-side-question-input").fill("这个对话干嘛了？");
  await page.getByTestId("robot-side-question-submit").click();

  await expect(page.getByTestId("robot-side-question-progress")).toHaveText("正在读取当前上下文");
  await page.locator(".figma-panel-center").dispatchEvent("click");
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
  await expect(page.getByTestId("robot-side-question-answer")).toContainText("增量片段");
  await expect(page.getByTestId("robot-side-question-answer")).toHaveText("最终完整答案");
  await page.waitForTimeout(120);
  await expect(page.getByTestId("robot-side-question-answer")).toHaveText("最终完整答案");
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __petSideQuestionReconnects?: Array<{ runId: string; lastEventId: string }> }
  ).__petSideQuestionReconnects)).toEqual([{ runId: "run_side_question_1", lastEventId: "evt_side_progress" }]);
  expect(sideQuestionRequests).toEqual([{
    question: "这个对话干嘛了？",
    messageId: "msg_remote_pet_context",
    model: "anthropic/sonnet"
  }]);
});

test("pet side-question keeps a failure editable and starts a fresh run on retry", async ({ page }) => {
  const sideQuestionRequests: Array<Record<string, unknown>> = [];
  await installPetSideQuestionRunEventStream(page, {
    run_side_question_failed: [
      streamEvent("evt_failed_progress", "side_question.progress", { stage: "reading" }, 30),
      streamEvent("evt_failed_terminal", "run.failed", { message: "旁路问答暂时失败" }, 80)
    ],
    run_side_question_retry: [
      streamEvent("evt_retry_progress", "side_question.progress", { stage: "composing" }, 30),
      streamEvent("evt_retry_terminal", "run.succeeded", { answer: "重试后的答案" }, 100)
    ]
  });
  await mockBackendApi(page, {
    sideQuestionRequests,
    sideQuestionRunIds: ["run_side_question_failed", "run_side_question_retry"],
    recentWorkspaces: {
      app_gcms: { ...workspace(), appId: "app_gcms", versionId: "awv_20260715", applicationWorkspaceId: "awp_1" }
    },
    personalWorkspaces: { awv_20260715: [defaultPersonalWorkspace("awv_20260715")] },
    sessions: [session()],
    sessionMessages: [petContextMessage()]
  });
  await gotoWorkbench(page);

  await selectPetContextSession(page);
  await openPetSideQuestion(page);
  const input = page.getByTestId("robot-side-question-input");
  await input.fill("第一次问题");
  await page.getByTestId("robot-side-question-submit").click();
  await expect(page.locator(".figma-robot-side-question-error")).toHaveText("旁路问答暂时失败");
  await expect(input).toBeEditable();

  await input.fill("修改后重试");
  await page.getByTestId("robot-side-question-submit").click();
  await expect(page.getByTestId("robot-side-question-answer")).toHaveText("重试后的答案");
  expect(sideQuestionRequests).toEqual([
    { question: "第一次问题", messageId: "msg_remote_pet_context", model: "anthropic/sonnet" },
    { question: "修改后重试", messageId: "msg_remote_pet_context", model: "anthropic/sonnet" }
  ]);
});

test("direct run projects remote question and permission to the platform session and replies", async ({ page }) => {
  const permissionReplies: Array<Record<string, unknown>> = [];
  const questionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    permissionReplies,
    questionReplies,
    runEvents: [
      event(1, "permission.asked", {
        requestId: "perm_1",
        sessionId: "ses_remote_root",
        title: "执行命令",
        description: "是否允许只读检查？"
      }),
      event(2, "question.asked", {
        requestId: "ques_1",
        sessionId: "ses_remote_root",
        questions: [{ question: "直接对话：请选择 A 或 B", options: [{ label: "A" }, { label: "B" }] }]
      })
    ]
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("直接验证交互映射");
  await page.getByRole("button", { name: "发送" }).click();

  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("执行命令");
  await expect(dock).toContainText("直接对话：请选择 A 或 B");
  await page.getByRole("button", { name: "允许一次" }).click();
  await dock.getByRole("button", { name: "A", exact: true }).click();
  await dock.getByRole("button", { name: "提交", exact: true }).click();

  await expect.poll(() => permissionReplies).toEqual([{ decision: "once" }]);
  await expect.poll(() => questionReplies).toEqual([{ answers: [["A"]] }]);
});

test("conflicting terminal replay keeps one authenticated SSE and one legacy feedback recovery chain", async ({ page }) => {
  const sessionMessageRequests: string[] = [];
  const runFeedbackQueryRequests: Array<Record<string, unknown>> = [];
  await installAuthenticatedRunEventFetchStream(page, {
    run_1: [
      {
        delayMs: 10,
        events: [
          {
            seq: 1,
            type: "message.updated",
            payload: {
              messageId: "msg_remote_live",
              role: "assistant",
              message: { id: "msg_remote_live", role: "assistant" }
            }
          },
          {
            seq: 2,
            type: "message.part.updated",
            payload: {
              messageId: "msg_remote_live",
              partId: "part_remote_live",
              part: {
                id: "part_remote_live",
                messageID: "msg_remote_live",
                type: "text",
                text: "根终态最终回答"
              }
            }
          },
          {
            seq: 3,
            type: "run.failed",
            payload: { message: "候选网关失败" }
          },
          {
            seq: 4,
            type: "run.succeeded",
            payload: { platformSessionTitlePending: true }
          }
        ]
      },
      {
        delayMs: 1_600,
        events: [{
          seq: 5,
          type: "session.updated",
          payload: {
            platformSessionTitleSynchronized: true,
            platformSessionTitle: "登录功能测试设计",
            isChildSession: false
          }
        }]
      }
    ]
  });
  await mockBackendApi(page, {
    sessionMessageRequests,
    runFeedbackQueryRequests,
    sessionMessages: [{
      messageId: "msg_11111111111111111111111111111111",
      remoteMessageId: "msg_persisted_other",
      sessionId: "ses_1",
      role: "ASSISTANT",
      content: "已持久化但不是当前远端消息",
      createdAt: "2026-07-17T08:00:00Z",
      runId: "run_1"
    }],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });
  await gotoWorkbench(page);

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("请设计登录功能测试");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("任务完成")).toBeVisible();
  await page.waitForTimeout(1_100);
  expect(await page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.length)).toBe(1);
  await expect.poll(() => sessionMessageRequests.length).toBe(3);
  await expect.poll(() => runFeedbackQueryRequests.length).toBe(3);
  expect(runFeedbackQueryRequests).toEqual(Array.from({ length: 3 }, () => ({ runIds: ["run_1"] })));
  expect(await page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ authorization: string | null }> }
  ).__titleWatchRunStreams?.[0]?.authorization)).toBe("Bearer test-token");
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.[0]?.closed)).toBe(false);
  await expect(page.locator(".figma-chat-title")).toHaveText("登录功能测试设计");
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.[0]?.closed)).toBe(true);
  expect(sessionMessageRequests).toHaveLength(3);
  expect(runFeedbackQueryRequests).toHaveLength(3);
});

test("a new run replaces one title-pending fetch SSE and ignores the old stream's late title", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await installAuthenticatedRunEventFetchStream(page, {
    run_1: [
      {
        delayMs: 10,
        events: [{
          seq: 1,
          type: "run.succeeded",
          payload: { platformSessionTitlePending: true }
        }]
      },
      {
        delayMs: 600,
        events: [{
          seq: 2,
          type: "session.updated",
          payload: {
            platformSessionTitleSynchronized: true,
            platformSessionTitle: "旧 Run 晚到标题",
            isChildSession: false
          }
        }]
      }
    ],
    run_2: []
  });
  await mockBackendApi(page, {
    runRequests,
    runIds: ["run_1", "run_2"],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });
  await gotoWorkbench(page);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("第一轮等待原生标题");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("任务完成")).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }).__titleWatchRunStreams?.[0]?.closed)).toBe(false);

  await composer.fill("第二轮开始后关闭旧标题监听");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(2);
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.length)).toBe(2);
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ closed: boolean }> }
  ).__titleWatchRunStreams?.[0]?.closed)).toBe(true);
  await page.waitForTimeout(700);
  await expect(page.locator(".figma-chat-title")).not.toHaveText("旧 Run 晚到标题");
  expect(await page.evaluate(() => (
    window as Window & { __titleWatchRunStreams?: Array<{ runId: string; authorization: string | null; closed: boolean }> }
  ).__titleWatchRunStreams)).toEqual([
    { runId: "run_1", authorization: "Bearer test-token", closed: true },
    { runId: "run_2", authorization: "Bearer test-token", closed: false }
  ]);
});

test("a superseded title-pending run cannot restore its todos into the next turn", async ({ page }) => {
  let releaseSecondRun!: () => void;
  const secondRunGate = new Promise<void>((resolve) => {
    releaseSecondRun = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
    type StreamProbe = {
      runId: string;
      closed: boolean;
      emit: (type: string, seq: number, payload: Record<string, unknown>) => void;
      fail: () => void;
    };
    const probes: StreamProbe[] = [];
    const seededRunIds = new Set<string>();
    (window as Window & { __todoOwnershipRunStreams?: StreamProbe[] }).__todoOwnershipRunStreams = probes;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init) => {
      const requestUrl = new URL(
        typeof input === "string" ? input : input instanceof Request ? input.url : input.toString(),
        window.location.origin
      );
      const runId = decodeURIComponent(requestUrl.pathname).match(/\/runs\/([^/]+)\/events$/)?.[1];
      if (!runId) {
        return nativeFetch(input, init);
      }
      const encoder = new TextEncoder();
      let controller: ReadableStreamDefaultController<Uint8Array> | undefined;
      let closed = false;
      const probe: StreamProbe = {
        runId,
        closed: false,
        emit: (type, seq, payload) => {
          if (closed || !controller) return;
          controller.enqueue(encoder.encode(
            `id: ${seq}\nevent: ${type}\ndata: ${JSON.stringify({
              eventId: `evt_todo_owner_${runId}_${seq}_${type}`,
              runId,
              seq,
              type,
              traceId: "trace_e2e",
              occurredAt: "2026-07-15T09:00:00Z",
              payload
            })}\n\n`
          ));
        },
        fail: () => {
          if (closed || !controller) return;
          closed = true;
          probe.closed = true;
          controller.error(new Error("superseded stream failed"));
        }
      };
      probes.push(probe);
      const body = new ReadableStream<Uint8Array>({
        start(streamController) {
          controller = streamController;
          if (runId === "run_1" && !seededRunIds.has(runId)) {
            seededRunIds.add(runId);
            window.setTimeout(() => probe.emit("todo.updated", 1, {
              todos: Array.from({ length: 4 }, (_, index) => ({
                id: `todo_first_${index}`,
                content: `第一轮任务 ${index + 1}`,
                status: "completed"
              }))
            }), 10);
            window.setTimeout(() => probe.emit("run.succeeded", 2, {
              platformSessionTitlePending: true
            }), 20);
          } else if (runId === "run_2" && !seededRunIds.has(runId)) {
            seededRunIds.add(runId);
            window.setTimeout(() => probe.emit("todo.updated", 1, {
              todos: Array.from({ length: 9 }, (_, index) => ({
                id: `todo_second_${index}`,
                content: `第二轮任务 ${index + 1}`,
                status: "pending"
              }))
            }), 10);
          }
        },
        cancel() {
          closed = true;
          probe.closed = true;
        }
      });
      init?.signal?.addEventListener("abort", () => {
        closed = true;
        probe.closed = true;
        controller?.close();
      }, { once: true });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
  });
  await mockBackendApi(page, {
    runRequests,
    runIds: ["run_1", "run_2"],
    runRequestGates: [Promise.resolve(), secondRunGate],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });
  await gotoWorkbench(page);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("第一轮生成 4 个待办");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText("任务完成")).toBeVisible();
  await page.getByRole("button", { name: "展开已完成工作状态" }).click();
  await expect(page.getByText("共 4")).toBeVisible();

  await composer.fill("第二轮生成 9 个待办");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(2);
  await expect(page.getByTestId("oc-work-status-dock").getByTestId("oc-todo-panel")).toHaveCount(0);

  await page.evaluate(() => {
    const probe = (window as Window & {
      __todoOwnershipRunStreams?: Array<{ runId: string; closed: boolean; fail: () => void }>;
    }).__todoOwnershipRunStreams?.find((item) => item.runId === "run_1" && !item.closed);
    probe?.fail();
  });
  await expect(page.getByText("RunEvent SSE 连接异常", { exact: true })).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => (
    (window as Window & {
      __todoOwnershipRunStreams?: Array<{ runId: string; closed: boolean }>;
    }).__todoOwnershipRunStreams?.filter((item) => item.runId === "run_1" && !item.closed).length ?? 0
  ))).toBeGreaterThan(0);

  await page.evaluate(() => {
    const probe = (window as Window & {
      __todoOwnershipRunStreams?: Array<{
        runId: string;
        closed: boolean;
        emit: (type: string, seq: number, payload: Record<string, unknown>) => void;
        fail: () => void;
      }>;
    }).__todoOwnershipRunStreams?.filter((item) => item.runId === "run_1" && !item.closed).at(-1);
    probe?.emit("todo.updated", 3, { todos: [{ content: "旧 Run todo.updated 回灌", status: "completed" }] });
    probe?.emit("message.part.updated", 4, {
      messageID: "msg_old_todowrite",
      part: {
        id: "part_old_todowrite",
        messageID: "msg_old_todowrite",
        type: "tool",
        tool: "todowrite",
        state: { status: "completed", input: { todos: [{ content: "旧 Run todowrite 回灌", status: "completed" }] } }
      }
    });
    probe?.emit("run.snapshot.reset", 5, {
      snapshot: {
        events: [{
          eventId: "evt_old_snapshot_todo",
          runId: "run_1",
          seq: 1,
          type: "todo.updated",
          traceId: "trace_e2e",
          occurredAt: "2026-07-15T09:00:00Z",
          payload: { todos: [{ content: "旧 Run snapshot 回灌", status: "completed" }] }
        }]
      }
    });
    probe?.emit("session.updated", 6, {
      platformSessionTitleSynchronized: true,
      platformSessionTitle: "旧 Run 标题仍同步",
      isChildSession: false
    });
  });

  await expect(page.locator(".figma-chat-title")).toHaveText("旧 Run 标题仍同步");
  await expect(page.getByTestId("oc-work-status-dock").getByTestId("oc-todo-panel")).toHaveCount(0);
  await page.getByRole("button", { name: "展开历史工作状态" }).click();
  await expect(page.getByText("共 4")).toBeVisible();
  await expect(page.getByText(/旧 Run .*回灌/)).toHaveCount(0);

  releaseSecondRun();
  await expect(page.getByTestId("oc-work-status-dock").getByText("共 9")).toBeVisible();
  await expect(page.getByTestId("oc-work-status-dock").getByText("共 4")).toHaveCount(0);
});

test("retrying a failed chat run resends the previous remote user turn", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const runResendRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await mockBackendApi(page, {
    runRequests,
    runResendRequests,
    runIds: ["run_1"],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    runEvents: [
      event(1, "message.updated", {
        message: { id: "msg_remote_retry_source", role: "user", content: "重试这条测试任务" }
      }),
      event(2, "run.failed", {
        error: { name: "ConnectionError", message: "Streaming response failed" }
      })
    ],
    runEventsByRunId: {
      run_resend_replacement: []
    }
  });

  await gotoWorkbench(page);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("重试这条测试任务");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.locator(".figma-chat-retry-card-text")).toContainText("Streaming response failed");

  await page.locator(".figma-chat-retry-card-btn").click();

  const resendComposer = page.getByPlaceholder("修改上一条消息后发送");
  await expect(page.getByTestId("resend-edit-banner")).toBeVisible();
  await expect(resendComposer).toHaveValue("重试这条测试任务");
  await resendComposer.fill("修改后重试这条测试任务");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runResendRequests.length).toBe(1);
  expect(runResendRequests[0]).toMatchObject({
    expectedRemoteMessageId: "msg_remote_retry_source",
    expectedRunId: "run_1",
    editedPrompt: "修改后重试这条测试任务"
  });
  expect(runRequests).toHaveLength(1);
  await expect(page.getByTestId("oc-user-message")).toHaveCount(1);
  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
});

test("manual retry refuses to replace a still-running run after a transient session interruption", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const cancelRunRequests: string[] = [];
  const runResendRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    cancelRunRequests,
    runResendRequests,
    runIds: ["run_1"],
    runEventsByRunId: {
      run_1: [event(1, "session.status", {
        status: { type: "error", message: "conversation interrupted" }
      })]
    }
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("恢复异常对话");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.locator(".figma-chat-retry-card")).toBeVisible();

  await page.locator(".figma-chat-retry-card-btn").click();

  expect(cancelRunRequests).toEqual([]);
  expect(runResendRequests).toEqual([]);
  expect(runRequests).toHaveLength(1);
  await expect(page.getByText("无法撤销重发")).toBeVisible();
  await expect(page.locator(".figma-chat-retry-card")).toBeVisible();
});

test("retrying a reopened failed chat resends the persisted remote user turn", async ({ page }) => {
  const runResendRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runResendRequests,
    sessions: [{
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      title: "异常中断的对话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-05T10:00:00Z",
      updatedAt: "2026-07-05T10:01:00Z"
    }],
    sessionMessages: [{
      messageId: "msg_user_failed",
      remoteMessageId: "msg_remote_user_failed",
      sessionId: "ses_history",
      role: "USER",
      content: "重新检查登录流程",
      createdAt: "2026-07-05T10:00:00Z",
      runId: "run_history",
      parts: [
        { type: "text", text: "重新检查登录流程" },
        { type: "file", path: "docs/login.md", name: "login.md", mimeType: "text/markdown" }
      ]
    }],
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      status: "FAILED",
      createdAt: "2026-07-05T10:00:00Z",
      updatedAt: "2026-07-05T10:01:00Z"
    },
    runEventsByRunId: { run_resend_replacement: [] }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "异常中断的对话").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click({ force: true });
  await expect(page.locator(".figma-chat-retry-card")).toBeVisible();

  await page.locator(".figma-chat-retry-card-btn").click();

  const resendComposer = page.getByPlaceholder("修改上一条消息后发送");
  await expect(resendComposer).toHaveValue("重新检查登录流程");
  await resendComposer.fill("重新检查登录和退出流程");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runResendRequests.length).toBe(1);
  expect(runResendRequests[0]).toMatchObject({
    expectedRemoteMessageId: "msg_remote_user_failed",
    expectedRunId: "run_history",
    editedPrompt: "重新检查登录和退出流程"
  });
  await expect(page.getByTestId("oc-user-message")).toHaveCount(1);
  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
});

test("new run success clears stale RunEvent SSE connection feedback", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    const nativeFetch = window.fetch.bind(window);
    const attempts: Record<string, number> = {};
    (window as Window & { __runEventFetchAttempts?: Record<string, number> }).__runEventFetchAttempts = attempts;
    window.fetch = async (input, init) => {
      const request = new Request(input, init);
      const requestUrl = new URL(request.url, window.location.origin);
      const runId = decodeURIComponent(requestUrl.pathname)
        .match(/^\/api\/internal\/agent\/opencode\/runs\/([^/]+)\/events$/)?.[1];
      if (!runId) {
        return nativeFetch(input, init);
      }
      const attempt = (attempts[runId] ?? 0) + 1;
      attempts[runId] = attempt;
      const encoder = new TextEncoder();
      let timer: number | undefined;
      let closed = false;
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          timer = window.setTimeout(() => {
            if (closed) return;
            if (runId === "run_1" && attempt === 1) {
              closed = true;
              controller.error(new Error("mock authenticated fetch SSE failure"));
              return;
            }
            const type = runId === "run_1" ? "run.failed" : "run.succeeded";
            const payload = runId === "run_1"
              ? { error: { name: "ConnectionError", message: "Streaming response failed" } }
              : {};
            controller.enqueue(encoder.encode(
              `id: evt_mock_${runId}_1\nevent: ${type}\ndata: ${JSON.stringify({
                eventId: `evt_mock_${runId}_1`,
                runId,
                seq: 1,
                type,
                traceId: "trace_e2e",
                occurredAt: "2026-07-17T08:00:00Z",
                payload
              })}\n\n`
            ));
          }, 20);
          request.signal.addEventListener("abort", () => {
            closed = true;
            if (timer !== undefined) window.clearTimeout(timer);
            try {
              controller.error(new DOMException("RunEvent stream aborted", "AbortError"));
            } catch {
              // reader 已结束时无需重复关闭。
            }
          }, { once: true });
        },
        cancel() {
          closed = true;
          if (timer !== undefined) window.clearTimeout(timer);
        }
      });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
  });
  await mockBackendApi(page, {
    runRequests,
    runIds: ["run_1", "run_2"],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("第一轮触发 SSE error");
  await page.getByRole("button", { name: "发送" }).click();
  await expect(page.getByText("RunEvent SSE 连接异常")).toBeVisible();
  await expect(page.locator(".figma-chat-retry-card")).toContainText("Streaming response failed");
  await expect(page.getByText("任务失败")).toBeVisible();

  await composer.fill("第二轮成功");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(2);
  await expect.poll(() => page.evaluate(() => (
    window as Window & { __runEventFetchAttempts?: Record<string, number> }
  ).__runEventFetchAttempts?.run_2)).toBe(1);
  await expect(page.getByText("任务完成")).toBeVisible();
  await expect(page.getByText("RunEvent SSE 连接异常")).toHaveCount(0);
  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
  await expect(page.getByText("Streaming response failed")).toHaveCount(0);
  await expect(page.getByText("任务失败")).toHaveCount(0);
});

test("a live diff refreshes the changed file parent directory before the run finishes", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const gitDiffRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileRequests,
    gitDiffRequests,
    runEvents: [
      event(1, "diff.proposed", {
        files: [{ path: "tests/generated.spec.ts", status: "added", additions: 8, deletions: 0 }]
      })
    ]
  });

  await gotoWorkbench(page);
  await openAgentsPanel(page);
  await page.getByRole("button", { name: /tests/ }).click();
  await expect(page.getByRole("button", { name: /checkout.spec.ts/ })).toBeVisible();
  fileRequests.length = 0;
  gitDiffRequests.length = 0;

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("生成新的测试文件");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByRole("button", { name: "文件修改 1 文件总增减行" })).toBeVisible();
  await expect.poll(() => fileRequests).toContainEqual({
    workspaceId: "wrk_personal_default",
    path: "tests"
  });
  await expect.poll(() => gitDiffRequests.length).toBeGreaterThan(0);
});

test("a live run diff does not hijack an open VCS diff panel", async ({ page }) => {
  await mockBackendApi(page, {
    authRoles: ["SUPER_ADMIN"],
    historyDiffFiles: [
      {
        path: "tests/checkout.spec.ts",
        status: "modified",
        staged: false,
        patch: "@@ -1 +1 @@\n-old\n+new",
        additions: 1,
        deletions: 1
      }
    ],
    runEvents: [
      event(1, "diff.proposed", {
        files: [{ path: "tests/generated.spec.ts", status: "added", additions: 8, deletions: 0 }]
      })
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);
  await openAgentsPanel(page);
  await page.getByRole("button", { name: "变更" }).click();
  await page.locator(".git-file-row").filter({ hasText: "checkout.spec.ts" }).first().click();
  await expect(page.getByText("基线版本（只读）")).toBeVisible();

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("继续生成文件");
  await page.getByRole("button", { name: "发送" }).click();

  await expect(page.getByText("基线版本（只读）")).toHaveCount(0);
  await expect(page.getByText("Run Diff")).toHaveCount(0);
});

test("discarding the last VCS diff closes the stale diff panel", async ({ page }) => {
  const diffFiles = [
    {
      path: "tests/checkout.spec.ts",
      status: "modified",
      staged: false,
      patch: "@@ -1 +1 @@\n-old\n+new",
      additions: 1,
      deletions: 1
    }
  ];
  await mockBackendApi(page, {
    authRoles: ["SUPER_ADMIN"],
    historyDiffFiles: diffFiles,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  });

  await gotoWorkbench(page);
  await openAgentsPanel(page);
  await page.getByRole("button", { name: "变更" }).click();
  const changeRow = page.locator(".git-file-row").filter({ hasText: "checkout.spec.ts" }).first();
  await changeRow.click();
  await expect(page.getByText("基线版本（只读）")).toBeVisible();

  diffFiles.length = 0;
  await changeRow.hover();
  await page.getByTitle("回退文件改动").click();

  await expect(page.getByText("暂无 Diff")).toHaveCount(0);
  await expect(page.getByText("基线版本（只读）")).toHaveCount(0);
});

test("switching history restores assistant documents and the file changes summary", async ({ page }) => {
  const sessionTreeRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  await mockBackendApi(page, {
    sessionTreeRequests,
    sessionMessageRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    sessions: [
      {
        sessionId: "ses_history",
        workspaceId: "wrk_1234567890abcdef",
        title: "请生成登录测试报告",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_user",
        sessionId: "ses_history",
        role: "USER",
        content: "请生成登录测试报告",
        createdAt: "2026-06-28T08:00:00Z",
        runId: "run_history"
      },
      {
        messageId: "msg_assistant",
        sessionId: "ses_history",
        role: "ASSISTANT",
        content: "测试报告已生成",
        createdAt: "2026-06-28T08:01:00Z",
        runId: "run_history",
        parts: [
          {
            id: "part_file",
            messageID: "msg_assistant",
            type: "file",
            name: "登录测试报告.md",
            path: "docs/登录测试报告.md",
            mimeType: "text/markdown"
          }
        ]
      }
    ],
    sessionTreeMessages: {
      sessionId: "ses_history",
      sessions: [{ rootSessionId: "ses_history", sessionId: "ses_history", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "run.started",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            runId: "run_history",
            status: "RUNNING",
            occurredAt: "2026-06-28T08:00:01Z"
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            message: { id: "remote_assistant", role: "assistant", content: "测试报告已生成" }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            messageId: "remote_assistant",
            messageID: "remote_assistant",
            part: {
              id: "part_text",
              messageID: "remote_assistant",
              type: "text",
              text: "测试报告已生成"
            }
          }
        },
        {
          type: "message.part.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            messageId: "remote_assistant",
            messageID: "remote_assistant",
            part: {
              id: "part_file",
              messageID: "remote_assistant",
              type: "file",
              name: "登录测试报告.md",
              path: "docs/登录测试报告.md",
              mimeType: "text/markdown"
            }
          }
        }
      ]
    },
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      createdAt: "2026-06-28T08:00:00Z",
      updatedAt: "2026-06-28T08:01:00Z"
    },
    historyDiffFiles: [
      {
        path: "docs/登录测试报告.md",
        patch: "--- /dev/null\n+++ b/登录测试报告.md\n@@ -0,0 +1,1 @@\n+# 登录测试报告",
        additions: 1,
        deletions: 0,
        status: "added"
      }
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "请生成登录测试报告").click();
  // 当前会话切换会短暂展示顶部信息提示；提示层不改变关闭处理，直接触发关闭按钮。
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click({ force: true });

  await expect.poll(() => sessionTreeRequests).toContain("/api/internal/agent/opencode/sessions/ses_history/session-tree/messages");
  await expect.poll(() => sessionMessageRequests).toContain("/api/internal/platform/opencode-runtime/sessions/ses_history/messages?page=1&size=100&refresh=false");
  await expect(page.getByText("测试报告已生成")).toBeVisible();
  await expect(page.getByRole("button", { name: "停止执行" })).toHaveCount(0);
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("继续追问测试报告");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  const changesCard = page.getByRole("button", { name: /文件修改 1/ });
  await expect(changesCard).toContainText("+1");
  await changesCard.click();
  await expect(page.getByTestId("oc-diff-summary").getByText("登录测试报告.md", { exact: false })).toBeVisible();
});

test("manual resend keeps the user turn, shows running status, and replaces the old answer", async ({ page }) => {
  const runResendRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    runResendRequests,
    sessions: [session()],
    sessionMessages: [
      {
        messageId: "msg_11111111111111111111111111111111",
        remoteMessageId: "msg_remote_source",
        sessionId: "ses_1",
        role: "USER",
        content: "重新检查登录流程",
        createdAt: "2026-08-07T08:00:00Z",
        runId: "run_history"
      },
      {
        messageId: "msg_22222222222222222222222222222222",
        remoteMessageId: "msg_remote_old_answer",
        sessionId: "ses_1",
        role: "ASSISTANT",
        content: "旧回答不应继续显示",
        createdAt: "2026-08-07T08:01:00Z",
        runId: "run_history"
      }
    ],
    historyRun: {
      runId: "run_history",
      sessionId: "ses_1",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      createdAt: "2026-08-07T08:00:00Z",
      updatedAt: "2026-08-07T08:01:00Z"
    },
    runEventsByRunId: { run_resend_replacement: [] }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await selectPetContextSession(page);
  await expect(page.getByText("旧回答不应继续显示")).toBeVisible();
  await page.getByRole("button", { name: "撤销重发最后一条消息" }).click();

  const resendComposer = page.getByPlaceholder("修改上一条消息后发送");
  await expect(resendComposer).toHaveValue("重新检查登录流程");
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runResendRequests.length).toBe(1);
  expect(runResendRequests[0]).toMatchObject({ editedPrompt: "重新检查登录流程" });
  await expect(page.getByTestId("figma-work-status-dock").locator(".oc-work-status[data-status='running']")).toBeVisible();
  await expect(page.getByText("旧回答不应继续显示")).toHaveCount(0);

  const resendPayload = {
    resendId: "rsd_e2e",
    sourceRunId: "run_history",
    replacementRunId: "run_resend_replacement",
    trigger: "MANUAL",
    totalAttempt: 1,
    automaticAttempt: 0,
    automaticLimit: 3,
    status: "DISPATCHED",
    executeAt: "2026-08-07T08:02:00Z"
  };
  await callAgentWorkbenchHandler(page, "handleRunEvent", [{
    ...event(1, "run.resend.started", resendPayload),
    runId: "run_resend_replacement"
  }, "ses_1"]);
  await callAgentWorkbenchHandler(page, "handleRunEvent", [{
    ...event(2, "message.updated", {
      message: { id: "msg_remote_replacement", role: "user", content: "重新检查登录流程" }
    }),
    runId: "run_resend_replacement"
  }, "ses_1"]);
  await callAgentWorkbenchHandler(page, "handleRunEvent", [{
    ...event(3, "message.updated", {
      message: { id: "msg_remote_new_answer", role: "assistant" }
    }),
    runId: "run_resend_replacement"
  }, "ses_1"]);
  await callAgentWorkbenchHandler(page, "handleRunEvent", [{
    ...event(4, "message.part.updated", {
      messageID: "msg_remote_new_answer",
      part: { id: "part_resend_answer", messageID: "msg_remote_new_answer", type: "text", text: "新回答已经接管页面" }
    }),
    runId: "run_resend_replacement"
  }, "ses_1"]);
  await callAgentWorkbenchHandler(page, "handleRunEvent", [{
    ...event(5, "run.succeeded", {}),
    runId: "run_resend_replacement"
  }, "ses_1"]);

  await expect(page.getByText("旧回答不应继续显示")).toHaveCount(0);
  await expect(page.getByText("新回答已经接管页面")).toBeVisible();
  await expect(page.locator(".oc-user-message")).toHaveCount(1);
  await expect(page.locator(".oc-user-message")).toHaveAttribute("data-oc-turn-id", "msg_remote_replacement");
  await expect(page.getByTestId("figma-work-status-dock").locator(".oc-work-status[data-status='running']")).toHaveCount(0);
});

test("history run projection keeps sending locked until stale details cannot overwrite a new run", async ({ page }) => {
  let releaseHistoryRun!: () => void;
  const historyRunGate = new Promise<void>((resolve) => {
    releaseHistoryRun = resolve;
  });
  const historyRunRequests: string[] = [];
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    historyRunGate,
    historyRunRequests,
    runRequests,
    sessions: [{
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      title: "等待历史运行详情",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T03:00:00Z",
      updatedAt: "2026-07-10T03:01:00Z"
    }],
    sessionMessages: [{
      messageId: "msg_history_projection",
      sessionId: "ses_history",
      role: "ASSISTANT",
      content: "历史正文已就绪",
      runId: "run_history",
      createdAt: "2026-07-10T03:01:00Z"
    }],
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      createdAt: "2026-07-10T03:00:00Z",
      updatedAt: "2026-07-10T03:01:00Z"
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "等待历史运行详情").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
  await expect.poll(() => historyRunRequests).toContain("/api/internal/agent/opencode/runs/run_history");
  await expect(page.getByText("历史正文已就绪")).toBeVisible();
  await expect(page.getByText("正在加载会话内容…")).toHaveCount(0);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.getByRole("button", { name: "发送" });
  await composer.fill("历史投影完成后发送");
  await expect(sendButton).toBeDisabled();
  await sendButton.click({ force: true });
  expect(runRequests).toEqual([]);

  releaseHistoryRun();
  await expect(page.getByText("历史正文已就绪")).toBeVisible();
  await expect(sendButton).toBeEnabled();
  await sendButton.click();
  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({ sessionId: "ses_history", prompt: "历史投影完成后发送" });
});

test("switching history restores a pending native question dock instead of only its tool JSON", async ({ page }) => {
  const questionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    questionReplies,
    sessions: [{
      sessionId: "ses_history_question",
      workspaceId: "wrk_1234567890abcdef",
      title: "历史提问会话",
      status: "ACTIVE",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    }],
    sessionMessages: [{
      messageId: "msg_history_question",
      sessionId: "ses_history_question",
      role: "ASSISTANT",
      content: "等待用户选择验证范围",
      createdAt: "2026-07-11T08:01:00Z",
      parts: [{
        id: "part_question_tool",
        messageID: "msg_history_question",
        type: "tool",
        tool: "question",
        state: { status: "running", input: { question: "请选择验证范围" } }
      }]
    }],
    sessionTreeMessages: {
      sessionId: "ses_history_question",
      sessions: [{ rootSessionId: "ses_history_question", sessionId: "ses_history_question", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: []
    },
    sessionQuestionsById: {
      ses_history_question: [{
        id: "que_history_question",
        sessionID: "ses_history_question",
        questions: [{
          question: "请选择验证范围",
          header: "验证范围",
          options: [{ label: "接口测试", description: "执行接口回归" }],
          multiple: false,
          custom: true
        }]
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, "历史提问会话").click();
  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("请选择验证范围");
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
  await page.getByRole("button", { name: "接口测试" }).click();
  await page.getByRole("button", { name: "提交" }).click();
  await expect.poll(() => questionReplies).toEqual([{ answers: [["接口测试"]] }]);
});

test("switching history restores a pending native permission dock and allows reply", async ({ page }) => {
  const permissionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    permissionReplies,
    sessions: [{
      sessionId: "ses_history_permission",
      workspaceId: "wrk_1234567890abcdef",
      title: "历史权限会话",
      status: "ACTIVE",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    }],
    sessionMessagesBySessionId: { ses_history_permission: [] },
    sessionTreeMessagesBySessionId: {
      ses_history_permission: {
        sessionId: "ses_history_permission",
        sessions: [{ rootSessionId: "ses_history_permission", sessionId: "ses_history_permission", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }
    },
    sessionPermissionsById: {
      ses_history_permission: [{
        id: "perm_history_permission",
        sessionID: "ses_remote_permission",
        permission: "edit",
        title: "允许修改测试文件",
        pattern: "tests/**"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, "历史权限会话").click();
  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("允许修改测试文件");
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
  await page.getByRole("button", { name: "允许一次" }).click();
  await expect.poll(() => permissionReplies).toEqual([{ decision: "once" }]);
});

test("history root permission snapshot keeps child permission attention from the session tree", async ({ page }) => {
  const permissionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    permissionReplies,
    sessions: [{
      sessionId: "ses_history_permission_tree",
      workspaceId: "wrk_1234567890abcdef",
      title: "历史子智能体权限会话",
      status: "ACTIVE",
      createdAt: "2026-07-21T23:49:00Z",
      updatedAt: "2026-07-21T23:50:18Z"
    }],
    sessionMessagesBySessionId: {
      ses_history_permission_tree: [{
        messageId: "msg_root_task",
        sessionId: "ses_history_permission_tree",
        role: "ASSISTANT",
        content: "",
        createdAt: "2026-07-21T23:49:30Z",
        parts: [{
          id: "part_child_permission",
          messageID: "msg_root_task",
          type: "tool",
          tool: "task",
          callID: "call_child_permission",
          state: {
            status: "running",
            input: { description: "检查外部参考目录", subagent_type: "explore" }
          }
        }]
      }]
    },
    sessionTreeMessagesBySessionId: {
      ses_history_permission_tree: {
        sessionId: "ses_history_permission_tree",
        sessions: [
          { rootSessionId: "ses_history_permission_tree", sessionId: "ses_history_permission_tree", childSession: false },
          {
            rootSessionId: "ses_history_permission_tree",
            sessionId: "ses_child_permission",
            parentSessionId: "ses_history_permission_tree",
            childSession: true,
            taskMessageId: "msg_root_task",
            taskPartId: "part_child_permission",
            taskCallId: "call_child_permission"
          }
        ],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: { part_child_permission: "ses_child_permission" },
        events: [{
          type: "permission.asked",
          rootSessionId: "ses_history_permission_tree",
          sessionId: "ses_child_permission",
          parentSessionId: "ses_history_permission_tree",
          childSession: true,
          payload: {
            id: "perm_child_tree",
            sessionID: "ses_child_permission",
            permission: "external_directory",
            patterns: ["/Users/huang/.testagent/agent-opencode/references/*"]
          }
        }]
      }
    },
    sessionPermissionsById: {
      ses_history_permission_tree: [{
        id: "perm_root_live",
        sessionID: "ses_remote_root",
        permission: "read",
        patterns: ["README.md"]
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "历史子智能体权限会话").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();

  await expect(page.locator(".figma-chat-question-dock")).toContainText("README.md");
  // 历史 snapshot 可能只携带最小 child 索引，任务标题可回退为 Subagent task；session 绑定与铃铛才是本场景契约。
  const childCard = page.locator(".oc-subagent-card");
  await expect(childCard.getByLabel("子智能体有待处理权限")).toBeVisible();

  await childCard.click();
  const childDock = page.locator(".figma-chat-question-dock");
  await expect(childDock).toContainText("访问项目目录之外的文件");
  await expect(childDock).toContainText("/Users/huang/.testagent/agent-opencode/references/*");
  await expect(childDock).not.toContainText("perm_child_tree");
  await page.getByRole("button", { name: "允许一次" }).click();
  await expect.poll(() => permissionReplies).toEqual([{ decision: "once" }]);

  await page.getByRole("button", { name: "切换到主 Agent" }).click();
  await expect(childCard.getByLabel("子智能体有待处理权限")).toHaveCount(0);
});

test("history pending interaction stays scoped to its own session", async ({ page }) => {
  const sessionMessageRequests: string[] = [];
  await mockBackendApi(page, {
    sessions: [
      {
        sessionId: "ses_history_question_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "A 会话有提问",
        status: "ACTIVE",
        createdAt: "2026-07-11T08:00:00Z",
        updatedAt: "2026-07-11T08:01:00Z"
      },
      {
        sessionId: "ses_history_question_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "B 会话无提问",
        status: "ACTIVE",
        createdAt: "2026-07-11T08:02:00Z",
        updatedAt: "2026-07-11T08:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_history_question_a: [],
      ses_history_question_b: []
    },
    sessionMessageRequests,
    sessionTreeMessagesBySessionId: {
      ses_history_question_a: {
        sessionId: "ses_history_question_a",
        sessions: [{ rootSessionId: "ses_history_question_a", sessionId: "ses_history_question_a", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      },
      ses_history_question_b: {
        sessionId: "ses_history_question_b",
        sessions: [{ rootSessionId: "ses_history_question_b", sessionId: "ses_history_question_b", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }
    },
    sessionQuestionsById: {
      ses_history_question_a: [{
        id: "que_history_question_a",
        sessionID: "ses_history_question_a",
        questions: [{ question: "只属于 A 的问题", header: "范围", options: [{ label: "A" }], multiple: false }]
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, "A 会话有提问").click();
  await expect(page.locator(".figma-chat-question-dock")).toContainText("只属于 A 的问题");
  await expect(page.getByRole("dialog", { name: "会话列表" })).toBeVisible();
  await expect(historySessionButton(page, "A 会话有提问")).toHaveAttribute("aria-current", "true");
  await historySessionButton(page, "B 会话无提问").click({ force: true });
  await expect.poll(() => sessionMessageRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_history_question_b/messages?page=1&size=100&refresh=false"
  );
  await expect(page.locator(".figma-chat-question-dock")).toHaveCount(0);
  await expect(page.getByText("只属于 A 的问题")).toHaveCount(0);
  await expect(page.getByRole("dialog", { name: "会话列表" })).toBeVisible();
  await expect(historySessionButton(page, "B 会话无提问")).toHaveAttribute("aria-current", "true");
});

test("switching to a running history maps its remote question event and allows reply", async ({ page }) => {
  const questionReplies: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    questionReplies,
    sessions: [{
      sessionId: "ses_history_running",
      workspaceId: "wrk_1234567890abcdef",
      title: "运行中的历史提问",
      status: "ACTIVE",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    }],
    sessionMessagesBySessionId: {
      ses_history_running: [{
        messageId: "msg_history_running",
        sessionId: "ses_history_running",
        runId: "run_history",
        role: "ASSISTANT",
        content: "正在等待用户选择",
        createdAt: "2026-07-11T08:01:00Z",
        parts: []
      }]
    },
    sessionTreeMessagesBySessionId: {
      ses_history_running: {
        sessionId: "ses_history_running",
        sessions: [{ rootSessionId: "ses_history_running", sessionId: "ses_history_running", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }
    },
    // 历史运行中的交互必须同时存在于当前 OpenCode pending 快照；旧 Event 单独回放不再伪造可回复弹框。
    sessionQuestionsById: {
      ses_history_running: [{
        id: "que_history_running",
        sessionID: "ses_remote_history",
        questions: [{ question: "历史运行中：选择继续方式", options: [{ label: "继续" }, { label: "停止" }] }]
      }]
    },
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 1,
      sessions: [{
        sessionId: "ses_history_running",
        runId: "run_history",
        runStatus: "RUNNING",
        attention: "QUESTION",
        attentionEventId: "evt_remote_question",
        updatedAt: "2026-07-11T08:01:00Z"
      }],
      generatedAt: "2026-07-11T08:01:00Z"
    },
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history_running",
      workspaceId: "wrk_1234567890abcdef",
      status: "RUNNING",
      createdAt: "2026-07-11T08:00:00Z",
      updatedAt: "2026-07-11T08:01:00Z"
    },
    runEventsByRunId: {
      run_history: [{
        ...event(1, "question.asked", {
          requestId: "que_history_running",
          sessionId: "ses_remote_history",
          questions: [{ question: "历史运行中：选择继续方式", options: [{ label: "继续" }, { label: "停止" }] }]
        }),
        runId: "run_history"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, "运行中的历史提问").click();

  const dock = page.locator(".figma-chat-question-dock");
  await expect(dock).toContainText("历史运行中：选择继续方式");
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
  await dock.getByRole("button", { name: "继续", exact: true }).click();
  await dock.getByRole("button", { name: "提交", exact: true }).click();
  await expect.poll(() => questionReplies).toEqual([{ answers: [["继续"]] }]);
});

test("switching history resumes the runtime-state run and reconciles active-run in background", async ({ page }) => {
  const activeRunRequests: string[] = [];
  const runEventRequests: string[] = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, {
    activeRunRequests,
    runEventRequests,
    runContextRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    sessions: [
      {
        sessionId: "ses_history",
        workspaceId: "wrk_1234567890abcdef",
        title: "/test-design-orthogonal 车贷",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_user",
        sessionId: "ses_history",
        role: "USER",
        content: "/test-design-orthogonal 车贷",
        createdAt: "2026-06-28T08:00:00Z",
        runId: "run_history"
      }
    ],
    historyRun: {
      runId: "run_history",
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      status: "SUCCEEDED",
      createdAt: "2026-06-28T08:00:00Z",
      updatedAt: "2026-06-28T08:01:00Z"
    },
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 0,
      sessions: [{
        sessionId: "ses_history",
        runId: "run_1",
        runStatus: "RUNNING",
        attention: null,
        updatedAt: "2026-06-28T08:02:01Z"
      }],
      generatedAt: "2026-06-28T08:02:02Z"
    },
    runEvents: [
      event(1, "message.part.delta", {
        messageId: "msg_live",
        messageID: "msg_live",
        partId: "part_text",
        partID: "part_text",
        delta: "正交表实时输出"
      })
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, /test-design-orthogonal/).click();

  await expect.poll(() => runEventRequests).toContain("/api/internal/agent/opencode/runs/run_1/events");
  expect(activeRunRequests).toEqual(["/api/internal/platform/opencode-runtime/sessions/ses_history/active-run"]);
  expect(runContextRequests).toEqual(["ses_history"]);
  await expect(page.getByText("正交表实时输出")).toBeVisible();
});

test("session share owner history keeps a shared actor run snapshot when history enrichment arrives late", async ({ page }) => {
  let releaseHistoryInteractions!: () => void;
  const sessionInteractionsGate = new Promise<void>((resolve) => {
    releaseHistoryInteractions = resolve;
  });
  const activeRun = {
    runId: "run_delegated_live",
    sessionId: "ses_delegated_live",
    workspaceId: "wrk_1234567890abcdef",
    status: "RUNNING",
    triggeredByUserId: "usr_admin",
    messageSenderUserId: "usr_shared_writer",
    messageSenderUnifiedAuthId: "ucid_shared_writer",
    messageSentBySharedUser: true,
    createdAt: "2026-08-10T01:00:00Z",
    updatedAt: "2026-08-10T01:00:01Z"
  };
  await installAuthenticatedRunEventFetchStream(page, {
    run_delegated_live: [{
      delayMs: 20,
      events: [{
        seq: 0,
        type: "run.snapshot.reset",
        payload: {
          reason: "TRANSIENT_SNAPSHOT_RECOVERY",
          snapshot: {
            barrierSeq: 2,
            runtimeVersion: 5,
            events: [
              {
                eventId: "evt_delegated_started",
                runId: "run_delegated_live",
                seq: 0,
                type: "run.started",
                traceId: "trace_delegated_live",
                occurredAt: "2026-08-10T01:00:01Z",
                payload: { status: "RUNNING" }
              },
              {
                eventId: "evt_delegated_user",
                runId: "run_delegated_live",
                seq: 0,
                type: "message.updated",
                traceId: "trace_delegated_live",
                occurredAt: "2026-08-10T01:00:00Z",
                payload: {
                  sessionId: "ses_delegated_live",
                  rootSessionId: "ses_delegated_live",
                  message: {
                    id: "msg_delegated_user",
                    role: "user",
                    text: "由协作者发起的实时任务",
                    senderUserId: "usr_shared_writer",
                    senderUsername: "协作者"
                  }
                }
              },
              {
                eventId: "evt_delegated_assistant",
                runId: "run_delegated_live",
                seq: 0,
                type: "message.updated",
                traceId: "trace_delegated_live",
                occurredAt: "2026-08-10T01:00:02Z",
                payload: {
                  sessionId: "ses_delegated_live",
                  rootSessionId: "ses_delegated_live",
                  message: { id: "msg_delegated_assistant", role: "assistant" }
                }
              },
              {
                eventId: "evt_delegated_reasoning",
                runId: "run_delegated_live",
                seq: 0,
                type: "message.part.updated",
                traceId: "trace_delegated_live",
                occurredAt: "2026-08-10T01:00:03Z",
                payload: {
                  sessionId: "ses_delegated_live",
                  rootSessionId: "ses_delegated_live",
                  messageID: "msg_delegated_assistant",
                  part: {
                    id: "part_delegated_reasoning",
                    messageID: "msg_delegated_assistant",
                    type: "reasoning",
                    text: "正在同步分析共享会话",
                    state: { status: "running" }
                  }
                }
              },
              {
                eventId: "evt_delegated_write",
                runId: "run_delegated_live",
                seq: 0,
                type: "message.part.updated",
                traceId: "trace_delegated_live",
                occurredAt: "2026-08-10T01:00:04Z",
                payload: {
                  sessionId: "ses_delegated_live",
                  rootSessionId: "ses_delegated_live",
                  messageID: "msg_delegated_assistant",
                  part: {
                    id: "part_delegated_write",
                    messageID: "msg_delegated_assistant",
                    type: "tool",
                    tool: "write",
                    state: {
                      status: "completed",
                      input: { filePath: "tests/shared.spec.ts" }
                    }
                  }
                }
              }
            ]
          }
        }
      }]
    }]
  });
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    sessionInteractionsGate,
    sessions: [{
      sessionId: "ses_delegated_live",
      workspaceId: "wrk_1234567890abcdef",
      title: "协作者运行中的会话",
      status: "ACTIVE",
      createdAt: "2026-08-10T00:59:00Z",
      updatedAt: "2026-08-10T01:00:04Z"
    }],
    sessionMessagesBySessionId: {
      ses_delegated_live: [{
        messageId: "msg_delegated_user",
        sessionId: "ses_delegated_live",
        role: "USER",
        content: "由协作者发起的实时任务",
        senderUserId: "usr_shared_writer",
        senderUsername: "协作者",
        senderUnifiedAuthId: "ucid_shared_writer",
        sentBySharedUser: true,
        createdAt: "2026-08-10T01:00:00Z"
      }]
    },
    sessionTreeMessagesBySessionId: {
      ses_delegated_live: {
        sessionId: "ses_delegated_live",
        sessions: [{ rootSessionId: "ses_delegated_live", sessionId: "ses_delegated_live", childSession: false }],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }
    },
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 0,
      permissionCount: 0,
      sessions: [{
        sessionId: "ses_delegated_live",
        runId: "run_delegated_live",
        runStatus: "RUNNING",
        attention: null,
        updatedAt: "2026-08-10T01:00:04Z"
      }],
      generatedAt: "2026-08-10T01:00:05Z"
    },
    activeRun,
    runsByRunId: { run_delegated_live: activeRun }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, /协作者运行中的会话/).click();

  await expect.poll(() => page.evaluate(() => (
    (window as Window & { __titleWatchRunStreams?: Array<{ runId: string }> })
      .__titleWatchRunStreams?.some((item) => item.runId === "run_delegated_live") ?? false
  ))).toBe(true);
  await expect(page.getByRole("button", { name: "停止执行" })).toBeEnabled();
  await expect(page.getByText("正在同步分析共享会话")).toBeVisible();
  await expect(page.getByTestId("oc-work-status-event-write")).toBeVisible();

  releaseHistoryInteractions();
  await expect(page.getByText("已切换 Session", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "停止执行" })).toBeEnabled();
  await expect(page.getByText("正在同步分析共享会话")).toBeVisible();
  await expect(page.getByTestId("oc-work-status-event-write")).toBeVisible();
});

test("runtime-state outage performs only one active-run fallback", async ({ page }) => {
  const activeRunRequests: string[] = [];
  const runtimeStateEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    activeRunRequests,
    runtimeStateEventRequests,
    runtimeStateStreamFailure: true,
    sessions: [{
      sessionId: "ses_history",
      workspaceId: "wrk_1234567890abcdef",
      title: "恢复中的会话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-06-28T08:00:00Z",
      updatedAt: "2026-06-28T08:01:00Z"
    }],
    activeRun: null
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "恢复中的会话").click();
  await expect.poll(() => runtimeStateEventRequests.length).toBeGreaterThanOrEqual(2);
  await page.waitForTimeout(3500);

  expect(activeRunRequests).toEqual(["/api/internal/platform/opencode-runtime/sessions/ses_history/active-run"]);
});

test("runtime-state outage falls back once for each switched session in the same outage", async ({ page }) => {
  const activeRunRequests: string[] = [];
  const runtimeStateEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    activeRunRequests,
    runtimeStateEventRequests,
    runtimeStateStreamFailure: true,
    sessions: [
      {
        sessionId: "ses_outage_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "故障会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      },
      {
        sessionId: "ses_outage_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "故障会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:02:00Z",
        updatedAt: "2026-06-28T08:03:00Z"
      }
    ],
    activeRun: null
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "故障会话 A").click();
  await expect.poll(() => activeRunRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_a/active-run"
  );

  await historySessionButton(page, "故障会话 B").click();
  await expect.poll(() => activeRunRequests).toContain(
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_b/active-run"
  );
  await expect.poll(() => runtimeStateEventRequests.length).toBeGreaterThanOrEqual(2);
  await page.waitForTimeout(3500);

  expect(activeRunRequests).toEqual([
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_a/active-run",
    "/api/internal/platform/opencode-runtime/sessions/ses_outage_b/active-run"
  ]);
});

test("a delayed history switch cannot overwrite a newer session and workspace", async ({ page }) => {
  let releaseWorkspaceA!: () => void;
  const workspaceAGate = new Promise<void>((resolve) => {
    releaseWorkspaceA = resolve;
  });
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const workspaceRequests: string[] = [];
  const runContextRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  const workspaceA = {
    ...workspace(),
    workspaceId: "wrk_race_a",
    name: "竞态工作区 A",
    rootPath: "/Users/huang/workspace/race-a",
    appId: "app_gcms"
  };
  const workspaceB = {
    ...workspace(),
    workspaceId: "wrk_race_b",
    name: "竞态工作区 B",
    rootPath: "/Users/huang/workspace/race-b",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    fileRequests,
    workspaceRequests,
    workspaceRequestGates: { wrk_race_a: workspaceAGate },
    runContextRequests,
    sessionMessageRequests,
    workspaces: [workspace(), workspaceA, workspaceB],
    markRecentWorkspaces: { wrk_race_a: workspaceA, wrk_race_b: workspaceB },
    sessions: [
      {
        sessionId: "ses_race_a",
        workspaceId: "wrk_race_a",
        title: "竞态会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:00:00Z",
        updatedAt: "2026-07-10T02:01:00Z"
      },
      {
        sessionId: "ses_race_b",
        workspaceId: "wrk_race_b",
        title: "竞态会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:02:00Z",
        updatedAt: "2026-07-10T02:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_race_a: [{
        messageId: "msg_race_a",
        sessionId: "ses_race_a",
        role: "ASSISTANT",
        content: "竞态正文 A",
        createdAt: "2026-07-10T02:01:00Z"
      }],
      ses_race_b: [{
        messageId: "msg_race_b",
        sessionId: "ses_race_b",
        role: "ASSISTANT",
        content: "竞态正文 B",
        createdAt: "2026-07-10T02:03:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  fileRequests.length = 0;
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "竞态会话 A").click();
  await expect.poll(() => workspaceRequests).toContain("wrk_race_a");

  await historySessionButton(page, "竞态会话 B").click();
  await expect(page.getByText("竞态正文 B")).toBeVisible();
  releaseWorkspaceA();
  await page.waitForTimeout(200);

  expect(runContextRequests).toEqual(["ses_race_b"]);
  expect(sessionMessageRequests).toEqual([
    "/api/internal/platform/opencode-runtime/sessions/ses_race_a/messages?page=1&size=100&refresh=false",
    "/api/internal/platform/opencode-runtime/sessions/ses_race_b/messages?page=1&size=100&refresh=false"
  ]);
  expect(fileRequests).toContainEqual({ workspaceId: "wrk_race_b", path: "" });
  expect(fileRequests).not.toContainEqual({ workspaceId: "wrk_race_a", path: "" });
  await expect(page.getByText("竞态正文 B")).toBeVisible();
  await expect(page.getByText("竞态正文 A")).toHaveCount(0);
});

test("history loading cannot send a run to the previous session", async ({ page }) => {
  let releaseTargetWorkspace!: () => void;
  const targetWorkspaceGate = new Promise<void>((resolve) => {
    releaseTargetWorkspace = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const workspaceRequests: string[] = [];
  const previousWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_send_previous",
    name: "发送保护旧工作区",
    rootPath: "/Users/huang/workspace/history-send-previous",
    appId: "app_gcms"
  };
  const targetWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_send_target",
    name: "发送保护目标工作区",
    rootPath: "/Users/huang/workspace/history-send-target",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    workspaceRequests,
    workspaceRequestGates: { wrk_history_send_target: targetWorkspaceGate },
    workspaces: [workspace(), previousWorkspace, targetWorkspace],
    markRecentWorkspaces: {
      wrk_history_send_previous: previousWorkspace,
      wrk_history_send_target: targetWorkspace
    },
    sessions: [
      {
        sessionId: "ses_history_send_previous",
        workspaceId: "wrk_history_send_previous",
        title: "发送保护旧会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:04:00Z",
        updatedAt: "2026-07-10T02:05:00Z"
      },
      {
        sessionId: "ses_history_send_target",
        workspaceId: "wrk_history_send_target",
        title: "发送保护目标会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T02:06:00Z",
        updatedAt: "2026-07-10T02:07:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_history_send_previous: [{
        messageId: "msg_history_send_previous",
        sessionId: "ses_history_send_previous",
        role: "ASSISTANT",
        content: "发送保护旧正文",
        createdAt: "2026-07-10T02:05:00Z"
      }],
      ses_history_send_target: [{
        messageId: "msg_history_send_target",
        sessionId: "ses_history_send_target",
        role: "ASSISTANT",
        content: "发送保护目标正文",
        createdAt: "2026-07-10T02:07:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "发送保护旧会话").click();
  await expect(page.getByText("发送保护旧正文")).toBeVisible();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("切换中不得发送");
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "发送保护目标会话").click();
  await expect.poll(() => workspaceRequests).toContain("wrk_history_send_target");

  const sendButton = page.getByRole("button", { name: "发送", exact: true });
  await expect(sendButton).toBeDisabled();
  await sendButton.click({ force: true });
  await page.waitForTimeout(100);
  expect(runRequests).toEqual([]);

  releaseTargetWorkspace();
  await expect(page.getByText("发送保护目标正文")).toBeVisible();
  expect(runRequests).toEqual([]);
});

test("a delayed history switch cannot survive a new conversation", async ({ page }) => {
  let releaseHistoryWorkspace!: () => void;
  const historyWorkspaceGate = new Promise<void>((resolve) => {
    releaseHistoryWorkspace = resolve;
  });
  const runContextRequests: string[] = [];
  const runRequests: Array<Record<string, unknown>> = [];
  const sessionRequests: Array<Record<string, unknown>> = [];
  const sessionMessageRequests: string[] = [];
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_new_conversation",
    name: "新对话竞态工作区",
    rootPath: "/Users/huang/workspace/history-new-conversation",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runContextRequests,
    runRequests,
    sessionRequests,
    sessionMessageRequests,
    fileRequests,
    workspaceRequestGates: { wrk_history_new_conversation: historyWorkspaceGate },
    workspaces: [workspace(), historyWorkspace],
    markRecentWorkspaces: { wrk_history_new_conversation: historyWorkspace },
    sessions: [{
      sessionId: "ses_history_new_conversation",
      workspaceId: "wrk_history_new_conversation",
      title: "等待后新建对话",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T02:10:00Z",
      updatedAt: "2026-07-10T02:11:00Z"
    }],
    sessionMessagesBySessionId: {
      ses_history_new_conversation: [{
        messageId: "msg_history_new_conversation",
        sessionId: "ses_history_new_conversation",
        role: "ASSISTANT",
        content: "不应恢复的新对话正文",
        createdAt: "2026-07-10T02:11:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  fileRequests.length = 0;
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "等待后新建对话").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
  await page.getByRole("button", { name: "新建对话" }).click();
  releaseHistoryWorkspace();
  await page.waitForTimeout(200);

  expect(runContextRequests).not.toContain("ses_history_new_conversation");
  expect(fileRequests).not.toContainEqual({ workspaceId: "wrk_history_new_conversation", path: "" });
  await expect(page.getByText("不应恢复的新对话正文")).toHaveCount(0);

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.getByRole("button", { name: "发送" });
  await composer.fill("新对话恢复发送");
  await expect(sendButton).toBeEnabled();
  await sendButton.click();
  await expect.poll(() => runRequests.length).toBe(1);

  expect(sessionRequests).toHaveLength(1);
  expect(sessionRequests[0]).toMatchObject({ workspaceId: "wrk_personal_default" });
  expect(runContextRequests).toEqual(["ses_1"]);
  expect(runRequests[0]).toMatchObject({ sessionId: "ses_1", prompt: "新对话恢复发送" });
});

test("a delayed history switch cannot overwrite a manual application workspace switch", async ({ page }) => {
  let releaseHistoryWorkspace!: () => void;
  const historyWorkspaceGate = new Promise<void>((resolve) => {
    releaseHistoryWorkspace = resolve;
  });
  const runContextRequests: string[] = [];
  const runRequests: Array<Record<string, unknown>> = [];
  const sessionRequests: Array<Record<string, unknown>> = [];
  const sessionMessageRequests: string[] = [];
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_manual_switch",
    name: "迟到历史工作区",
    rootPath: "/Users/huang/workspace/history-manual-switch",
    appId: "app_gcms"
  };
  const cossWorkspace = {
    ...workspace(),
    workspaceId: "wrk_coss_manual_switch",
    name: "COSS 手动工作区",
    rootPath: "/Users/huang/workspace/coss-manual-switch",
    appId: "app_coss",
    versionId: "awv_coss_manual",
    applicationWorkspaceId: "awp_coss_manual"
  };
  await mockBackendApi(page, {
    fileRequests,
    runContextRequests,
    runRequests,
    sessionRequests,
    sessionMessageRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      },
      app_coss: cossWorkspace
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")],
      awv_coss_manual: [{
        ...defaultPersonalWorkspace("awv_coss_manual"),
        appId: "app_coss",
        applicationWorkspaceId: "awp_coss_manual",
        runtimeWorkspace: cossWorkspace
      }]
    },
    workspaceRequestGates: { wrk_history_manual_switch: historyWorkspaceGate },
    workspaces: [workspace(), historyWorkspace, cossWorkspace],
    markRecentWorkspaces: {
      wrk_history_manual_switch: historyWorkspace,
      wrk_coss_manual_switch: cossWorkspace
    },
    sessions: [{
      sessionId: "ses_history_manual_switch",
      workspaceId: "wrk_history_manual_switch",
      title: "等待手动切工作区",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T02:20:00Z",
      updatedAt: "2026-07-10T02:21:00Z"
    }]
  });

  await gotoWorkbench(page);
  fileRequests.length = 0;
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "等待手动切工作区").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();

  await page.getByRole("button", { name: "F-GCMS" }).click();
  await page.getByRole("option", { name: /F-COSS/ }).click();
  await expect.poll(() => fileRequests).toContainEqual({ workspaceId: "wrk_coss_manual_switch", path: "" });
  releaseHistoryWorkspace();
  await page.waitForTimeout(200);

  expect(runContextRequests).not.toContain("ses_history_manual_switch");
  expect(fileRequests).not.toContainEqual({ workspaceId: "wrk_history_manual_switch", path: "" });
  await expect(page.getByRole("button", { name: "F-COSS" })).toBeVisible();

  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.getByRole("button", { name: "发送" });
  await composer.fill("新工作区恢复发送");
  await expect(sendButton).toBeEnabled();
  await sendButton.click();
  await expect.poll(() => runRequests.length).toBe(1);

  expect(sessionRequests).toHaveLength(1);
  expect(sessionRequests[0]).toMatchObject({ workspaceId: "wrk_coss_manual_switch" });
  expect(runContextRequests).toEqual(["ses_1"]);
  expect(runRequests[0]).toMatchObject({ sessionId: "ses_1", prompt: "新工作区恢复发送" });
});

test("a delayed history switch cannot survive an authentication change", async ({ page }) => {
  let releaseHistoryWorkspace!: () => void;
  let releaseSessionMessages!: () => void;
  const historyWorkspaceGate = new Promise<void>((resolve) => {
    releaseHistoryWorkspace = resolve;
  });
  const sessionMessagesGate = new Promise<void>((resolve) => {
    releaseSessionMessages = resolve;
  });
  const runContextRequests: string[] = [];
  const sessionMessageRequests: string[] = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_auth_change",
    name: "认证竞态工作区",
    rootPath: "/Users/huang/workspace/history-auth-change",
    appId: "app_gcms"
  };
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runContextRequests,
    sessionMessageRequests,
    sessionMessagesGate,
    sessionMessagesBySessionId: {
      ses_history_auth_change: [{
        messageId: "msg_auth_change_late",
        sessionId: "ses_history_auth_change",
        role: "ASSISTANT",
        content: "认证变化后的迟到正文不应渲染",
        createdAt: "2026-07-10T02:31:00Z"
      }]
    },
    workspaceRequestGates: { wrk_history_auth_change: historyWorkspaceGate },
    workspaces: [workspace(), historyWorkspace],
    markRecentWorkspaces: { wrk_history_auth_change: historyWorkspace },
    sessions: [{
      sessionId: "ses_history_auth_change",
      workspaceId: "wrk_history_auth_change",
      title: "等待认证变化",
      status: "ACTIVE",
      pinned: false,
      createdAt: "2026-07-10T02:30:00Z",
      updatedAt: "2026-07-10T02:31:00Z"
    }]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "等待认证变化").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
  await expect.poll(() => sessionMessageRequests.length).toBe(1);
  await page.getByRole("button", { name: /当前用户/ }).click();
  await expect(page.getByRole("menuitem", { name: "退出登录" })).toHaveCount(0);
  // 产品菜单按现状不提供退出入口；用应用已有的未认证处理器模拟认证变化，继续验证异步历史切换被拦截。
  await page.evaluate(() => (window as unknown as { __handleUnauthorized?: () => void }).__handleUnauthorized?.());
  await expect(page.getByRole("heading", { name: "智能测试代理平台" })).toBeVisible();
  releaseHistoryWorkspace();
  releaseSessionMessages();
  await page.waitForTimeout(200);

  expect(runContextRequests).not.toContain("ses_history_auth_change");
  await expect(page.getByText("认证变化后的迟到正文不应渲染")).toHaveCount(0);
});

test("a delayed conversation context cannot dispatch after switching history", async ({ page }) => {
  let releaseContextA!: () => void;
  const contextAGate = new Promise<void>((resolve) => {
    releaseContextA = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runContextRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runContextRequests,
    runContextRequestGates: { ses_context_a: contextAGate },
    sessions: [
      {
        sessionId: "ses_context_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "上下文会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T03:00:00Z",
        updatedAt: "2026-07-10T03:01:00Z"
      },
      {
        sessionId: "ses_context_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "上下文会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T03:02:00Z",
        updatedAt: "2026-07-10T03:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_context_a: [{
        messageId: "msg_context_a",
        sessionId: "ses_context_a",
        role: "ASSISTANT",
        content: "上下文正文 A",
        createdAt: "2026-07-10T03:01:00Z"
      }],
      ses_context_b: [{
        messageId: "msg_context_b",
        sessionId: "ses_context_b",
        role: "ASSISTANT",
        content: "上下文正文 B",
        createdAt: "2026-07-10T03:03:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "上下文会话 A").click();
  await expect(page.getByText("上下文正文 A")).toBeVisible();
  await expect.poll(() => runContextRequests).toContain("ses_context_a");
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("等待上下文");
  await page.getByRole("button", { name: "发送" }).click();
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "上下文会话 B").click();
  await expect(page.getByText("上下文正文 B")).toBeVisible();
  releaseContextA();
  await page.waitForTimeout(200);

  expect(runRequests).toEqual([]);
  await expect(page.getByText("上下文正文 B")).toBeVisible();
});

test("a delayed startRun response cannot replace a newer history session", async ({ page }) => {
  let releaseRunRequest!: () => void;
  const runRequestGate = new Promise<void>((resolve) => {
    releaseRunRequest = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequestGate,
    runRequests,
    runEventRequests,
    sessions: [
      {
        sessionId: "ses_start_a",
        workspaceId: "wrk_1234567890abcdef",
        title: "启动会话 A",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T04:00:00Z",
        updatedAt: "2026-07-10T04:01:00Z"
      },
      {
        sessionId: "ses_start_b",
        workspaceId: "wrk_1234567890abcdef",
        title: "启动会话 B",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-10T04:02:00Z",
        updatedAt: "2026-07-10T04:03:00Z"
      }
    ],
    sessionMessagesBySessionId: {
      ses_start_a: [{
        messageId: "msg_start_a",
        sessionId: "ses_start_a",
        role: "ASSISTANT",
        content: "启动正文 A",
        createdAt: "2026-07-10T04:01:00Z"
      }],
      ses_start_b: [{
        messageId: "msg_start_b",
        sessionId: "ses_start_b",
        role: "ASSISTANT",
        content: "启动正文 B",
        createdAt: "2026-07-10T04:03:00Z"
      }]
    }
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "启动会话 A").click();
  await expect(page.getByText("启动正文 A")).toBeVisible();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("等待启动结果");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  await page.getByRole("button", { name: /会话列表/ }).click();
  await historySessionButton(page, "启动会话 B").click();
  await expect(page.getByText("启动正文 B")).toBeVisible();
  releaseRunRequest();
  await page.waitForTimeout(200);

  expect(runEventRequests).not.toContain("/api/internal/agent/opencode/runs/run_1/events");
  await expect(page.getByText("启动正文 B")).toBeVisible();
});

test("an ambiguous startRun failure does not fail a run recovered by runtime-state", async ({ page }) => {
  let releaseRuntimeState!: () => void;
  let releaseRunRequest!: () => void;
  const runtimeStateEventGate = new Promise<void>((resolve) => {
    releaseRuntimeState = resolve;
  });
  const runRequestGate = new Promise<void>((resolve) => {
    releaseRunRequest = resolve;
  });
  const runRequests: Array<Record<string, unknown>> = [];
  const runEventRequests: string[] = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runtimeStateEventGate,
    runRequestGate,
    runRequests,
    runEventRequests,
    runFailureResponses: [{ status: 504, code: "OPENCODE_TIMEOUT", message: "启动确认超时" }],
    runtimeStateSummary: {
      runningCount: 1,
      questionCount: 0,
      sessions: [{
        sessionId: "ses_1",
        runId: "run_runtime_recovered",
        runStatus: "RUNNING",
        attention: null,
        updatedAt: "2026-07-10T05:01:00Z"
      }],
      generatedAt: "2026-07-10T05:01:01Z"
    },
    runEventsByRunId: { run_runtime_recovered: [] }
  });

  await gotoWorkbench(page);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("超时但已受理");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);

  releaseRuntimeState();
  await expect.poll(() => runEventRequests).toContain(
    "/api/internal/agent/opencode/runs/run_runtime_recovered/events"
  );
  releaseRunRequest();
  await page.waitForTimeout(200);

  await expect(page.locator(".figma-chat-retry-card")).toHaveCount(0);
  await expect(page.getByText("启动 Run 失败")).toHaveCount(0);
  await expect(page.getByText("启动确认超时")).toHaveCount(0);
});

test("history loading does not wait for interaction snapshot or message feedback", async ({ page }) => {
  let releaseSessionMessages!: () => void;
  let releaseSessionInteractions!: () => void;
  let releaseMessageFeedback!: () => void;
  const sessionMessagesGate = new Promise<void>((resolve) => {
    releaseSessionMessages = resolve;
  });
  const messageFeedbackGate = new Promise<void>((resolve) => {
    releaseMessageFeedback = resolve;
  });
  const sessionInteractionsGate = new Promise<void>((resolve) => {
    releaseSessionInteractions = resolve;
  });
  const feedbackRequests: string[] = [];
  const sessionTreeRequests: string[] = [];
  const sessionMessageRequests: string[] = [];

  await mockBackendApi(page, {
    sessionTreeRequests,
    sessionMessageRequests,
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    sessions: [
      {
        sessionId: "ses_history",
        workspaceId: "wrk_1234567890abcdef",
        title: "历史加载测试",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-06-28T08:00:00Z",
        updatedAt: "2026-06-28T08:01:00Z"
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_1234567890abcdef1234567890abcdef",
        sessionId: "ses_history",
        role: "ASSISTANT",
        content: "历史正文已加载",
        createdAt: "2026-06-28T08:01:00Z"
      },
      {
        messageId: "msg_2234567890abcdef1234567890abcdef",
        sessionId: "ses_history",
        role: "ASSISTANT",
        content: "历史正文已加载",
        createdAt: "2026-06-28T08:01:01Z"
      }
    ],
    sessionTreeMessages: {
      sessionId: "ses_history",
      sessions: [{ rootSessionId: "ses_history", sessionId: "ses_history", childSession: false }],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [
        {
          type: "message.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            message: { id: "remote_dup", role: "assistant", content: "历史正文已加载" }
          }
        },
        {
          type: "message.updated",
          rootSessionId: "ses_history",
          sessionId: "ses_history",
          childSession: false,
          payload: {
            rootSessionId: "ses_history",
            sessionId: "ses_history",
            message: { id: "remote_dup", role: "assistant", content: "历史正文已加载" }
          }
        }
      ]
    },
    sessionMessagesGate,
    sessionInteractionsGate,
    messageFeedbackGate,
    feedbackRequests
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, "历史加载测试").click();
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();

  await expect(page.getByText("正在加载会话内容…")).toBeVisible();

  releaseSessionMessages();
  await expect(page.getByText("历史正文已加载")).toHaveCount(1);
  await expect(page.getByText("正在加载会话内容…")).toHaveCount(0);
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await composer.fill("等待历史交互快照完成后发送");
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();

  releaseSessionInteractions();
  await expect.poll(() => sessionTreeRequests).toContain("/api/internal/agent/opencode/sessions/ses_history/session-tree/messages");
  await expect.poll(() => sessionMessageRequests).toContain("/api/internal/platform/opencode-runtime/sessions/ses_history/messages?page=1&size=100&refresh=false");
  // 当前历史恢复链没有可用的 run 反馈快照时不会发起 feedback 请求；正文和交互快照仍需完成。
  await expect.poll(() => feedbackRequests).toEqual([]);
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();

  releaseMessageFeedback();
});

test("switching history changes to the session application and workspace", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const markRecentRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  const historyWorkspace = {
    ...workspace(),
    workspaceId: "wrk_history_coss",
    name: "F-COSS 历史工作区",
    rootPath: "/Users/huang/workspace/history-coss",
    appId: "app_coss",
    versionId: "awv_coss",
    applicationWorkspaceId: "awp_coss"
  };
  const historyPersonalWorkspace = {
    ...defaultPersonalWorkspace("awv_coss"),
    personalWorkspaceId: "psw_history_coss",
    appId: "app_coss",
    applicationWorkspaceId: "awp_coss",
    branch: "feature_coss_usr_admin_default",
    runtimeWorkspace: historyWorkspace
  };
  await mockBackendApi(page, {
    fileRequests,
    markRecentRequests,
    personalWorkspaceRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    workspaces: [workspace(), historyWorkspace],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")],
      awv_coss: [historyPersonalWorkspace]
    },
    markRecentWorkspaces: {
      wrk_history_coss: historyWorkspace
    },
    sessions: [
      {
        sessionId: "ses_history_coss",
        workspaceId: "wrk_history_coss",
        title: "COSS 历史会话",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-08T08:00:00Z",
        updatedAt: "2026-07-08T09:00:00Z",
        workspaceContext: {
          appId: "app_coss",
          appName: "F-COSS",
          applicationWorkspaceId: "awp_coss",
          workspaceName: "COSS 主干",
          versionId: "awv_coss",
          version: "20260708"
        }
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_history_coss",
        sessionId: "ses_history_coss",
        role: "USER",
        content: "COSS 历史会话",
        createdAt: "2026-07-08T08:00:00Z"
      }
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await expect(page.getByText("F-COSS · COSS 主干 · 20260708")).toBeVisible();
  await historySessionButton(page, "COSS 历史会话").click();

  await expect.poll(() => markRecentRequests).toContain("wrk_history_coss");
  await expect.poll(() => personalWorkspaceRequests).toContain("awv_coss");
  await expect(page.getByRole("button", { name: "应用：F-COSS", exact: true })).toBeVisible();
  await expect.poll(() => fileRequests).toContainEqual({ workspaceId: "wrk_history_coss", path: "" });
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
  await expect(page.getByRole("button", { name: "新建或上传到工作区根目录" })).toBeVisible();
  await page.getByRole("button", { name: "package.json", exact: true }).hover();
  await expect(page.getByRole("button", { name: "删除 package.json" })).toBeVisible();
});

test("history switch failure keeps current context and makes the session readonly", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const markRecentRequests: string[] = [];
  const forbiddenWorkspace = {
    ...workspace(),
    workspaceId: "wrk_forbidden_coss",
    name: "F-COSS 已失效工作区",
    rootPath: "/Users/huang/workspace/forbidden-coss",
    appId: "app_coss",
    versionId: "awv_forbidden",
    applicationWorkspaceId: "awp_coss"
  };
  await mockBackendApi(page, {
    fileRequests,
    markRecentRequests,
    applications: [
      { appId: "app_gcms", appName: "F-GCMS", enabled: true },
      { appId: "app_coss", appName: "F-COSS", enabled: true }
    ],
    workspaces: [workspace(), forbiddenWorkspace],
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    },
    markRecentFailures: {
      wrk_forbidden_coss: { code: "FORBIDDEN", message: "无该应用工作区权限" }
    },
    sessions: [
      {
        sessionId: "ses_forbidden_coss",
        workspaceId: "wrk_forbidden_coss",
        title: "失效应用历史",
        status: "ACTIVE",
        pinned: false,
        createdAt: "2026-07-08T08:00:00Z",
        updatedAt: "2026-07-08T09:00:00Z",
        workspaceContext: {
          appId: "app_coss",
          appName: "F-COSS",
          applicationWorkspaceId: "awp_coss",
          workspaceName: "COSS 主干",
          versionId: "awv_forbidden",
          version: "20260708"
        }
      }
    ],
    sessionMessages: [
      {
        messageId: "msg_forbidden_coss",
        sessionId: "ses_forbidden_coss",
        role: "ASSISTANT",
        content: "只读历史正文",
        createdAt: "2026-07-08T08:00:00Z"
      }
    ]
  });

  await gotoWorkbench(page);
  await page.getByRole("button", { name: "会话列表" }).click();
  await historySessionButton(page, "失效应用历史").click();

  await expect.poll(() => markRecentRequests).toContain("wrk_forbidden_coss");
  await expect(page.getByRole("button", { name: "F-GCMS" })).toBeVisible();
  await expect.poll(() => fileRequests).not.toContainEqual({ workspaceId: "wrk_forbidden_coss", path: "" });
  await expect(page.getByText("只读历史正文")).toBeVisible();
  const readonlyReason = "你已不属于该会话所属应用，当前会话只读。";
  const composer = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  const sendButton = page.locator(".figma-chat-send-card");
  await expect(composer).toBeEnabled();
  await expect(composer).toHaveAttribute("title", readonlyReason);
  await composer.fill("继续执行");
  await expect(sendButton).toBeDisabled();
  await expect(sendButton).toHaveAttribute("title", readonlyReason);
  await composer.fill("/clear");
  await expect(sendButton).toBeEnabled();
});

test("workbench disables chat until opencode process is initialized", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { processStatus: "NEEDS_INITIALIZATION", processInitializations });

  await gotoWorkbench(page, { selectConversation: false });

  await expect(page.getByText("我还没有准备好运行进程，要现在帮你初始化吗？")).toBeVisible();
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await page.getByRole("button", { name: "初始化进程" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  await expect(page.getByText("TestAgent 进程可用").first()).toBeVisible();
  await page.getByRole("button", { name: "新建对话" }).click();
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("run after init");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
});

test("workbench restarts a stopped opencode process directly from the activity pet entry", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    processStatus: "NEEDS_INITIALIZATION",
    processServiceStatus: "NOT_RUNNING",
    processInitializations
  });

  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "启动 TestAgent 进程并唤起小宠物" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  await expect(page.getByTestId("figma-robot")).toBeVisible();
  await expect(page.getByTestId("robot-process-status")).toHaveCount(0);
  await expect(page.getByTestId("robot-side-question")).toHaveCount(0);
  await expect(page.getByTestId("robot-visibility-toggle")).toHaveAttribute("aria-label", "收起小宠物");
});

test("workbench refetches opencode status when initialize returns a stale failure", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  const processStatusRequests: string[] = [];
  await mockBackendApi(page, {
    processStatus: "NEEDS_INITIALIZATION",
    processInitializations,
    processStatusRequests,
    initializeFailureThenReady: true
  });

  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "初始化进程" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  await expect.poll(() => processStatusRequests.length).toBeGreaterThanOrEqual(2);
  await expect(page.getByText("TestAgent 进程可用").first()).toBeVisible();
  await expect(page.getByText("初始化 TestAgent 进程失败")).toHaveCount(0);
});

test("workbench does not create default personal workspace while opencode becomes ready", async ({ page }) => {
  const fileRequests: Array<{ workspaceId: string; path: string }> = [];
  const defaultPersonalRequests: string[] = [];
  const personalWorkspaceRequests: string[] = [];
  const processInitializations: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    processStatus: "NEEDS_INITIALIZATION",
    ensureDefaultRequiresReady: true,
    fileRequests,
    defaultPersonalRequests,
    personalWorkspaceRequests,
    processInitializations,
    personalWorkspaces: {
      awv_20260715: []
    },
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        workspaceId: "wrk_app_replica",
        name: "F-GCMS 报表 / 20260715",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms"
      }
    }
  });

  await gotoWorkbench(page, { selectConversation: false });
  await expect.poll(() => personalWorkspaceRequests).toEqual(["awv_20260715"]);
  expect(defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();

  await page.getByRole("button", { name: "初始化进程" }).click();

  await expect.poll(() => processInitializations.length).toBe(1);
  expect(defaultPersonalRequests).toEqual([]);
  expect(fileRequests).toEqual([]);
  await expect(page.getByText("当前应用尚未切换到可用工作区。")).toBeVisible();
});

test("version selection prompts for process initialization before creating a personal workspace", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  const gitAccessRequests: string[] = [];
  const defaultPersonalRequests: string[] = [];
  await mockBackendApi(page, {
    processStatus: "NEEDS_INITIALIZATION",
    processInitializations,
    gitAccessRequests,
    defaultPersonalRequests,
    ...versionSelectionWorkspaceSetup()
  });

  await gotoWorkbench(page, { selectConversation: false });
  const chooseVersion = async () => {
    await page.locator(".ta-workbench-footer-branch").click();
    await page.getByRole("menuitem", { name: /F-GCMS 主服务/ }).hover();
    await page.getByRole("menuitem", { name: /2024年1月/ }).first().click();
  };

  await chooseVersion();
  const prompt = page.locator(".el-message-box");
  await expect(prompt).toBeVisible();
  await expect(prompt.getByText("请先初始化 TestAgent 进程")).toBeVisible();
  await expect(prompt).toContainText("切换应用版本前需要先初始化 TestAgent 专属进程");
  expect(gitAccessRequests).toEqual([]);
  expect(defaultPersonalRequests).toEqual([]);

  await prompt.getByRole("button", { name: "初始化进程" }).click();
  await expect.poll(() => processInitializations.length).toBe(1);
  expect(gitAccessRequests).toEqual([]);
  expect(defaultPersonalRequests).toEqual([]);
  await expect(page.getByText("TestAgent 进程可用").first()).toBeVisible();

  await chooseVersion();
  await expect.poll(() => gitAccessRequests).toEqual(["awv_2024_01"]);
  await expect.poll(() => defaultPersonalRequests).toEqual(["awv_2024_01"]);
});

test("version selection does not offer initialization when no process can be initialized", async ({ page }) => {
  const processInitializations: Array<Record<string, unknown>> = [];
  const gitAccessRequests: string[] = [];
  const defaultPersonalRequests: string[] = [];
  await mockBackendApi(page, {
    processStatus: "UNAVAILABLE",
    processInitializations,
    gitAccessRequests,
    defaultPersonalRequests,
    ...versionSelectionWorkspaceSetup()
  });

  await gotoWorkbench(page, { selectConversation: false });
  await page.locator(".ta-workbench-footer-branch").click();
  await page.getByRole("menuitem", { name: /F-GCMS 主服务/ }).hover();
  await page.getByRole("menuitem", { name: /2024年1月/ }).first().click();

  const alert = page.locator(".el-message-box");
  await expect(alert).toBeVisible();
  await expect(alert.getByText("TestAgent 进程当前不可用")).toBeVisible();
  await expect(alert).toContainText("没有可用的 TestAgent 容器");
  await expect(alert.getByRole("button", { name: "初始化进程" })).toHaveCount(0);
  expect(processInitializations).toEqual([]);
  expect(gitAccessRequests).toEqual([]);
  expect(defaultPersonalRequests).toEqual([]);
});

test("workbench accepts the first prompt without requiring new conversation while pet manual help remains available", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await mockBackendApi(page, { runRequests, ...runnableWorkspaceSetup(), authRoles: ["APP_ADMIN"] });

  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click();
  await page.getByTestId("figma-robot").click();
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
  await expect(page.getByTestId("robot-side-question-input")).toBeEnabled();
  await expect(page.getByRole("button", { name: "打开宠物小游戏" })).toHaveCount(0);

  const composer = page.locator(".figma-chat-input-card");
  const textarea = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await expect(composer).not.toHaveClass(/is-disabled/);
  await expect(textarea).toBeEnabled();
  await expect(page.getByRole("button", { name: "发送" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "新建对话" })).toBeEnabled();

  await textarea.fill("直接开始第一轮测试");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]?.prompt).toBe("直接开始第一轮测试");
});

test("pet mini games are hidden from non-super administrators", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), authRoles: ["APP_ADMIN"] });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click();
  await page.getByTestId("figma-robot").click();
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
  await expect(page.getByRole("button", { name: "打开宠物小游戏" })).toHaveCount(0);
  await expect(page.getByTestId("pet-mini-games")).toHaveCount(0);
});

test("pet drag continues after the pointer leaves the robot hit area", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), authRoles: ["SUPER_ADMIN"] });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click({ force: true });
  const robot = page.getByTestId("figma-robot");
  await expect(robot).toBeVisible();
  await page.evaluate(() => {
    // 模拟 Monaco 等工作台子组件拦截 Pointer Events，验证 window 捕获监听仍能完成拖动。
    document.addEventListener("pointermove", (event) => event.stopPropagation());
    document.addEventListener("pointerup", (event) => event.stopPropagation());
  });
  const box = await robot.boundingBox();
  expect(box).not.toBeNull();
  const start = await robot.evaluate((element) => ({
    x: Number.parseFloat((element as HTMLElement).style.left),
    y: Number.parseFloat((element as HTMLElement).style.top)
  }));

  await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
  await page.mouse.down();
  await page.mouse.move(box!.x + box!.width / 2 + 100, box!.y + box!.height / 2 + 80, { steps: 3 });
  await page.mouse.up();

  await expect.poll(async () => robot.evaluate((element) => Number.parseFloat((element as HTMLElement).style.left))).toBeGreaterThan(start.x);
  await expect.poll(async () => robot.evaluate((element) => Number.parseFloat((element as HTMLElement).style.top))).toBeGreaterThan(start.y);
});

test("pet mini games support tetris, minesweeper, sudoku and snake interactions", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("test-agent.onboarding.v2:usr_admin", "seen");
  });
  await page.addInitScript(() => {
    Math.random = () => 0;
  });
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), authRoles: ["SUPER_ADMIN"] });
  await gotoWorkbench(page, { selectConversation: false });

  await page.getByRole("button", { name: "唤起小宠物" }).click();
  await page.getByTestId("figma-robot").click();
  await page.getByRole("button", { name: "打开宠物小游戏" }).click();
  await expect(page.getByTestId("pet-mini-games")).toBeVisible();
  await expect(page.getByTestId("figma-robot")).toBeVisible();

  await page.getByTestId("pet-game-open-tetris").click();
  await expect(page.getByTestId("pet-tetris").locator(".pet-tetris-cell")).toHaveCount(160);
  await page.getByRole("button", { name: "右移" }).click();
  await page.getByRole("button", { name: "旋转" }).click();
  await page.getByRole("button", { name: "直接落下" }).click();
  await expect(page.getByTestId("pet-tetris")).toContainText("分数");

  await page.getByRole("button", { name: "扫雷", exact: true }).click();
  const mineCells = page.getByTestId("pet-minesweeper").locator(".pet-mine-cell");
  await expect(mineCells).toHaveCount(64);
  await mineCells.first().click();
  await expect(page.getByTestId("pet-minesweeper")).not.toContainText("踩雷了");
  await expect(mineCells.nth(2)).not.toHaveClass(/is-revealed/);
  await mineCells.nth(1).dblclick();
  await expect(mineCells.nth(2)).not.toHaveClass(/is-revealed/);
  await mineCells.nth(10).click({ button: "right" });
  await expect(mineCells.nth(10)).toHaveAttribute("aria-label", /已插旗/);
  await mineCells.nth(1).dblclick();
  await expect(mineCells.nth(2)).toHaveClass(/is-revealed/);

  await page.getByRole("button", { name: "数独", exact: true }).click();
  const sudokuCells = page.getByTestId("pet-sudoku").locator(".pet-sudoku-cell");
  await expect(sudokuCells).toHaveCount(81);
  await sudokuCells.nth(2).click();
  await page.getByRole("button", { name: "填写数字 4" }).click();
  await expect(sudokuCells.nth(2)).toHaveText("4");
  await expect(sudokuCells.nth(2)).not.toHaveClass(/is-error/);

  await page.getByRole("button", { name: "贪吃蛇", exact: true }).click();
  await expect(page.getByTestId("pet-snake").locator(".pet-snake-cell")).toHaveCount(144);
  await page.getByRole("button", { name: "贪吃蛇向上" }).click();
  await page.getByTestId("pet-snake").getByRole("button", { name: "暂停" }).click();
  await expect(page.getByTestId("pet-snake")).toContainText("已暂停");

  await page.getByRole("button", { name: "关闭宠物旁路问答" }).click();
  await expect(page.getByTestId("pet-mini-games")).toHaveCount(0);
});

test("phase 11 runtime flow sends attachment parts and handles docks", async ({ page, isMobile }) => {
  test.skip(isMobile, "当前项目没有移动端产品内容，底部终端 dock 只在桌面视口覆盖。");
  const runRequests: Array<Record<string, unknown>> = [];
  const permissionReplies: Array<Record<string, unknown>> = [];
  const questionReplies: Array<Record<string, unknown>> = [];
  const terminalTickets: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), runRequests, permissionReplies, questionReplies, terminalTickets });

  await gotoWorkbench(page);
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("analyze checkout");
  await page.getByRole("button", { name: "上传附件" }).click();
  await expect(page.getByRole("dialog", { name: "上传附件" })).toBeVisible();
  await page.getByTestId("chat-attachment-input").setInputFiles({
    name: "notes.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("checkout failure log")
  });
  await expect(page.getByTestId("chat-uploaded-attachments").getByText("notes.txt")).toBeVisible();
  // 聊天附件先落到工作区，等上传状态切换为可随任务提交后再发送。
  await expect(page.getByText("发送时一并交给智能体")).toBeVisible();
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    prompt: "analyze checkout",
    parts: expect.arrayContaining([
      { type: "text", text: "analyze checkout" },
      {
        type: "file",
        name: "notes.txt",
        mimeType: "text/plain",
        path: expect.stringMatching(/^\.testagent\/attachments\/sha256_[a-f0-9]+\.txt$/),
        source: { contextType: "workspace_attachment", deliveryMode: "native" }
      }
    ])
  });

  await expect(page.getByText("Run bash")).toBeVisible();
  await page.getByRole("button", { name: "允许一次" }).click();
  await expect.poll(() => permissionReplies.length).toBe(1);
  expect(permissionReplies[0]).toEqual({ decision: "once" });

  await expect(page.getByText("Need target env?")).toBeVisible();
  await page.getByPlaceholder("输入你的答案...").fill("staging");
  await page.getByRole("button", { name: "提交" }).click();
  await expect.poll(() => questionReplies.length).toBe(1);
  expect(questionReplies[0]).toEqual({ answers: [["staging"]] });

  const diffSummary = page.getByTestId("oc-diff-summary");
  await expect(diffSummary).toBeVisible();
  await expect(diffSummary.getByRole("button", { name: "文件修改 1 文件总增减行" })).toBeVisible();
  await diffSummary.getByRole("button", { name: "文件修改 1 文件总增减行" }).click();
  const diffFileRow = diffSummary.locator(".oc-diff-file");
  await expect(diffFileRow).toContainText("App.tsx");
  await expect(diffFileRow).toContainText("+2");
  await expect(diffFileRow).toContainText("-1");

  const bottomDrawer = page.getByRole("region", { name: "运行与终端" });
  await expect(bottomDrawer).toBeVisible();
  const openBottomDrawerButton = page.getByRole("button", { name: "打开运行与终端" });
  if (await openBottomDrawerButton.count()) {
    await openBottomDrawerButton.click();
  }
  await expect.poll(async () => (await bottomDrawer.boundingBox())?.y ?? Number.POSITIVE_INFINITY).toBeLessThan(766);
  // 底部 dock 可能与工作区 footer 发生视觉覆盖，直接派发标签点击仍走当前 UI 的状态处理。
  await page.getByRole("button", { name: "终端", exact: true }).dispatchEvent("click");
  await page.getByRole("button", { name: "连接终端" }).dispatchEvent("click");
  await expect.poll(() => terminalTickets.length).toBe(1);
  expect(terminalTickets[0]).toEqual({ workspaceId: "wrk_personal_default", cols: 120, rows: 32 });
});

test("slash skill starts a recoverable run instead of a direct session command", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const commandRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, { ...runnableWorkspaceSetup(), runRequests, commandRequests });

  await gotoWorkbench(page);
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();

  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因")
    .fill("/test-design-path 对车贷的开发文档，生成路径图");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  expect(runRequests[0]).toMatchObject({
    sessionId: "ses_1",
    prompt: "/test-design-path 对车贷的开发文档，生成路径图",
    command: "test-design-path",
    arguments: "对车贷的开发文档，生成路径图"
  });
  expect(commandRequests).toEqual([]);
});

test("enterprise native slash commands open models, compact context, and rename the session", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  const compactRequests: Array<Record<string, unknown>> = [];
  const sessionUpdateRequests: Array<{ sessionId: string; payload: Record<string, unknown> }> = [];
  let releaseCompact!: () => void;
  const compactRequestGate = new Promise<void>((resolve) => {
    releaseCompact = resolve;
  });
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    compactRequests,
    compactRequestGate,
    sessionUpdateRequests,
    runEvents: [event(1, "run.succeeded", {})]
  });

  await gotoWorkbench(page);
  const textarea = page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因");
  await textarea.fill("建立原生命令测试会话");
  await page.getByRole("button", { name: "发送" }).click();
  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.getByRole("button", { name: "发送" })).toBeVisible();

  await textarea.fill("/");
  await expect(page.getByTestId("slash-native-section").locator(".figma-chat-skill-name")).toHaveText([
    "/sessions",
    "/new",
    "/models",
    "/compact",
    "/rename"
  ]);

  await textarea.fill("/models");
  await page.getByTestId("slash-native-section").locator(".figma-chat-skill-row", { hasText: "/models" }).click();
  await expect(page.getByRole("dialog", { name: "模型选择" })).toBeVisible();
  await page.getByRole("button", { name: "切换模型" }).click();

  await textarea.fill("/compact");
  await page.getByTestId("slash-native-section").locator(".figma-chat-skill-row", { hasText: "/compact" }).click();
  await expect.poll(() => compactRequests.length).toBe(1);
  expect(compactRequests[0]).toEqual({ providerID: "anthropic", modelID: "sonnet" });
  const compactProgress = page.getByTestId("compact-progress");
  await expect(compactProgress).toHaveAttribute("data-phase", "running");
  await expect(compactProgress.getByText("正在压缩上下文", { exact: true })).toBeVisible();
  await expect(compactProgress.locator(".figma-chat-compact-progress-line")).toHaveCount(3);
  expect(await compactProgress.locator(".figma-chat-compact-progress-line").first()
    .evaluate((element) => getComputedStyle(element).animationName))
    .toContain("figma-chat-compact-fold");

  releaseCompact();
  await expect(compactProgress).toHaveAttribute("data-phase", "success");
  await expect(compactProgress.getByText("上下文压缩完成", { exact: true })).toBeVisible();
  await expect(page.getByText("上下文已压缩", { exact: true })).toBeVisible();

  await textarea.fill("/rename");
  await page.getByTestId("slash-native-section").locator(".figma-chat-skill-row", { hasText: "/rename" }).click();
  const renameDialog = page.locator(".el-message-box", { hasText: "重命名会话" });
  await expect(renameDialog).toBeVisible();
  await renameDialog.locator("input").fill("企业原生命令会话");
  await renameDialog.getByRole("button", { name: "保存" }).click();

  await expect.poll(() => sessionUpdateRequests).toEqual([{
    sessionId: "ses_1",
    payload: { title: "企业原生命令会话" }
  }]);
  await expect(page.getByText("会话已重命名", { exact: true })).toBeVisible();
});

test("completed write events refresh the changed file without a separate live toggle", async ({ page }) => {
  const runRequests: Array<Record<string, unknown>> = [];
  await mockBackendApi(page, {
    ...runnableWorkspaceSetup(),
    runRequests,
    runEvents: [
      event(1, "message.part.updated", {
        messageID: "msg_1",
        part: {
          id: "part_write",
          messageID: "msg_1",
          type: "tool",
          tool: "write",
          state: {
            status: "completed",
            input: { filePath: "/Users/huang/workspace/demo-tests/tests/checkout.spec.ts" },
            metadata: { filepath: "/Users/huang/workspace/demo-tests/tests/checkout.spec.ts" }
          }
        }
      }),
      event(2, "diff.proposed", {
        source: "tool",
        tool: "write",
        messageID: "msg_1",
        partID: "part_write",
        files: [
          {
            path: "/Users/huang/workspace/demo-tests/tests/checkout.spec.ts",
            patch: "@@ -1 +1,3 @@",
            additions: 3,
            deletions: 1,
            status: "modified"
          }
        ]
      })
    ],
    fileContents: {
      "tests/checkout.spec.ts": "import { test } from '@playwright/test';\n\n// live tracking content\n"
    }
  });

  await gotoWorkbench(page);
  await expect(page.locator(".ta-workbench-footer-branch")).toBeVisible();

  const liveButton = page.getByRole("button", { name: "实时" });
  await expect(liveButton).toHaveCount(0);
  await page.getByPlaceholder("描述测试任务，例如：跑 checkout 模块并分析失败原因").fill("change checkout");
  await expect(page.getByRole("button", { name: "发送" })).toBeEnabled();
  await page.getByRole("button", { name: "发送" }).click();

  await expect.poll(() => runRequests.length).toBe(1);
  await expect(page.getByRole("button", { name: "文件修改 1 文件总增减行" })).toBeVisible();
});

test("workspace cascade menu teleports panel and submenu above all other UI", async ({ page }) => {
  // 模拟后端返回两个工作空间模板，每个模板下两个版本。
  // 验证：
  // 1) 一级菜单 Teleport 到 body + position:fixed，不被 dockview 面板的 overflow:hidden 裁切；
  // 2) 一级菜单 z-index 高于父级；
  // 3) hover 一级菜单项后，二级菜单 Teleport 到 body 出现在右侧；
  // 4) 菜单没有横向滚动条（max-width 不会越界）。
  await mockBackendApi(page, {
    workspaceTemplates: {
      app_gcms: [
        {
          workspaceId: "awp_main",
          workspaceName: "F-GCMS 主服务",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        },
        {
          workspaceId: "awp_v1",
          workspaceName: "F-GCMS v1 灰度",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    },
    workspaceVersions: {
      "app_gcms:awp_main": [
        {
          versionId: "awv_2024_01",
          applicationWorkspaceId: "awp_main",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: "2024年1月",
          branch: "feature_testagent_2024-01",
          repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
          status: "ACTIVE",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        },
        {
          versionId: "awv_2024_06",
          applicationWorkspaceId: "awp_main",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: "2024年6月",
          branch: "feature_testagent_2024-06",
          repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
          status: "ACTIVE",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    }
  });

  await gotoWorkbench(page);

  // 触发按钮：label 是 "F-GCMS 工作空间"（无 selected version）
  const trigger = page.locator(".ta-workbench-footer-branch");
  await expect(trigger).toBeVisible();
  await trigger.click();

  // 一级菜单面板：Teleport 到 body，position:fixed，有真实宽度
  const panel = page.locator(".ta-workbench-cascade-panel");
  await expect(panel).toBeVisible();
  const panelBox = await panel.boundingBox();
  expect(panelBox).not.toBeNull();
  expect(panelBox!.width).toBeGreaterThan(0);
  expect(panelBox!.height).toBeGreaterThan(0);
  // 一级菜单 y 小于按钮 y（菜单在按钮正上方）；这个特性是用户反馈"最上面"的关键。
  const buttonBox = await trigger.boundingBox();
  expect(buttonBox).not.toBeNull();
  expect(panelBox!.y).toBeLessThan(buttonBox!.y);

  // 没有横向滚动条：scrollWidth 应等于 clientWidth
  const panelScroll = await panel.evaluate((el) => ({
    scrollWidth: (el as HTMLElement).scrollWidth,
    clientWidth: (el as HTMLElement).clientWidth
  }));
  expect(panelScroll.scrollWidth).toBeLessThanOrEqual(panelScroll.clientWidth);

  // hover 一级菜单项 → 二级菜单 Teleport 到 body 出现在一级菜单右侧
  const firstItem = page.getByRole("menuitem", { name: /F-GCMS 主服务/ });
  await firstItem.hover();
  const submenu = page.locator(".ta-workbench-cascade-submenu");
  await expect(submenu).toBeVisible();
  const submenuBox = await submenu.boundingBox();
  expect(submenuBox).not.toBeNull();
  expect(submenuBox!.width).toBeGreaterThan(0);
  // 二级菜单 left 应当 >= 一级菜单的 right（出现在右侧）
  expect(submenuBox!.x).toBeGreaterThanOrEqual(panelBox!.x + panelBox!.width - 1);

  // 二级菜单里展示版本（Teleport 后依然可被 role=menuitem 检索到）
  await expect(page.getByRole("menuitem", { name: /2024年1月/ }).first()).toBeVisible();
  await expect(page.getByRole("menuitem", { name: /2024年6月/ }).first()).toBeVisible();
});

test("workspace cascade menu +新增版本 dialog opens with yyyy年M月 label", async ({ page }) => {
  await mockBackendApi(page, {
    workspaceTemplates: {
      app_gcms: [
        {
          workspaceId: "awp_main",
          workspaceName: "F-GCMS 主服务",
          appId: "app_gcms",
          repositoryId: "repo_1",
          defaultBranch: "main",
          createdAt: "2026-06-24T00:00:00Z",
          updatedAt: "2026-06-24T00:00:00Z"
        }
      ]
    },
    workspaceVersions: { "app_gcms:awp_main": [] }
  });

  await gotoWorkbench(page);

  // 打开一级菜单 → hover 模板 → 出现二级菜单
  const trigger = page.locator(".ta-workbench-footer-branch");
  await trigger.click();
  const firstItem = page.getByRole("menuitem", { name: /F-GCMS 主服务/ });
  await firstItem.hover();
  const submenu = page.locator(".ta-workbench-cascade-submenu");
  await expect(submenu).toBeVisible();

  // 点「+新增版本」打开 el-dialog
  await page.getByRole("menuitem", { name: /新增版本/ }).first().click();
  const dialog = page.locator(".el-dialog");
  await expect(dialog).toBeVisible();
  // 弹窗内标签明确告诉用户格式是 yyyy年M月
  await expect(dialog.getByText("选择日期（格式 yyyyMMdd）")).toBeVisible();
  await expect(dialog.locator(".el-date-editor input")).toHaveAttribute("placeholder", "请选择日期");
  // 没选日期时确定按钮处于 disabled
  await expect(dialog.getByRole("button", { name: "确定" })).toBeDisabled();
});

test("workspace cascade submenu shifts up when it would overflow the viewport bottom", async ({ page, isMobile }) => {
  test.skip(isMobile, "viewport math is desktop-specific in this mock");
  // 构造一个触发 li 接近视口底部的场景：模板多到面板能填满视口。
  const manyTemplates = Array.from({ length: 20 }).map((_, idx) => ({
    workspaceId: `awp_${idx}`,
    workspaceName: `F-COSS 模板 ${idx}`,
    appId: "app_gcms",
    repositoryId: "repo_1",
    defaultBranch: "main",
    createdAt: "2026-06-24T00:00:00Z",
    updatedAt: "2026-06-24T00:00:00Z"
  }));
  await mockBackendApi(page, {
    workspaceTemplates: { app_gcms: manyTemplates },
    workspaceVersions: {
      "app_gcms:awp_19": Array.from({ length: 15 }).map((_, idx) => ({
        versionId: `awv_${idx}`,
        applicationWorkspaceId: "awp_19",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: `2024年${idx + 1}月`,
        branch: `feature_testagent_2024-${String(idx + 1).padStart(2, "0")}`,
        repoRootPath: "/tmp/test-agent/appworkspace/awp_19/repo_1",
        workspaceRootPath: "/tmp/test-agent/appworkspace/awp_19/repo_1/F-COSS/workspace",
        status: "ACTIVE",
        createdAt: "2026-06-24T00:00:00Z",
        updatedAt: "2026-06-24T00:00:00Z"
      }))
    }
  });

  await gotoWorkbench(page);
  const trigger = page.locator(".ta-workbench-footer-branch");
  await trigger.click();

  // hover 最后一个 li（最接近视口底部），让子菜单自然位置会溢出
  const lastItem = page.getByRole("menuitem", { name: /F-COSS 模板 19/ });
  await lastItem.hover();

  const submenu = page.locator(".ta-workbench-cascade-submenu");
  await expect(submenu).toBeVisible();
  // 等一帧让 Vue 完成 reactive 周期
  await page.waitForTimeout(200);
  const submenuBox = await submenu.boundingBox();
  const viewport = page.viewportSize();
  expect(submenuBox).not.toBeNull();
  expect(viewport).not.toBeNull();
  // 子菜单底部必须 <= 视口高度（不能被底部遮挡）
  expect(submenuBox!.y + submenuBox!.height).toBeLessThanOrEqual(viewport!.height);
});

type RunEventFetchBatch = {
  delayMs?: number;
  releaseKey?: string;
  events: Array<{ eventId?: string; seq: number; type: string; payload: Record<string, unknown> }>;
};

/** 认证后的主 RunEvent 客户端走 fetch SSE；同一 batch 用于复现 durable 终态同步重放。 */
async function installAuthenticatedRunEventFetchStream(
  page: Page,
  scenarios: Record<string, RunEventFetchBatch[]>
) {
  await page.addInitScript(({ scenarios }) => {
    type StreamProbe = {
      runId: string;
      authorization: string | null;
      closed: boolean;
      emittedEventIds: string[];
    };
    const probes: StreamProbe[] = [];
    const manualReleases = new Map<string, () => void>();
    const testWindow = window as Window & {
      __titleWatchRunStreams?: StreamProbe[];
      __releaseRunEventBatch?: (releaseKey: string) => boolean;
    };
    testWindow.__titleWatchRunStreams = probes;
    testWindow.__releaseRunEventBatch = (releaseKey) => {
      const release = manualReleases.get(releaseKey);
      if (!release) return false;
      manualReleases.delete(releaseKey);
      release();
      return true;
    };
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init) => {
      const request = new Request(input, init);
      const requestUrl = new URL(request.url, window.location.origin);
      const runId = decodeURIComponent(requestUrl.pathname)
        .match(/^\/api\/internal\/agent\/opencode\/runs\/([^/]+)\/events$/)?.[1];
      if (!runId) {
        return nativeFetch(input, init);
      }
      const probe: StreamProbe = {
        runId,
        authorization: request.headers.get("authorization"),
        closed: false,
        emittedEventIds: []
      };
      probes.push(probe);
      const encoder = new TextEncoder();
      let controller: ReadableStreamDefaultController<Uint8Array> | undefined;
      let timers: number[] = [];
      let releaseKeys: string[] = [];
      const closeProbe = () => {
        if (probe.closed) return;
        probe.closed = true;
        timers.forEach((timer) => window.clearTimeout(timer));
        timers = [];
        releaseKeys.forEach((releaseKey) => manualReleases.delete(releaseKey));
        releaseKeys = [];
      };
      const body = new ReadableStream<Uint8Array>({
        start(streamController) {
          controller = streamController;
          for (const batch of scenarios[runId] ?? []) {
            // 双用户同步用例需要精确控制空 envelope 与平台正文的先后，避免靠浏览器定时器猜测。
            const emitBatch = () => {
              if (probe.closed) return;
              probe.emittedEventIds.push(...batch.events.map((item) => item.eventId ?? `evt_title_${runId}_${item.seq}`));
              const frame = batch.events.map((item) => (
                `id: ${item.eventId ?? `evt_title_${runId}_${item.seq}`}\nevent: ${item.type}\ndata: ${JSON.stringify({
                  eventId: item.eventId ?? `evt_title_${runId}_${item.seq}`,
                  runId,
                  seq: item.seq,
                  type: item.type,
                  traceId: "trace_e2e",
                  occurredAt: "2026-07-17T08:00:00Z",
                  payload: item.payload
                })}\n\n`
              )).join("");
              streamController.enqueue(encoder.encode(frame));
            };
            if (batch.releaseKey) {
              manualReleases.set(batch.releaseKey, emitBatch);
              releaseKeys.push(batch.releaseKey);
            } else {
              timers.push(window.setTimeout(emitBatch, batch.delayMs ?? 0));
            }
          }
        },
        cancel() {
          closeProbe();
        }
      });
      request.signal.addEventListener("abort", () => {
        closeProbe();
        try {
          controller?.error(new DOMException("RunEvent stream aborted", "AbortError"));
        } catch {
          // reader 已结束时无需重复关闭。
        }
      }, { once: true });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
  }, { scenarios });
}

type PetStreamEvent = {
  eventId: string;
  type: string;
  payload: Record<string, unknown>;
  delayMs: number;
  disconnectAfter?: boolean;
};

function streamEvent(
  eventId: string,
  type: string,
  payload: Record<string, unknown>,
  delayMs: number,
  disconnectAfter = false
): PetStreamEvent {
  return { eventId, type, payload, delayMs, disconnectAfter };
}

async function installPetSideQuestionRunEventStream(page: Page, scenarios: Record<string, PetStreamEvent[]>) {
  await page.addInitScript(({ scenarios }) => {
    const reconnects: Array<{ runId: string; lastEventId: string }> = [];
    (window as Window & { __petSideQuestionReconnects?: typeof reconnects }).__petSideQuestionReconnects = reconnects;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = async (input, init) => {
      const requestUrl = new URL(
        typeof input === "string" ? input : input instanceof Request ? input.url : input.toString(),
        window.location.origin
      );
      if (!requestUrl.pathname.includes("/runs/") || !requestUrl.pathname.endsWith("/events")) {
        return nativeFetch(input, init);
      }
      const runId = decodeURIComponent(requestUrl.pathname).match(/\/runs\/([^/]+)\/events/)?.[1] ?? "run_1";
      const events = scenarios[runId] ?? (runId === "run_1"
        ? [{ eventId: "evt_main_terminal", type: "run.succeeded", payload: {}, delayMs: 10 }]
        : []);
      const lastEventId = requestUrl.searchParams.get("lastEventId") ?? "";
      const resumeIndex = lastEventId ? events.findIndex((item) => item.eventId === lastEventId) + 1 : 0;
      if (lastEventId) {
        reconnects.push({ runId, lastEventId });
      }
      const selected = events.slice(Math.max(0, resumeIndex));
      const disconnectIndex = lastEventId ? -1 : selected.findIndex((item) => item.disconnectAfter);
      const batch = disconnectIndex < 0 ? selected : selected.slice(0, disconnectIndex + 1);
      const encoder = new TextEncoder();
      let timers: number[] = [];
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          batch.forEach((item, index) => {
            timers.push(window.setTimeout(() => {
              controller.enqueue(encoder.encode(
                `id: ${item.eventId}\nevent: ${item.type}\ndata: ${JSON.stringify({
                  eventId: item.eventId,
                  runId,
                  seq: resumeIndex + index + 1,
                  type: item.type,
                  traceId: "trace_pet_side_question",
                  occurredAt: "2026-07-11T00:00:00Z",
                  payload: item.payload
                })}\n\n`
              ));
              if (item.disconnectAfter) controller.close();
            }, item.delayMs));
          });
        },
        cancel() {
          timers.forEach((timer) => window.clearTimeout(timer));
          timers = [];
        }
      });
      return new Response(body, { headers: { "content-type": "text/event-stream" } });
    };
  }, { scenarios });
}

async function selectPetContextSession(page: Page) {
  // 顶部工作区/版本选择器采用绝对定位；在慢速初始化期间可能暂时压到聊天按钮的命中区域，仍调用同一按钮事件完成会话列表打开。
  await page.getByRole("button", { name: "会话列表" }).click({ force: true });
  await historySessionButton(page, "E2E Session").click();
  await expect(page.locator(".figma-chat-title")).toHaveText("E2E Session");
  await page.getByRole("button", { name: "关闭会话列表抽屉" }).click();
}

async function openPetSideQuestion(page: Page) {
  await page.getByTestId("robot-visibility-toggle").click();
  await page.getByTestId("figma-robot").click();
  await expect(page.getByTestId("robot-side-question")).toBeVisible();
}

async function mockBackendApi(
  page: Page,
  capture: {
    runRequests?: Array<Record<string, unknown>>;
    cancelRunRequests?: string[];
    commandRequests?: Array<Record<string, unknown>>;
    compactRequests?: Array<Record<string, unknown>>;
    compactRequestGate?: Promise<void>;
    sessionUpdateRequests?: Array<{ sessionId: string; payload: Record<string, unknown> }>;
    sessionRequests?: Array<Record<string, unknown>>;
    batchSessionRequests?: Array<Record<string, unknown>>;
    runResendRequests?: Array<Record<string, unknown>>;
    permissionReplies?: Array<Record<string, unknown>>;
    questionReplies?: Array<Record<string, unknown>>;
    terminalTickets?: Array<Record<string, unknown>>;
    fileRequests?: Array<{ workspaceId: string; path: string }>;
    fileReadRequests?: Array<{ workspaceId: string; path: string; attempt: number }>;
    fileReadDelays?: Record<string, number[]>;
    fileReadFailuresBeforeSuccess?: Record<string, number>;
    fileReadFailureAttempts?: Record<string, number[]>;
    fileReadNotFoundAttempts?: Record<string, number[]>;
    fileReadResponses?: Record<string, string[]>;
    workspaceMutationDelays?: Record<string, number>;
    fileWriteRequests?: Array<{ workspaceId: string; path: string; content: string }>;
    workspaceMoveRequests?: Array<{ workspaceId: string; sourcePath: string; targetPath: string }>;
    /** 组合工作区视图响应以 `kind:alias:path` 为键；未配置时自动映射普通工作区目录。 */
    workspaceViewLists?: Record<string, {
      entries: Array<Record<string, unknown>>;
      warnings?: Array<{ alias?: string; code: string; message: string }>;
      truncated?: boolean;
    }>;
    /** 每次 workspace.move 成功后替换组合树响应，用于覆盖稳定 ID 变化和反向撤销。 */
    workspaceViewListsAfterMoves?: Array<Record<string, {
      entries: Array<Record<string, unknown>>;
      warnings?: Array<{ alias?: string; code: string; message: string }>;
      truncated?: boolean;
    }>>;
    /** 引用文件正文以 `kind:alias:path` 为键。 */
    workspaceViewContents?: Record<string, string>;
    agentFileFrames?: Array<{
      op: string;
      scope: string;
      path: string;
      workspaceId?: string;
      worktreeId?: string;
      attempt?: number;
      content?: string;
    }>;
    /** Agent 文件配置以 `PUBLIC:path` / `WORKSPACE:path` 为键。 */
    agentFileContents?: Record<string, string>;
    agentFileReadDelays?: Record<string, number[]>;
    agentFileReadFailureAttempts?: Record<string, number[]>;
    agentFileReadNotFoundAttempts?: Record<string, number[]>;
    agentFileReadResponses?: Record<string, string[]>;
    gitDiffRequests?: string[];
    workspaces?: Array<ReturnType<typeof workspace> & Record<string, unknown>>;
    workspaceRequests?: string[];
    workspaceRequestGates?: Record<string, Promise<void>>;
    runEvents?: Array<ReturnType<typeof event>>;
    runIds?: string[];
    runEventsByRunId?: Record<string, Array<ReturnType<typeof event>>>;
    fileContents?: Record<string, string>;
    authRoles?: string[];
    authMeGate?: Promise<void>;
    logoutRequests?: string[];
    configurationApplicationRequests?: string[];
    agentRequests?: string[];
    agents?: Array<Record<string, unknown>>;
    agentResponses?: Array<Array<Record<string, unknown>> | { status?: number; code: string; message: string; details?: Record<string, unknown> }>;
    agentsByWorkspace?: Record<string, Array<Record<string, unknown>>>;
    agentGatesByWorkspace?: Record<string, Promise<void>>;
    models?: Array<Record<string, unknown>>;
    modelResponses?: Array<Array<Record<string, unknown>>>;
    providers?: Array<Record<string, unknown>>;
    runtimeCatalogRequests?: Array<{ path: string; workspaceId: string | null; shareId: string | null }>;
    applications?: Array<{ appId: string; appName: string; enabled: boolean }>;
    managedApplications?: Array<{ appId: string; appName: string; enabled: boolean }>;
    recentWorkspaces?: Record<string, (ReturnType<typeof workspace> & Record<string, unknown>) | null>;
    forbiddenRecentWorkspaces?: Record<string, { code: string; message: string; details?: Record<string, unknown>; status?: number }>;
    markRecentRequests?: string[];
    markRecentWorkspaces?: Record<string, ReturnType<typeof workspace> & Record<string, unknown>>;
    markRecentFailures?: Record<string, { code: string; message: string; details?: Record<string, unknown>; status?: number }>;
    personalWorkspaces?: Record<string, Array<Record<string, unknown>>>;
    personalWorkspaceRequests?: string[];
    /** 自定义 /vcs/status 返回，覆盖默认的 { status: "ready", branch: "main", defaultBranch: "main" }。 */
    vcsStatus?: { status?: string; branch?: string; defaultBranch?: string };
    /** 收集「+新增版本」发出的 POST workspace-templates/{id}/versions 请求的 version 字段（用户原值）。 */
    createVersionRequests?: string[];
    /** 自定义 /applications/{appId}/workspace-templates 返回；不传则用默认空数组。 */
    workspaceTemplates?: Record<string, Array<Record<string, unknown>>>;
    /** 自定义 /applications/{appId}/workspace-templates/{tid}/versions 返回；key 用 `{appId}:{templateId}`。 */
    workspaceVersions?: Record<string, Array<Record<string, unknown>>>;
    /** 工具盒子目录；滚动布局用例注入足量工具，普通用例保留最小目录。 */
    toolboxTools?: Array<Record<string, unknown>>;
    /** 通用记忆中心 mock；默认能力开放但列表为空。 */
    memoryAvailable?: boolean;
    personalMemories?: Array<Record<string, unknown>>;
    teamMemories?: Array<Record<string, unknown>>;
    memorySkillProposals?: Array<Record<string, unknown>>;
    memoryEvidence?: Array<Record<string, unknown>>;
    memoryAdminHealth?: Record<string, unknown>;
    memorySettings?: Record<string, unknown>;
    memoryWhitelist?: Array<Record<string, unknown>>;
    memorySettingsRequests?: Array<Record<string, unknown>>;
    memoryWhitelistEnableRequests?: string[];
    memoryWhitelistDisableRequests?: string[];
    memoryInternalModelProviders?: Record<string, unknown>;
    memoryInternalModelsByProvider?: Record<string, Array<Record<string, unknown>>>;
    memoryDirectoryUsers?: Array<Record<string, unknown>>;
    memoryDirectoryUserQueries?: string[];
    /** 版本选择前的 Git 只读访问预检响应，以 versionId 为键。 */
    gitAccessResults?: Record<string, Record<string, unknown>>;
    gitAccessRequests?: string[];
    publicAgentRepositories?: Array<Record<string, unknown>>;
    publicAgentWorktreesByServer?: Record<string, Array<Record<string, unknown>>>;
    defaultPersonalRequests?: string[];
    processStatus?: "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE";
    processServiceStatus?: "UNASSIGNED" | "NOT_RUNNING";
    processStatusRequests?: string[];
    processInitializations?: Array<Record<string, unknown>>;
    initializeFailureThenReady?: boolean;
    ensureDefaultRequiresReady?: boolean;
    sessions?: Array<Record<string, unknown>>;
    nightTaskRequests?: Array<Record<string, unknown>>;
    nightTasks?: Array<Record<string, unknown>>;
    sessionRequestGate?: Promise<void>;
    sessionTreeMessages?: Record<string, unknown>;
    sessionTreeMessagesBySessionId?: Record<string, Record<string, unknown>>;
    sessionTreeRequests?: string[];
    sessionMessages?: Array<Record<string, unknown>>;
    sessionMessagesBySessionId?: Record<string, Array<Record<string, unknown>>>;
    /** 指定历史会话的 pending native question，键为 sessionId。 */
    sessionQuestionsById?: Record<string, Array<Record<string, unknown>>>;
    /** 指定历史会话的 pending native permission，键为 sessionId。 */
    sessionPermissionsById?: Record<string, Array<Record<string, unknown>>>;
    sessionMessageRequests?: string[];
    sessionMessagesGate?: Promise<void>;
    sessionInteractionsGate?: Promise<void>;
    messageFeedbackGate?: Promise<void>;
    feedbackRequests?: string[];
    runFeedbackQueryRequests?: Array<Record<string, unknown>>;
    historyRun?: Record<string, unknown>;
    runsByRunId?: Record<string, Record<string, unknown>>;
    runDetailRequests?: string[];
    runDetailFailuresBeforeSuccess?: Record<string, number>;
    historyDiffFiles?: Array<Record<string, unknown>>;
    historyRunGate?: Promise<void>;
    historyRunRequests?: string[];
    activeRun?: Record<string, unknown> | null;
    activeRunRequests?: string[];
    activeRunRequestGate?: Promise<void>;
    runEventRequests?: string[];
    runContextRequests?: string[];
    runContextTokens?: string[];
    runContextRequestGates?: Record<string, Promise<void>>;
    runFailures?: string[];
    runFailureResponses?: Array<{ status: number; code: string; message: string }>;
    runRequestGate?: Promise<void>;
    runRequestGates?: Array<Promise<void>>;
    runtimeStateHttpRequests?: string[];
    runtimeStateSummary?: Record<string, unknown>;
    runtimeStateEventGate?: Promise<void>;
    runtimeStateStreamFailure?: boolean;
    runtimeStateEventRequests?: string[];
    /** 分享工作台与分享管理 mock；分享头只应出现在固定分享范围请求。 */
    authUser?: { userId: string; username: string; unifiedAuthId: string; roles?: string[] };
    sessionShareAccess?: Record<string, unknown>;
    sessionShareAccessFailure?: { status: number; code: string; message: string; details?: Record<string, unknown> };
    missingSessionsNotFound?: boolean;
    sessionShareRuntimeStates?: Array<Record<string, unknown>>;
    sessionShareHeaderRequests?: Array<{ method: string; path: string; shareId: string }>;
    sharedSessions?: Array<Record<string, unknown>>;
    sessionCollaborationShare?: Record<string, unknown> | null;
    sessionShareCandidates?: Array<Record<string, unknown>>;
    sessionSharePutRequests?: Array<Record<string, unknown>>;
    sessionShareRevokeRequests?: Array<{ sessionId: string; expectedVersion: string | null }>;
    userNotifications?: Array<Record<string, unknown>>;
    userNotificationUnreadCount?: number;
    userNotificationReadRequests?: string[];
    userNotificationEventRequests?: string[];
    userNotificationEvents?: Array<{
      gate?: Promise<void>;
      eventName?: "user-notification.snapshot" | "user-notification.updated";
      changeType: string;
      notificationId?: string | null;
      unreadCount: number;
    }>;
    skipInitialAuthToken?: boolean;
    loginRequests?: Array<{ username?: string; password?: string }>;
    sideQuestionRequests?: Array<Record<string, unknown>>;
    sideQuestionRunIds?: string[];
    /** 记录应用个人配置更新的 Git 同步与运行态 dispose 顺序。 */
    runtimeReloadRequests?: string[];
    /** 应用源码 mock 与请求记录；key 分别使用 appId 与 `appId:repositoryId:generation`。 */
    appSourceRepositories?: Record<string, Array<Record<string, unknown>>>;
    appSourceRepositoryListGates?: Record<string, Promise<void>>;
    appSourceOpenResults?: Record<string, Record<string, unknown>>;
    appSourceOpenGates?: Record<string, Promise<void>>;
    appSourceOpenFailures?: Record<string, { status: number; code: string; message: string }>;
    recentAppSource?: Record<string, unknown> | null;
    appSourceRecentGetResponses?: Array<{
      value?: Record<string, unknown> | null;
      failure?: { status: number; code: string; message: string };
    }>;
    appSourceBranches?: Record<string, string[]>;
    appSourceBranchGates?: Record<string, Promise<void>>;
    appSourceTreeSnapshots?: Record<string, Record<string, unknown>>;
    appSourceTreeGates?: Record<string, Promise<void>>;
    appSourceMaterializationResults?: Record<string, Record<string, unknown>>;
    appSourceMaterializationRequests?: Array<{ key: string; payload: Record<string, unknown> }>;
    appSourceOperationSnapshots?: Record<string, Array<Record<string, unknown>> | Record<string, unknown>>;
    /** 按 operationId 与 GET 次序阻塞 snapshot，用于覆盖重连 snapshot 尚未返回的代次窗口。 */
    appSourceOperationRequestGates?: Record<string, Array<Promise<void> | undefined>>;
    appSourceTicketRequests?: string[];
    appSourceTicketRequestTimes?: number[];
    appSourceRetryResults?: Record<string, Record<string, unknown>>;
    appSourceRetryRequests?: Array<{ key: string; payload: Record<string, unknown> }>;
    appSourceProgressSocketPlans?: Array<{
      frames?: Array<{ afterMs: number; event: Record<string, unknown> }>;
      disconnectAfterMs?: number;
      openAfterMs?: number;
    }>;
    appSourceRequests?: string[];
    clearedRecentAppSource?: string[];
  } = {}
) {
  await page.exposeFunction("__taRecordWorkspaceFileRequest", (workspaceId: string, path: string) => {
    capture.fileRequests?.push({ workspaceId, path });
  });
  await page.exposeFunction("__taRecordWorkspaceFileWrite", (workspaceId: string, path: string, content: string) => {
    capture.fileWriteRequests?.push({ workspaceId, path, content });
  });
  await page.exposeFunction("__taRecordWorkspaceFileRead", (workspaceId: string, path: string, attempt: number) => {
    capture.fileReadRequests?.push({ workspaceId, path, attempt });
  });
  await page.exposeFunction("__taRecordWorkspaceMove", (workspaceId: string, sourcePath: string, targetPath: string) => {
    capture.workspaceMoveRequests?.push({ workspaceId, sourcePath, targetPath });
  });
  await page.exposeFunction("__taRecordAgentFileFrame", (frame: {
    op: string;
    scope: string;
    path: string;
    workspaceId?: string;
    worktreeId?: string;
    attempt?: number;
    content?: string;
  }) => {
    capture.agentFileFrames?.push(frame);
  });
  if (!capture.skipInitialAuthToken) {
    await page.addInitScript(() => {
      sessionStorage.setItem("test-agent.auth.token", "test-token");
      // 工作台 E2E 默认跳过首次引导，避免遮罩拦截真实文件树与 tab 点击。
      localStorage.setItem("test-agent.onboarding.v7:usr_admin", "seen");
    });
  }
  await page.addInitScript(({
    fileContents,
    fileReadDelays,
    fileReadFailuresBeforeSuccess,
    fileReadFailureAttempts,
    fileReadNotFoundAttempts,
    fileReadResponses,
    workspaceMutationDelays,
    workspaceViewLists,
    workspaceViewListsAfterMoves,
    workspaceViewContents,
    agentFileContents,
    agentFileReadDelays,
    agentFileReadFailureAttempts,
    agentFileReadNotFoundAttempts,
    agentFileReadResponses,
    appSourceProgressSocketPlans
  }) => {
    const recordFileRequest = (workspaceId: string, path: string) => {
      const win = window as Window & {
        __taRecordWorkspaceFileRequest?: (workspaceId: string, path: string) => void;
        __taRecordWorkspaceFileRead?: (workspaceId: string, path: string, attempt: number) => void;
        __taRecordWorkspaceFileWrite?: (workspaceId: string, path: string, content: string) => void;
        __taRecordWorkspaceMove?: (workspaceId: string, sourcePath: string, targetPath: string) => void;
      };
      win.__taRecordWorkspaceFileRequest?.(workspaceId, path);
    };
    const recordWorkspaceMove = (workspaceId: string, sourcePath: string, targetPath: string) => {
      const win = window as Window & {
        __taRecordWorkspaceMove?: (workspaceId: string, sourcePath: string, targetPath: string) => void;
      };
      win.__taRecordWorkspaceMove?.(workspaceId, sourcePath, targetPath);
    };
    const recordFileWrite = (workspaceId: string, path: string, content: string) => {
      const win = window as Window & {
        __taRecordWorkspaceFileWrite?: (workspaceId: string, path: string, content: string) => void;
      };
      win.__taRecordWorkspaceFileWrite?.(workspaceId, path, content);
    };
    const recordFileRead = (workspaceId: string, path: string, attempt: number) => {
      const win = window as Window & {
        __taRecordWorkspaceFileRead?: (workspaceId: string, path: string, attempt: number) => void;
      };
      win.__taRecordWorkspaceFileRead?.(workspaceId, path, attempt);
    };
    const recordAgentFileFrame = (frame: {
      op: string;
      scope: string;
      path: string;
      workspaceId?: string;
      worktreeId?: string;
      attempt?: number;
      content?: string;
    }) => {
      const win = window as Window & {
        __taRecordAgentFileFrame?: (payload: typeof frame) => void;
      };
      win.__taRecordAgentFileFrame?.(frame);
    };
    const readAttempts: Record<string, number> = {};
    const agentReadAttempts: Record<string, number> = {};
    let workspaceMoveAttempt = 0;
    (window as Window & { __taClosedAppSourceTickets?: string[] }).__taClosedAppSourceTickets = [];
    type ViewLocator = { kind?: string; path?: string; referenceAlias?: string };
    const viewKey = (locator: ViewLocator) =>
      `${locator.kind ?? "COMPOSITE"}:${locator.referenceAlias ?? ""}:${locator.path ?? ""}`;
    const entries = (path: string, workspaceId = "wrk_1234567890abcdef") => {
      if (workspaceId === "wrk_project_a") {
        return path === "src"
          ? [{ path: "src/main.ts", name: "main.ts", directory: false, size: 90, lastModifiedAt: "2026-06-19T00:00:00Z" }]
          : [{ path: "src", name: "src", directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" }];
      }
      if (path === "tests") {
        return [{ path: "tests/checkout.spec.ts", name: "checkout.spec.ts", directory: false, size: 120, lastModifiedAt: "2026-06-19T00:00:00Z" }];
      }
      const configuredFiles = Object.keys(fileContents as Record<string, string>);
      if (path) {
        const prefix = `${path}/`;
        const directFiles = configuredFiles
          .filter((filePath) => filePath.startsWith(prefix) && !filePath.slice(prefix.length).includes("/"))
          .map((filePath) => ({ path: filePath, name: filePath.slice(prefix.length), directory: false, size: (fileContents as Record<string, string>)[filePath]?.length ?? 0, lastModifiedAt: "2026-06-19T00:00:00Z" }));
        if (directFiles.length) return directFiles;
      }
      const configuredDirectories = Array.from(new Set(configuredFiles.filter((filePath) => filePath.includes("/")).map((filePath) => filePath.split("/")[0] ?? "")))
        .filter(Boolean)
        .map((name) => ({ path: name, name, directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" }));
      return [
        { path: "tests", name: "tests", directory: true, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" },
        ...configuredDirectories.filter((entry) => entry.name !== "tests"),
        { path: "package.json", name: "package.json", directory: false, size: 80, lastModifiedAt: "2026-06-19T00:00:00Z" }
      ];
    };
    const directories = (path?: string) => {
      if (path === "/Users/huang/workspace/project-a") {
        return { path, parentPath: "/Users/huang/workspace", entries: [{ name: "src", path: "/Users/huang/workspace/project-a/src" }] };
      }
      if (path === "/Users/huang/workspace/demo-tests") {
        return { path, parentPath: "/Users/huang/workspace", entries: [{ name: "tests", path: "/Users/huang/workspace/demo-tests/tests" }] };
      }
      return {
        path: "/Users/huang/workspace",
        parentPath: null,
        entries: [
          { name: "demo-tests", path: "/Users/huang/workspace/demo-tests" },
          { name: "project-a", path: "/Users/huang/workspace/project-a" }
        ]
      };
    };
    const agentEntries = (scope: string, path: string) => {
      const prefix = `${scope}:`;
      const files = Object.keys(agentFileContents as Record<string, string>)
        .filter((key) => key.startsWith(prefix))
        .map((key) => key.slice(prefix.length));
      const directoryPrefix = path ? `${path}/` : "";
      const children = new Map<string, { path: string; name: string; directory: boolean; size: number; lastModifiedAt: string }>();
      for (const filePath of files) {
        if (!filePath.startsWith(directoryPrefix)) continue;
        const rest = filePath.slice(directoryPrefix.length);
        if (!rest) continue;
        const name = rest.split("/")[0] ?? rest;
        const childPath = path ? `${path}/${name}` : name;
        const directory = rest.includes("/");
        const content = (agentFileContents as Record<string, string>)[`${scope}:${filePath}`] ?? "";
        children.set(childPath, {
          path: childPath,
          name,
          directory,
          size: directory ? 0 : content.length,
          lastModifiedAt: "2026-06-19T00:00:00Z"
        });
      }
      return [...children.values()];
    };
    class MockWorkspaceFileWebSocket {
      static CONNECTING = 0;
      static OPEN = 1;
      static CLOSING = 2;
      static CLOSED = 3;
      onopen: ((event: Event) => void) | null = null;
      onmessage: ((event: MessageEvent) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      onclose: ((event: CloseEvent) => void) | null = null;
      readyState = MockWorkspaceFileWebSocket.CONNECTING;
      private readonly uploadStates = new Map<string, { totalBytes: number; uploadedBytes: number }>();
      constructor(readonly url: string) {
        const appSourceTicket = url.includes("/mock/app-source-progress")
          ? new URL(url, window.location.href).searchParams.get("ticket") ?? ""
          : "";
        const ticketIndex = Math.max(0, Number(appSourceTicket.replace("ast_", "")) - 1);
        const plan = (appSourceProgressSocketPlans as Array<{
          frames?: Array<{ afterMs: number; event: Record<string, unknown> }>;
          disconnectAfterMs?: number;
          openAfterMs?: number;
        }>)[ticketIndex];
        window.setTimeout(() => {
          if (this.readyState === MockWorkspaceFileWebSocket.CLOSED) return;
          this.readyState = MockWorkspaceFileWebSocket.OPEN;
          this.onopen?.(new Event("open"));
          if (!url.includes("/mock/app-source-progress")) return;
          for (const frame of plan?.frames ?? []) {
            window.setTimeout(() => {
              // 故意允许关闭后的迟到帧到达，验证 UI authority 会忽略旧连接。
              this.onmessage?.(new MessageEvent("message", { data: JSON.stringify(frame.event) }));
            }, frame.afterMs);
          }
          if (plan?.disconnectAfterMs !== undefined) {
            window.setTimeout(() => {
              this.readyState = MockWorkspaceFileWebSocket.CLOSED;
              this.onclose?.(new CloseEvent("close"));
            }, plan.disconnectAfterMs);
          }
        }, plan?.openAfterMs ?? 0);
      }
      send(payload: string) {
        const request = JSON.parse(payload) as { id: string; op: string; params?: Record<string, string | undefined> };
        const params = request.params ?? {};
        const locator = (request.params as unknown as { locator?: ViewLocator } | undefined)?.locator
          ?? { kind: "COMPOSITE", path: "" };
        let data: unknown = null;
        if (request.op === "workspace.view.list") {
          recordFileRequest(params.workspaceId ?? "", locator.path ?? "");
          const configured = (workspaceViewLists as Record<string, {
            entries: Array<Record<string, unknown>>;
            warnings?: Array<{ alias?: string; code: string; message: string }>;
            truncated?: boolean;
          }>)[viewKey(locator)];
          data = configured ?? {
            entries: entries(locator.path ?? "", params.workspaceId).map((entry) => ({
              id: `workspace:${entry.path}`,
              ...entry,
              locator: { kind: "WORKSPACE", path: entry.path },
              source: "WORKSPACE",
              merged: false,
              collision: false,
              readonly: false,
              workspacePath: entry.path,
              referenceAliases: []
            })),
            warnings: [],
            truncated: false
          };
        } else if (request.op === "workspace.list") {
          recordFileRequest(params.workspaceId ?? "", params.path ?? "");
          data = entries(params.path ?? "", params.workspaceId);
        } else if (request.op === "workspace.search") {
          const query = (params.query ?? "").toLowerCase();
          data = Object.keys(fileContents as Record<string, string>)
            .filter((path) => path.toLowerCase().includes(query))
            .map((path) => ({
              path,
              name: path.split("/").at(-1) ?? path,
              directory: path.includes("/") ? path.slice(0, path.lastIndexOf("/")) : "",
              size: (fileContents as Record<string, string>)[path]?.length ?? 0,
              lastModifiedAt: "2026-06-19T00:00:00Z"
            }));
        } else if (request.op === "workspace.view.read") {
          const content = (workspaceViewContents as Record<string, string>)[viewKey(locator)] ?? "";
          data = {
            path: locator.path ?? "",
            content,
            size: content.length,
            readonly: true,
            source: "REFERENCE",
            referenceAlias: locator.referenceAlias,
            locator
          };
        } else if (request.op === "workspace.read") {
          const path = params.path ?? "tests/checkout.spec.ts";
          const workspaceId = params.workspaceId ?? "";
          const attemptKey = `${workspaceId}:${path}`;
          const attempt = (readAttempts[attemptKey] ?? 0) + 1;
          readAttempts[attemptKey] = attempt;
          recordFileRead(workspaceId, path, attempt);
          const delay = (fileReadDelays as Record<string, number[]>)[path]?.[attempt - 1] ?? 0;
          const failures = (fileReadFailuresBeforeSuccess as Record<string, number>)[path] ?? 0;
          const failureAttempts = (fileReadFailureAttempts as Record<string, number[]>)[path] ?? [];
          const notFoundAttempts = (fileReadNotFoundAttempts as Record<string, number[]>)[path] ?? [];
          if (attempt <= failures || failureAttempts.includes(attempt) || notFoundAttempts.includes(attempt)) {
            const notFound = notFoundAttempts.includes(attempt);
            window.setTimeout(() => {
              this.onmessage?.(new MessageEvent("message", {
                data: JSON.stringify({
                  id: request.id,
                  type: "error",
                  code: notFound ? "NOT_FOUND" : "FILE_READ_FAILED",
                  message: notFound ? "mock file not found" : "mock file read failed",
                  traceId: "trace_e2e"
                })
              }));
            }, delay);
            return;
          }
          const content = (fileReadResponses as Record<string, string[]>)[path]?.[attempt - 1]
            ?? (fileContents as Record<string, string>)[path]
            ?? "import { test } from '@playwright/test';\n\ntest('checkout', async () => {});\n";
          data = {
            path,
            content,
            encoding: "utf-8",
            size: content.length
          };
          window.setTimeout(() => {
            this.onmessage?.(new MessageEvent("message", {
              data: JSON.stringify({ id: request.id, type: "result", data, traceId: "trace_e2e" })
            }));
          }, delay);
          return;
        } else if (request.op === "workspace.write") {
          const path = params.path ?? "";
          const content = params.content ?? "";
          recordFileWrite(params.workspaceId ?? "", path, content);
          (fileContents as Record<string, string>)[path] = content;
        } else if (request.op === "workspace.rename") {
          const path = params.path ?? "";
          const name = params.name ?? "";
          const separatorIndex = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
          const parent = separatorIndex >= 0 ? path.slice(0, separatorIndex) : "";
          const separator = path.includes("\\") ? "\\" : "/";
          const nextPath = parent ? `${parent}${separator}${name}` : name;
          window.setTimeout(() => {
            const contents = fileContents as Record<string, string>;
            contents[nextPath] = contents[path] ?? "";
            delete contents[path];
            this.onmessage?.(new MessageEvent("message", {
              data: JSON.stringify({ id: request.id, type: "result", data: null, traceId: "trace_e2e" })
            }));
          }, (workspaceMutationDelays as Record<string, number>)[request.op] ?? 0);
          return;
        } else if (request.op === "workspace.move") {
          const workspaceId = params.workspaceId ?? "";
          const sourcePath = params.sourcePath ?? "";
          const targetPath = params.targetPath ?? "";
          recordWorkspaceMove(workspaceId, sourcePath, targetPath);
          const contents = fileContents as Record<string, string>;
          for (const currentPath of Object.keys(contents)) {
            if (currentPath !== sourcePath && !currentPath.startsWith(`${sourcePath}/`)) continue;
            const movedPath = `${targetPath}${currentPath.slice(sourcePath.length)}`;
            contents[movedPath] = contents[currentPath] ?? "";
            delete contents[currentPath];
          }
          const nextViewLists = (workspaceViewListsAfterMoves as Array<Record<string, {
            entries: Array<Record<string, unknown>>;
            warnings?: Array<{ alias?: string; code: string; message: string }>;
            truncated?: boolean;
          }>>)[workspaceMoveAttempt];
          workspaceMoveAttempt += 1;
          if (nextViewLists) {
            const currentViewLists = workspaceViewLists as Record<string, unknown>;
            for (const key of Object.keys(currentViewLists)) delete currentViewLists[key];
            Object.assign(currentViewLists, nextViewLists);
          }
        } else if (request.op === "agent-config.list") {
          const scope = params.scope ?? "PUBLIC";
          const path = params.path ?? "";
          recordAgentFileFrame({
            op: request.op,
            scope,
            path,
            workspaceId: params.workspaceId,
            worktreeId: params.worktreeId
          });
          data = agentEntries(scope, path);
        } else if (request.op === "agent-config.read") {
          const scope = params.scope ?? "PUBLIC";
          const path = params.path ?? "";
          const key = `${scope}:${path}`;
          const attemptKey = `${scope}:${params.workspaceId ?? ""}:${params.worktreeId ?? ""}:${path}`;
          const attempt = (agentReadAttempts[attemptKey] ?? 0) + 1;
          agentReadAttempts[attemptKey] = attempt;
          recordAgentFileFrame({
            op: request.op,
            scope,
            path,
            workspaceId: params.workspaceId,
            worktreeId: params.worktreeId,
            attempt
          });
          const delay = (agentFileReadDelays as Record<string, number[]>)[key]?.[attempt - 1] ?? 0;
          const failureAttempts = (agentFileReadFailureAttempts as Record<string, number[]>)[key] ?? [];
          const notFoundAttempts = (agentFileReadNotFoundAttempts as Record<string, number[]>)[key] ?? [];
          if (failureAttempts.includes(attempt) || notFoundAttempts.includes(attempt)) {
            const notFound = notFoundAttempts.includes(attempt);
            window.setTimeout(() => {
              this.onmessage?.(new MessageEvent("message", {
                data: JSON.stringify({
                  id: request.id,
                  type: "error",
                  code: notFound ? "NOT_FOUND" : "FILE_READ_FAILED",
                  message: notFound ? "mock Agent file not found" : "mock Agent file read failed",
                  traceId: "trace_agent_e2e"
                })
              }));
            }, delay);
            return;
          }
          const content = (agentFileReadResponses as Record<string, string[]>)[key]?.[attempt - 1]
            ?? (agentFileContents as Record<string, string>)[key]
            ?? "";
          data = { path, content, encoding: "utf-8", size: content.length };
          window.setTimeout(() => {
            this.onmessage?.(new MessageEvent("message", {
              data: JSON.stringify({ id: request.id, type: "result", data, traceId: "trace_agent_e2e" })
            }));
          }, delay);
          return;
        } else if (request.op === "agent-config.write") {
          const scope = params.scope ?? "PUBLIC";
          const path = params.path ?? "";
          const content = params.content ?? "";
          recordAgentFileFrame({
            op: request.op,
            scope,
            path,
            workspaceId: params.workspaceId,
            worktreeId: params.worktreeId,
            content
          });
          (agentFileContents as Record<string, string>)[`${scope}:${path}`] = content;
        } else if (request.op === "workspace.status") {
          const path = params.path ?? "";
          // 聊天附件使用内容指纹路径；默认 mock 工作区不存在该路径，避免把占位文件状态误判为可复用。
          data = path.startsWith(".testagent/attachments/")
            ? { path, exists: false, directory: false, size: 0, lastModifiedAt: "2026-06-19T00:00:00Z" }
            : { path, exists: true, directory: false, size: 80, lastModifiedAt: "2026-06-19T00:00:00Z" };
        } else if (request.op === "workspace.mkdir") {
          data = null;
        } else if (request.op === "workspace.upload.begin") {
          const uploadId = `upload_${request.id}`;
          const totalBytes = Number(params.size ?? 0);
          this.uploadStates.set(uploadId, { totalBytes, uploadedBytes: 0 });
          data = { uploadId, chunkBytes: 4 * 1024 * 1024, totalBytes };
        } else if (request.op === "workspace.upload.chunk") {
          const uploadId = params.uploadId ?? "";
          const state = this.uploadStates.get(uploadId) ?? { totalBytes: 0, uploadedBytes: 0 };
          const encoded = params.contentBase64 ?? "";
          const padding = encoded.endsWith("==") ? 2 : encoded.endsWith("=") ? 1 : 0;
          const chunkBytes = Math.max(0, Math.floor(encoded.length * 3 / 4) - padding);
          state.uploadedBytes = Math.min(state.totalBytes, state.uploadedBytes + chunkBytes);
          this.uploadStates.set(uploadId, state);
          data = { uploadedBytes: state.uploadedBytes, totalBytes: state.totalBytes };
        } else if (request.op === "workspace.upload.complete") {
          const uploadId = params.uploadId ?? "";
          const state = this.uploadStates.get(uploadId) ?? { totalBytes: 0, uploadedBytes: 0 };
          this.uploadStates.delete(uploadId);
          data = { size: state.totalBytes };
        } else if (request.op === "workspace.upload.abort") {
          this.uploadStates.delete(params.uploadId ?? "");
          data = null;
        } else if (request.op === "directory.list") {
          data = directories(params.path);
        } else if (request.op === "workspace.create") {
          data = {
            workspaceId: "wrk_project_a",
            name: params.name ?? "project-a",
            rootPath: params.rootPath ?? "/Users/huang/workspace/project-a",
            linuxServerId: "10.8.0.12",
            status: "ACTIVE",
            createdAt: "2026-06-19T00:00:00Z",
            updatedAt: "2026-06-19T00:00:00Z"
          };
        }
        window.setTimeout(() => {
          this.onmessage?.(new MessageEvent("message", {
            data: JSON.stringify({ id: request.id, type: "result", data, traceId: "trace_e2e" })
          }));
        }, 0);
      }
      close() {
        this.readyState = MockWorkspaceFileWebSocket.CLOSED;
        if (this.url.includes("/mock/app-source-progress")) {
          (window as Window & { __taClosedAppSourceTickets?: string[] })
            .__taClosedAppSourceTickets?.push(
              new URL(this.url, window.location.href).searchParams.get("ticket") ?? ""
            );
        }
        this.onclose?.(new CloseEvent("close"));
      }
    }
    Object.assign(MockWorkspaceFileWebSocket, {
      CONNECTING: MockWorkspaceFileWebSocket.CONNECTING,
      OPEN: MockWorkspaceFileWebSocket.OPEN,
      CLOSING: MockWorkspaceFileWebSocket.CLOSING,
      CLOSED: MockWorkspaceFileWebSocket.CLOSED
    });
    (window as Window & { WebSocket: typeof WebSocket }).WebSocket = MockWorkspaceFileWebSocket as unknown as typeof WebSocket;
  }, {
    fileContents: capture.fileContents ?? {},
    fileReadDelays: capture.fileReadDelays ?? {},
    fileReadFailuresBeforeSuccess: capture.fileReadFailuresBeforeSuccess ?? {},
    fileReadFailureAttempts: capture.fileReadFailureAttempts ?? {},
    fileReadNotFoundAttempts: capture.fileReadNotFoundAttempts ?? {},
    fileReadResponses: capture.fileReadResponses ?? {},
    workspaceMutationDelays: capture.workspaceMutationDelays ?? {},
    workspaceViewLists: capture.workspaceViewLists ?? {},
    workspaceViewListsAfterMoves: capture.workspaceViewListsAfterMoves ?? [],
    workspaceViewContents: capture.workspaceViewContents ?? {},
    agentFileContents: capture.agentFileContents ?? {},
    agentFileReadDelays: capture.agentFileReadDelays ?? {},
    agentFileReadFailureAttempts: capture.agentFileReadFailureAttempts ?? {},
    agentFileReadNotFoundAttempts: capture.agentFileReadNotFoundAttempts ?? {},
    agentFileReadResponses: capture.agentFileReadResponses ?? {},
    appSourceProgressSocketPlans: capture.appSourceProgressSocketPlans ?? []
  });
  // E2E 不依赖外部字体，避免 Google Fonts 网络波动阻塞 domcontentloaded。
  await page.route("https://fonts.googleapis.com/**", async (route) => {
    await route.fulfill({ status: 200, contentType: "text/css", body: "" });
  });
  await page.route("https://fonts.gstatic.com/**", async (route) => {
    await route.fulfill({ status: 200, body: "" });
  });
  const workspaceItems = capture.workspaces ?? [workspace()];
  const applications = capture.applications ?? [{ appId: "app_gcms", appName: "F-GCMS", enabled: true }];
  const managedApplications = capture.managedApplications ?? applications;
  const agentResponses = [...(capture.agentResponses ?? [])];
  const nightTasks = capture.nightTasks ?? [];
  let currentProcessStatus = capture.processStatus ?? "READY";
  let sshKeys: Array<Record<string, unknown>> = [];
  let memorySettings = { ...(capture.memorySettings ?? {}) };
  let memoryWhitelist = [...(capture.memoryWhitelist ?? [])];
  const appSourceOperationRequestCounts: Record<string, number> = {};
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    const sessionShareHeader = route.request().headers()["x-test-agent-session-share"];
    if (sessionShareHeader) {
      capture.sessionShareHeaderRequests?.push({
        method,
        path: url.pathname,
        shareId: sessionShareHeader
      });
    }
    if (method === "OPTIONS") {
      await route.fulfill({ status: 204, headers: corsHeaders() });
      return;
    }
    if (method === "POST" && url.pathname === "/api/auth/login") {
      const body = JSON.parse(route.request().postData() ?? "{}") as { username?: string; password?: string };
      capture.loginRequests?.push({ username: body.username, password: body.password });
      await route.fulfill(json({
        token: "test-token",
        tokenType: "Bearer",
        expiresAt: "2026-06-24T01:00:00Z"
      }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/auth/me") {
      await capture.authMeGate;
      const currentUser = capture.authUser ?? {
        userId: "usr_admin",
        username: "admin",
        unifiedAuthId: "admin",
        roles: capture.authRoles ?? ["APP_ADMIN"]
      };
      const roles = currentUser.roles ?? capture.authRoles ?? ["APP_ADMIN"];
      await route.fulfill(json({
        userId: currentUser.userId,
        username: currentUser.username,
        unifiedAuthId: currentUser.unifiedAuthId,
        roles,
        // E2E mock 直接把后端 translations 关系预生成好；用户菜单顶部灰显行会展示这里的中文标签。
        roleLabels: roles.map((role) => roleLabelOf(role))
      }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/notification-center/notifications/events") {
      const requestIndex = capture.userNotificationEventRequests?.length ?? 0;
      capture.userNotificationEventRequests?.push(url.pathname);
      const update = capture.userNotificationEvents?.[requestIndex] ?? {
        eventName: "user-notification.snapshot" as const,
        changeType: "SNAPSHOT",
        notificationId: null,
        unreadCount: capture.userNotificationUnreadCount ?? 0
      };
      await update.gate;
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: `event: ${update.eventName ?? "user-notification.updated"}\ndata: ${JSON.stringify({
          changeType: update.changeType,
          notificationId: update.notificationId ?? null,
          unreadCount: update.unreadCount,
          generatedAt: "2026-08-10T10:00:00Z"
        })}\n\n`
      });
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/notification-center/notifications") {
      const pageNumber = Number(url.searchParams.get("page") ?? "1");
      const size = Number(url.searchParams.get("size") ?? "20");
      const unreadOnly = url.searchParams.get("unreadOnly") === "true";
      const allItems = capture.userNotifications ?? [];
      const filtered = unreadOnly ? allItems.filter((item) => item.unread === true) : allItems;
      const offset = Math.max(0, (pageNumber - 1) * size);
      await route.fulfill(json({
        items: filtered.slice(offset, offset + size),
        page: pageNumber,
        size,
        total: filtered.length,
        unreadCount: capture.userNotificationUnreadCount
          ?? allItems.filter((item) => item.unread === true && item.actionAvailable === true).length
      }));
      return;
    }
    const notificationReadMatch = url.pathname.match(
      /^\/api\/internal\/platform\/notification-center\/notifications\/([^/]+)\/read$/
    );
    if (method === "POST" && notificationReadMatch) {
      const notificationId = decodeURIComponent(notificationReadMatch[1] ?? "");
      capture.userNotificationReadRequests?.push(notificationId);
      await route.fulfill(json({ notificationId, read: true }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/session-shares/access") {
      const failure = capture.sessionShareAccessFailure;
      if (failure) {
        await route.fulfill({
          status: failure.status,
          ...jsonFailure(failure.code, failure.message, failure.details)
        });
        return;
      }
      await route.fulfill(json(capture.sessionShareAccess ?? null));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/session-shares/runtime-state/events") {
      const states = capture.sessionShareRuntimeStates ?? [];
      const body = states.map((state, index) => {
        const eventName = state.active === false
          ? "session-share.invalidated"
          : index === 0
            ? "session-share.snapshot"
            : "session-share.updated";
        return `event: ${eventName}\ndata: ${JSON.stringify(state)}\n\n`;
      }).join("");
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body
      });
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/session-shares") {
      await route.fulfill(json(pageOf(capture.sharedSessions ?? [])));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/session-share-candidates") {
      await route.fulfill(json(pageOf(capture.sessionShareCandidates ?? [])));
      return;
    }
    const collaborationShareMatch = url.pathname.match(
      /^\/api\/internal\/platform\/opencode-runtime\/sessions\/([^/]+)\/collaboration-share$/
    );
    if (collaborationShareMatch && method === "GET") {
      await route.fulfill(json(capture.sessionCollaborationShare ?? null));
      return;
    }
    if (collaborationShareMatch && method === "PUT") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.sessionSharePutRequests?.push(request);
      const sessionId = decodeURIComponent(collaborationShareMatch[1] ?? "ses_1");
      const members = Array.isArray(request.members) ? request.members : [];
      const candidates = capture.sessionShareCandidates ?? [];
      const updated = {
        shareId: "shr_e2e_unique",
        sharePath: "/s/shr_e2e_unique",
        sessionId,
        workspaceId: "wrk_1234567890abcdef",
        ownerUserId: capture.authUser?.userId ?? "usr_admin",
        status: "ACTIVE",
        expiresAt: String(request.expiresAt ?? "2026-08-16T00:00:00Z"),
        version: Number(capture.sessionCollaborationShare?.version ?? -1) + 1,
        members: members.map((member) => {
          const item = member as { userId?: string; canChat?: boolean };
          const candidate = candidates.find((value) => value.userId === item.userId) ?? {};
          return {
            userId: item.userId,
            unifiedAuthId: candidate.unifiedAuthId ?? item.userId,
            username: candidate.username ?? item.userId,
            canChat: item.canChat === true,
            status: "ACTIVE",
            sharedAt: "2026-08-09T00:00:00Z",
            updatedAt: "2026-08-09T00:00:00Z",
            removedAt: null
          };
        }),
        createdAt: "2026-08-09T00:00:00Z",
        updatedAt: "2026-08-09T00:00:00Z",
        revokedAt: null
      };
      capture.sessionCollaborationShare = updated;
      await route.fulfill(json(updated));
      return;
    }
    if (collaborationShareMatch && method === "DELETE") {
      const sessionId = decodeURIComponent(collaborationShareMatch[1] ?? "ses_1");
      capture.sessionShareRevokeRequests?.push({
        sessionId,
        expectedVersion: url.searchParams.get("expectedVersion")
      });
      const updated = {
        ...(capture.sessionCollaborationShare ?? {}),
        status: "REVOKED",
        version: Number(capture.sessionCollaborationShare?.version ?? 0) + 1,
        updatedAt: "2026-08-09T00:01:00Z",
        revokedAt: "2026-08-09T00:01:00Z"
      };
      capture.sessionCollaborationShare = updated;
      await route.fulfill(json(updated));
      return;
    }
    if (method === "POST" && url.pathname === "/api/auth/logout") {
      capture.logoutRequests?.push(`${method} ${url.pathname}`);
      await route.fulfill(json(null));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/toolbox/tools") {
      const toolboxTools = capture.toolboxTools ?? [{
        toolId: "it-tools.hash-text",
        source: "IT_TOOLS",
        sourceName: "IT-Tools",
        sourceVersion: "2024.10.22-7ca5933",
        nameZh: "文本哈希",
        nameEn: "Hash text",
        descriptionZh: "计算文本摘要",
        category: "SECURITY",
        categoryLabel: "安全与加密",
        keywords: ["sha256", "摘要"],
        launchPath: "/toolbox/apps/it-tools/hash-text",
        clickCount: 0,
        hotRank: null
      }];
      await route.fulfill(json({
        catalogVersion: "catalog-e2e",
        total: toolboxTools.length,
        hotLimit: 10,
        tools: toolboxTools
      }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/toolbox\/tools\/[^/]+\/clicks$/.test(url.pathname)) {
      await route.fulfill(json({
        toolId: decodeURIComponent(url.pathname.split("/").at(-2) ?? ""),
        clickCount: 1,
        recorded: true,
        incremented: true
      }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/memory/v1/availability") {
      await route.fulfill(json({ enabled: capture.memoryAvailable ?? true }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/memory/v1/personal") {
      const items = capture.personalMemories ?? [];
      await route.fulfill(json({ items, page: 1, size: 100, total: items.length }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/memory/v1/team") {
      const items = capture.teamMemories ?? [];
      await route.fulfill(json({ items, page: 1, size: 100, total: items.length }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/memory/v1/skill-proposals") {
      const items = capture.memorySkillProposals ?? [];
      await route.fulfill(json({ items, page: 1, size: 100, total: items.length }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/memory\/v1\/memories\/[^/]+\/evidence$/.test(url.pathname)) {
      await route.fulfill(json(capture.memoryEvidence ?? []));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/memory/v1/admin/health") {
      await route.fulfill(json(capture.memoryAdminHealth ?? {}));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/memory/v1/admin/settings") {
      await route.fulfill(json(memorySettings));
      return;
    }
    if (method === "PATCH" && url.pathname === "/api/internal/platform/memory/v1/admin/settings") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.memorySettingsRequests?.push(request);
      memorySettings = {
        ...memorySettings,
        primaryChatModelId: request.primaryChatModelId ?? null,
        primaryEmbeddingModelId: request.primaryEmbeddingModelId ?? null,
        version: Number(memorySettings.version ?? 0) + 1,
        updatedByUserId: "usr_admin",
        updatedAt: "2026-08-09T01:00:00Z"
      };
      await route.fulfill(json(memorySettings));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/memory/v1/admin/whitelist") {
      await route.fulfill(json({ items: memoryWhitelist, page: 1, size: 100, total: memoryWhitelist.length }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/memory/v1/admin/whitelist") {
      const request = JSON.parse(route.request().postData() ?? "{}") as { userId?: string };
      const userId = request.userId ?? "";
      capture.memoryWhitelistEnableRequests?.push(userId);
      const entry = {
        userId,
        enabled: true,
        updatedByUserId: "usr_admin",
        createdAt: "2026-08-09T01:00:00Z",
        updatedAt: "2026-08-09T01:00:00Z"
      };
      memoryWhitelist = [...memoryWhitelist.filter((item) => item.userId !== userId), entry];
      await route.fulfill(json(entry));
      return;
    }
    if (method === "DELETE" && /^\/api\/internal\/platform\/memory\/v1\/admin\/whitelist\/[^/]+$/.test(url.pathname)) {
      const userId = decodeURIComponent(url.pathname.split("/").at(-1) ?? "");
      capture.memoryWhitelistDisableRequests?.push(userId);
      memoryWhitelist = memoryWhitelist.filter((item) => item.userId !== userId);
      await route.fulfill(json(null));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/internal-model-providers") {
      await route.fulfill(json(capture.memoryInternalModelProviders ?? { providers: [], tokenConfigured: false }));
      return;
    }
    const internalModelsMatch = url.pathname.match(/^\/api\/internal\/platform\/configuration-management\/internal-model-providers\/([^/]+)\/models$/);
    if (method === "GET" && internalModelsMatch) {
      const providerId = decodeURIComponent(internalModelsMatch[1] ?? "");
      await route.fulfill(json(capture.memoryInternalModelsByProvider?.[providerId] ?? []));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/system-management/users") {
      const keyword = url.searchParams.get("keyword") ?? "";
      capture.memoryDirectoryUserQueries?.push(keyword);
      const items = (capture.memoryDirectoryUsers ?? []).filter((user) => {
        if (!keyword) return true;
        const normalized = keyword.toLocaleLowerCase();
        return [user.userId, user.username, user.unifiedAuthId]
          .some((value) => String(value ?? "").toLocaleLowerCase().includes(normalized));
      });
      await route.fulfill(json({ items, page: 1, size: 30, total: items.length }));
      return;
    }
    if (url.pathname.startsWith("/api/internal/platform/configuration-management")) {
      if (!url.pathname.startsWith("/api/internal/platform/configuration-management/personal/ssh-keys")) {
        capture.configurationApplicationRequests?.push(`${method} ${url.pathname}`);
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/ssh-key/public-key") {
        // E2E 只需要可解密的 RSA 公钥来走真实前端混合加密链路；私钥不落盘也不进入请求。
        await route.fulfill(json({
          publicKey: "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjPXYeAezZ0gyMguKvrraoeA75sv4GGjZgt1JW1l2rEQ/vXM/WaoZf7rivIyZjQ0TZ3iGPXt1pwCiiKdjc4HL1xTnwPHzMsrFACxVG/sqsErkQm+WLmJuPK7r1iu3FQ6wiHrnScQU1p3msmDu1GDp+3Z+T/IxBa6JMdLiBD/aM9nJBZIrUfVzaXjJtJ7mv2opwZw7/oCmSLMrapwprs50PmOdbXLmu3LbujSwxYUR9ovo3iYaM43L4zwb+4mLs7CAD02trCzMvt+iEqbXrzUIbgwLYgSrTAhdFG0P7zjehsF9RerylhHHcFUwX/Z+xwauT3hB1V+z8Dw9L+75NW5A3QIDAQAB"
        }));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications") {
        await route.fulfill(json(applications));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications/app_gcms/members") {
        await route.fulfill(json([]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/repositories") {
        await route.fulfill(json(pageOf([])));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications/app_gcms/repositories") {
        await route.fulfill(json([]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/applications/app_gcms/workspaces") {
        await route.fulfill(json([]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/configuration-management/personal/ssh-keys") {
        await route.fulfill(json(sshKeys));
        return;
      }
      if (method === "POST" && url.pathname === "/api/internal/platform/configuration-management/personal/ssh-keys") {
        sshKeys = [{ sshKeyId: "ssh_1", name: "work", fingerprint: "SHA256:abc", createdAt: "2026-06-23T00:00:00Z" }];
        await route.fulfill(json(sshKeys[0]));
        return;
      }
      if (method === "DELETE" && url.pathname.startsWith("/api/internal/platform/configuration-management/personal/ssh-keys/")) {
        sshKeys = [];
        await route.fulfill(json(null));
        return;
      }
    }
    if (url.pathname.startsWith("/api/internal/platform/workspace-management")) {
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/agent-config/public/status") {
        await route.fulfill(json({
          scope: "PUBLIC",
          enabled: true,
          writable: true,
          gitUrl: "git@example.test:opencode-config.git",
          gitRootPath: "/mock/public-config",
          agentDirectory: "/mock/public-config/opencode",
          currentBranch: "main",
          commitHash: "public_commit"
        }));
        return;
      }
      if (method === "GET" && /^\/api\/internal\/platform\/workspace-management\/agent-config\/workspaces\/[^/]+\/status$/.test(url.pathname)) {
        await route.fulfill(json({
          scope: "WORKSPACE",
          enabled: true,
          writable: true,
          gitUrl: null,
          gitRootPath: "/mock/workspace-config",
          agentDirectory: "/mock/workspace-config/.opencode",
          currentBranch: "feature_testagent_20260715",
          commitHash: "workspace_agent_commit"
        }));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/agent-config/public/repositories") {
        await route.fulfill(json(capture.publicAgentRepositories ?? [{
          linuxServerId: "10.8.0.12",
          serverName: "dev-backend",
          gitRootPath: "/mock/public-config",
          configDirPath: "/mock/public-config/opencode",
          worktreeRootPath: "/mock/public-worktrees",
          status: "READY",
          initialized: true,
          initializationAllowed: true,
          currentBranch: "main",
          commitHash: "public_commit",
          message: null
        }]));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/agent-config/public/worktrees") {
        const linuxServerId = url.searchParams.get("linuxServerId") ?? "";
        await route.fulfill(json(capture.publicAgentWorktreesByServer?.[linuxServerId] ?? []));
        return;
      }
      if (method === "POST" && url.pathname === "/api/internal/platform/workspace-management/agent-config/file-ws-route") {
        const body = JSON.parse(route.request().postData() ?? "{}") as {
          scope?: string;
          workspaceId?: string;
          worktreeId?: string;
          linuxServerId?: string;
        };
        await route.fulfill(json({
          scope: body.scope ?? "PUBLIC",
          workspaceId: body.workspaceId,
          worktreeId: body.worktreeId,
          linuxServerId: body.linuxServerId ?? "10.8.0.12",
          baseUrl: "http://127.0.0.1:8080",
          webSocketPath: "/api/internal/platform/workspace-management/file/ws",
          sameServer: true,
          message: null
        }));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/backend-servers") {
        await route.fulfill(json([
          {
            linuxServerId: "10.8.0.12",
            name: "dev-backend",
            baseUrl: "http://127.0.0.1:8080",
            webSocketPath: "/api/internal/platform/workspace-management/file/ws",
            defaultDirectory: "/Users/huang/workspace",
            sameAsAgent: true
          }
        ]));
        return;
      }
      if (method === "POST" && url.pathname === "/api/internal/platform/workspace-management/file-ws/tickets") {
        await route.fulfill(json({
          ticket: "wft_e2e",
          expiresAt: "2026-06-19T00:01:00Z",
          webSocketUrl: "/api/internal/platform/workspace-management/file/ws?ticket=wft_e2e"
        }));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/applications") {
        await route.fulfill(json(managedApplications));
        return;
      }
      if (url.pathname === "/api/internal/platform/workspace-management/recent-app-source") {
        if (method === "GET") {
          capture.appSourceRequests?.push("recent:get");
          const scripted = capture.appSourceRecentGetResponses?.shift();
          if (scripted?.failure) {
            await route.fulfill({
              status: scripted.failure.status,
              ...jsonFailure(scripted.failure.code, scripted.failure.message)
            });
            return;
          }
          if (scripted) {
            await route.fulfill(json(scripted.value ?? null));
            return;
          }
          await route.fulfill(json(capture.recentAppSource ?? null));
          return;
        }
        if (method === "DELETE") {
          capture.clearedRecentAppSource?.push("DELETE");
          capture.recentAppSource = null;
          await route.fulfill({ status: 204, headers: corsHeaders() });
          return;
        }
      }
      const appSourceRepositoryListMatch = url.pathname.match(
        /^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/app-source-repositories$/
      );
      if (method === "GET" && appSourceRepositoryListMatch) {
        const appId = decodeURIComponent(appSourceRepositoryListMatch[1] ?? "");
        capture.appSourceRequests?.push(`list:${appId}`);
        await capture.appSourceRepositoryListGates?.[appId];
        await route.fulfill(json(capture.appSourceRepositories?.[appId] ?? []));
        return;
      }
      const appSourceOpenMatch = url.pathname.match(
        /^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/app-source-repositories\/([^/]+)\/open$/
      );
      if (method === "POST" && appSourceOpenMatch) {
        const appId = decodeURIComponent(appSourceOpenMatch[1] ?? "");
        const repositoryId = decodeURIComponent(appSourceOpenMatch[2] ?? "");
        const body = JSON.parse(route.request().postData() ?? "{}") as { generation?: number };
        const key = `${appId}:${repositoryId}:${body.generation ?? ""}`;
        capture.appSourceRequests?.push(`open:${key}`);
        await capture.appSourceOpenGates?.[key];
        const failure = capture.appSourceOpenFailures?.[key];
        if (failure) {
          await route.fulfill({ status: failure.status, ...jsonFailure(failure.code, failure.message) });
          return;
        }
        const result = capture.appSourceOpenResults?.[key];
        if (!result) {
          await route.fulfill({ status: 404, ...jsonFailure("NOT_FOUND", "源码 generation 不存在") });
          return;
        }
        capture.recentAppSource = result;
        await route.fulfill(json(result));
        return;
      }
      const appSourceOperationMatch = url.pathname.match(
        /^\/api\/internal\/platform\/workspace-management\/app-source-operations\/([^/]+)(?:\/(ticket))?$/
      );
      if (appSourceOperationMatch) {
        const operationId = decodeURIComponent(appSourceOperationMatch[1] ?? "");
        if (method === "GET" && !appSourceOperationMatch[2]) {
          capture.appSourceRequests?.push(`operation:${operationId}`);
          const requestIndex = appSourceOperationRequestCounts[operationId] ?? 0;
          appSourceOperationRequestCounts[operationId] = requestIndex + 1;
          await capture.appSourceOperationRequestGates?.[operationId]?.[requestIndex];
          const configured = capture.appSourceOperationSnapshots?.[operationId];
          const snapshot = Array.isArray(configured)
            ? (configured.length > 1 ? configured.shift() : configured[0])
            : configured;
          if (!snapshot) {
            await route.fulfill({ status: 404, ...jsonFailure("NOT_FOUND", "源码任务不存在") });
            return;
          }
          await route.fulfill(json(snapshot));
          return;
        }
        if (method === "POST" && appSourceOperationMatch[2] === "ticket") {
          capture.appSourceTicketRequests?.push(operationId);
          capture.appSourceTicketRequestTimes?.push(Date.now());
          const ticketNumber = capture.appSourceTicketRequests?.length ?? 1;
          await route.fulfill(json({
            ticket: `ast_${ticketNumber}`,
            expiresAt: "2026-07-28T12:30:00Z",
            webSocketUrl: `/mock/app-source-progress?ticket=ast_${ticketNumber}`
          }));
          return;
        }
      }
      const appSourceRepositoryActionMatch = url.pathname.match(
        /^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/app-source-repositories\/([^/]+)\/(branches|tree|materializations|replica-retries)$/
      );
      if (appSourceRepositoryActionMatch) {
        const appId = decodeURIComponent(appSourceRepositoryActionMatch[1] ?? "");
        const repositoryId = decodeURIComponent(appSourceRepositoryActionMatch[2] ?? "");
        const action = appSourceRepositoryActionMatch[3];
        const repositoryKey = `${appId}:${repositoryId}`;
        if (method === "GET" && action === "branches") {
          capture.appSourceRequests?.push(`branches:${repositoryKey}`);
          await capture.appSourceBranchGates?.[repositoryKey];
          await route.fulfill(json(capture.appSourceBranches?.[repositoryKey] ?? []));
          return;
        }
        if (method === "GET" && action === "tree") {
          const branch = url.searchParams.get("branch") ?? "";
          const path = url.searchParams.get("path") ?? ".";
          const treeKey = `${repositoryKey}:${branch}:${path}`;
          capture.appSourceRequests?.push(`tree:${treeKey}`);
          await capture.appSourceTreeGates?.[treeKey];
          await route.fulfill(json(capture.appSourceTreeSnapshots?.[treeKey] ?? {
            targetCommit: "",
            nodes: []
          }));
          return;
        }
        if (method === "POST" && action === "materializations") {
          const payload = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
          capture.appSourceMaterializationRequests?.push({ key: repositoryKey, payload });
          const operation = capture.appSourceMaterializationResults?.[repositoryKey];
          if (!operation) {
            await route.fulfill({ status: 409, ...jsonFailure("APP_SOURCE_OPERATION_REJECTED", "源码任务未配置") });
            return;
          }
          await route.fulfill(json(operation));
          return;
        }
        if (method === "POST" && action === "replica-retries") {
          const payload = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
          capture.appSourceRetryRequests?.push({ key: repositoryKey, payload });
          const operation = capture.appSourceRetryResults?.[repositoryKey];
          if (!operation) {
            await route.fulfill({ status: 409, ...jsonFailure("CONFLICT", "源码重试任务未配置") });
            return;
          }
          await route.fulfill(json(operation));
          return;
        }
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/recent-workspace") {
        await route.fulfill(json(null));
        return;
      }
      if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/applications/app_gcms/recent-workspace") {
        const forbidden = capture.forbiddenRecentWorkspaces?.app_gcms;
        if (forbidden) {
          await route.fulfill({
            status: forbidden.status ?? 403,
            ...jsonFailure(forbidden.code, forbidden.message, forbidden.details)
          });
          return;
        }
        await route.fulfill(json(capture.recentWorkspaces?.app_gcms ?? workspace()));
        return;
      }
      const recentWorkspaceMatch = url.pathname.match(/^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/recent-workspace$/);
      if (method === "GET" && recentWorkspaceMatch) {
        const appId = recentWorkspaceMatch[1] ?? "";
        const forbidden = capture.forbiddenRecentWorkspaces?.[appId];
        if (forbidden) {
          await route.fulfill({
            status: forbidden.status ?? 403,
            ...jsonFailure(forbidden.code, forbidden.message, forbidden.details)
          });
          return;
        }
        await route.fulfill(json(capture.recentWorkspaces?.[appId] ?? null));
        return;
      }
      if (method === "POST" && /^\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/recent$/.test(url.pathname)) {
        const workspaceId = url.pathname.match(/\/workspaces\/([^/]+)\/recent$/)?.[1] ?? "";
        capture.markRecentRequests?.push(workspaceId);
        const failure = capture.markRecentFailures?.[workspaceId];
        if (failure) {
          await route.fulfill({
            status: failure.status ?? 403,
            ...jsonFailure(failure.code, failure.message, failure.details)
          });
          return;
        }
        await route.fulfill(json(capture.markRecentWorkspaces?.[workspaceId] ?? null));
        return;
      }
      const workspaceTemplatesMatch = url.pathname.match(/^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/workspace-templates$/);
      if (method === "GET" && workspaceTemplatesMatch) {
        const appId = workspaceTemplatesMatch[1] ?? "";
        await route.fulfill(json(capture.workspaceTemplates?.[appId] ?? []));
        return;
      }
      const workspaceVersionsMatch = url.pathname.match(/^\/api\/internal\/platform\/workspace-management\/applications\/([^/]+)\/workspace-templates\/([^/]+)\/versions$/);
      if (method === "GET" && workspaceVersionsMatch) {
        const appId = workspaceVersionsMatch[1] ?? "";
        const templateId = url.pathname.match(/\/workspace-templates\/([^/]+)\/versions$/)?.[1] ?? "";
        await route.fulfill(json(capture.workspaceVersions?.[`${appId}:${templateId}`] ?? []));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/applications\/app_gcms\/workspace-templates\/[^/]+\/versions$/.test(url.pathname)) {
        // 拦截「+新增版本」请求：捕获 payload，返回一个伪 ApplicationWorkspaceVersion 供前端刷新菜单使用。
        const body = JSON.parse(route.request().postData() ?? "{}") as { version?: string };
        capture.createVersionRequests ??= [];
        capture.createVersionRequests.push(body.version ?? "");
        await route.fulfill(json({
          versionId: "awv_new",
          applicationWorkspaceId: "awp_1",
          appId: "app_gcms",
          repositoryId: "repo_1",
          version: body.version ?? "2024年1月",
          branch: "feature_testagent_" + (body.version ?? "2024年1月"),
          repoRootPath: "/tmp/test-agent/appworkspace/new/repo_1",
          workspaceRootPath: "/tmp/test-agent/appworkspace/new/repo_1/F-GCMS/workspace",
          runtimeWorkspace: workspace(),
          status: "ACTIVE",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        }));
        return;
      }
      if (method === "GET" && /\/api\/internal\/platform\/workspace-management\/workspace-versions\/[^/]+\/git-access$/.test(url.pathname)) {
        const versionId = url.pathname.match(/\/workspace-versions\/([^/]+)\/git-access$/)?.[1] ?? "";
        capture.gitAccessRequests?.push(versionId);
        await route.fulfill(json(capture.gitAccessResults?.[versionId] ?? {
          accessible: true,
          repositoryId: "repo_1",
          repositoryName: "F-GCMS 测试版本库",
          branch: "feature_testagent_20260715",
          reason: null
        }));
        return;
      }
      if (method === "GET" && /\/api\/internal\/platform\/workspace-management\/workspace-versions\/[^/]+\/personal-workspaces$/.test(url.pathname)) {
        const versionId = url.pathname.match(/\/workspace-versions\/([^/]+)\/personal-workspaces$/)?.[1] ?? "";
        capture.personalWorkspaceRequests?.push(versionId);
        await route.fulfill(json(capture.personalWorkspaces?.[versionId] ?? []));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/workspace-versions\/[^/]+\/ensure-default-personal-workspace$/.test(url.pathname)) {
        const versionId = url.pathname.match(/\/workspace-versions\/([^/]+)\/ensure-default-personal-workspace$/)?.[1] ?? "";
        capture.defaultPersonalRequests?.push(versionId);
        if (capture.ensureDefaultRequiresReady && currentProcessStatus !== "READY") {
          await route.fulfill({
            status: 409,
            ...jsonFailure("OPENCODE_PROCESS_STARTING", "TestAgent 进程正在启动")
          });
          return;
        }
        await route.fulfill(json({
          personalWorkspaceId: "psw_default",
          personalWorkspaceName: "default",
          personalWorkspaceBranch: "feature_testagent_20260715_usr_admin_default",
          runtimeWorkspace: {
            ...workspace(),
            workspaceId: "wrk_personal_default",
            name: "default",
            rootPath: "/Users/huang/workspace/personal-default",
            appId: "app_gcms",
            versionId,
            applicationWorkspaceId: "awp_1"
          }
        }));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/personal-workspaces\/[^/]+\/sync-from-application$/.test(url.pathname)) {
        const personalWorkspaceId = url.pathname.match(/\/personal-workspaces\/([^/]+)\/sync-from-application$/)?.[1] ?? "";
        capture.runtimeReloadRequests?.push(`sync:${personalWorkspaceId}`);
        await route.fulfill(json({
          syncRecordId: "wsy_agent_update",
          status: "SUCCEEDED",
          files: [],
          force: false
        }));
        return;
      }
      if (method === "GET" && /\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/git-diff$/.test(url.pathname)) {
        capture.gitDiffRequests?.push(`${method} ${url.pathname}`);
        await route.fulfill(json({ files: capture.historyDiffFiles ?? [] }));
        return;
      }
      if (method === "POST" && /\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/git-discard$/.test(url.pathname)) {
        await route.fulfill(json(null));
        return;
      }
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/processes/me") {
      capture.processStatusRequests?.push(`${method} ${url.pathname}`);
      await route.fulfill(json(opencodeProcessStatus(currentProcessStatus, capture.processServiceStatus)));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/global/dispose") {
      capture.runtimeReloadRequests?.push("dispose");
      await route.fulfill(json(true));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/processes/me/health") {
      await route.fulfill(json({
        healthy: currentProcessStatus === "READY",
        status: currentProcessStatus === "READY" ? "HEALTHY" : "UNHEALTHY",
        message: currentProcessStatus === "READY" ? "TestAgent 进程可用" : "TestAgent 进程不可用",
        linuxServerId: url.searchParams.get("linuxServerId") ?? "server-a",
        containerId: url.searchParams.get("containerId") ?? "ctr_01",
        port: Number(url.searchParams.get("port") ?? 4096),
        checkedAt: "2026-07-10T00:00:00Z"
      }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/processes/me/initialize") {
      capture.processInitializations?.push({});
      if (capture.initializeFailureThenReady) {
        currentProcessStatus = "READY";
        await route.fulfill({
          status: 409,
          ...jsonFailure("OPENCODE_PROCESS_STARTING", "TestAgent 进程正在启动")
        });
        return;
      }
      currentProcessStatus = "READY";
      await route.fulfill(json(opencodeProcessStatus(currentProcessStatus, capture.processServiceStatus)));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/workspace-management/workspaces") {
      await route.fulfill(json(pageOf(workspaceItems)));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+\/file-ws-route$/.test(url.pathname)) {
      const workspaceId = url.pathname.match(/\/api\/internal\/platform\/workspace-management\/workspaces\/([^/]+)\/file-ws-route$/)?.[1] ?? "";
      await route.fulfill(json({
        workspaceId,
        linuxServerId: "10.8.0.12",
        baseUrl: "http://127.0.0.1:8080",
        webSocketPath: "/api/internal/platform/workspace-management/file/ws",
        sameServer: true,
        message: null
      }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/workspace-management\/workspaces\/[^/]+$/.test(url.pathname)) {
      const workspaceId = url.pathname.match(/\/api\/internal\/platform\/workspace-management\/workspaces\/([^/]+)$/)?.[1];
      if (workspaceId) {
        capture.workspaceRequests?.push(workspaceId);
        await capture.workspaceRequestGates?.[workspaceId];
      }
      await route.fulfill(json(workspaceItems.find((item) => item.workspaceId === workspaceId) ?? workspace()));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/file/content") {
      const path = url.searchParams.get("path") ?? "";
      await route.fulfill(json({
        type: "file",
        content: capture.fileContents?.[path] ?? ""
      }));
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files")) {
      await route.fulfill({ status: 500, ...json({ error: "workspace files must use websocket" }) });
      return;
    }
    if (method === "GET" && url.pathname.endsWith("/files/content")) {
      await route.fulfill({ status: 500, ...json({ error: "workspace files must use websocket" }) });
      return;
    }
    if (method === "PUT" && url.pathname.endsWith("/files/content")) {
      await route.fulfill({ status: 500, ...json({ error: "workspace files must use websocket" }) });
      return;
    }
    if (method === "GET" && /\/api\/internal\/platform\/opencode-runtime\/workspaces\/[^/]+\/sessions$/.test(url.pathname)) {
      await route.fulfill(json(pageOf(capture.sessions ?? [])));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/night-execution/slots") {
      await route.fulfill(json({
        timeZone: "Asia/Shanghai",
        windowStart: "2026-07-18T13:00:00Z",
        windowEnd: "2026-07-18T23:00:00Z",
        capacity: 2,
        slots: [{
          slotStart: "2026-07-18T13:15:00Z",
          slotEnd: "2026-07-18T13:30:00Z",
          reservedCount: 0,
          capacity: 2,
          available: true,
          recommended: true
        }]
      }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/night-execution/tasks") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.nightTaskRequests?.push(request);
      const taskId = `net_e2e_${nightTasks.length + 1}`;
      const createdSessionId = request.sessionId == null && request.batchContext != null
        ? `ses_night_created_${nightTasks.length + 1}`
        : String(request.sessionId ?? "ses_night_created");
      const scheduleMode = String(request.scheduleMode ?? "NIGHT_WINDOW");
      const slotStart = String(request.slotStart ?? "2026-07-18T13:15:00Z");
      const slotStartMillis = new Date(slotStart).getTime();
      const task = {
        taskId,
        sessionId: createdSessionId,
        workspaceId: String(request.workspaceId ?? "wrk_1234567890abcdef"),
        sessionTitle: String(request.sessionTitle ?? "夜间任务"),
        contentPreview: String(request.prompt ?? "夜间任务"),
        scheduleMode,
        status: "SCHEDULED",
        slotStart,
        slotEnd: scheduleMode === "ADMIN_CUSTOM"
          ? new Date(slotStartMillis + 60_000).toISOString()
          : "2026-07-18T13:30:00Z",
        windowEnd: scheduleMode === "ADMIN_CUSTOM"
          ? new Date(slotStartMillis + 15 * 60_000).toISOString()
          : "2026-07-18T23:00:00Z",
        rolloverCount: 0,
        runId: null,
        errorCode: null,
        errorMessage: null,
        createdAt: "2026-07-18T04:00:00Z",
        updatedAt: "2026-07-18T04:00:00Z"
      };
      nightTasks.push(task);
      await route.fulfill(json(task));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/batch-items") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.batchSessionRequests?.push(request);
      const requestIndex = capture.batchSessionRequests?.length ?? 1;
      await route.fulfill(json({
        ...session(),
        sessionId: `ses_batch_${requestIndex}`,
        title: String(request.title ?? "批量测试案例")
      }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/night-execution/tasks") {
      const sessionId = url.searchParams.get("sessionId");
      const pending = nightTasks.filter((task) =>
        (task.status === "SCHEDULED" || task.status === "DISPATCHING")
        && (!sessionId || task.sessionId === sessionId));
      const visibleFailure = sessionId
        ? nightTasks.find((task) => task.sessionId === sessionId && task.status === "FAILED") ?? null
        : null;
      const pageNumber = Math.max(Number(url.searchParams.get("page") ?? "1"), 1);
      const pageSize = Math.max(Number(url.searchParams.get("size") ?? "100"), 1);
      const start = (pageNumber - 1) * pageSize;
      await route.fulfill(json({
        items: pending.slice(start, start + pageSize),
        page: pageNumber,
        size: pageSize,
        total: pending.length,
        visibleFailure
      }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions") {
      capture.sessionRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await capture.sessionRequestGate;
      await route.fulfill(json(session()));
      return;
    }
    if (method === "PATCH" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+$/.test(url.pathname)) {
      const sessionId = decodeURIComponent(url.pathname.match(/\/sessions\/([^/]+)$/)?.[1] ?? "ses_1");
      const payload = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.sessionUpdateRequests?.push({ sessionId, payload });
      const current = capture.sessions?.find((item) => item.sessionId === sessionId) ?? session();
      const updated = {
        ...current,
        sessionId,
        title: typeof payload.title === "string" ? payload.title : current.title,
        ...(typeof payload.pinned === "boolean" ? { pinned: payload.pinned } : {})
      };
      const currentIndex = capture.sessions?.findIndex((item) => item.sessionId === sessionId) ?? -1;
      if (currentIndex >= 0 && capture.sessions) {
        capture.sessions[currentIndex] = updated;
      }
      await route.fulfill(json(updated));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+$/.test(url.pathname)) {
      const sessionId = decodeURIComponent(url.pathname.match(/\/sessions\/([^/]+)$/)?.[1] ?? "ses_1");
      const nightTask = nightTasks.find((task) => task.sessionId === sessionId);
      const configuredSession = (capture.sessions ?? []).find((item) => item.sessionId === sessionId);
      if (!nightTask && !configuredSession && capture.missingSessionsNotFound) {
        await route.fulfill({ status: 404, ...jsonFailure("SESSION_NOT_FOUND", "Session 不存在") });
        return;
      }
      await route.fulfill(json(nightTask ? {
        sessionId,
        workspaceId: nightTask.workspaceId,
        title: nightTask.sessionTitle,
        status: "ACTIVE",
        sourceType: "SCHEDULED_TASK",
        sourceRefId: nightTask.taskId,
        createdAt: "2026-07-18T04:00:00Z",
        updatedAt: "2026-07-18T04:00:00Z"
      } : configuredSession ?? session()));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/sessions") {
      await route.fulfill(json(pageOf(capture.sessions ?? [])));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/runtime-state") {
      capture.runtimeStateHttpRequests?.push(url.pathname);
      await route.fulfill(json({ runningCount: 0, questionCount: 0, sessions: [], generatedAt: "2026-07-10T00:00:00Z" }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/runtime-state/events") {
      capture.runtimeStateEventRequests?.push(url.pathname);
      await capture.runtimeStateEventGate;
      if (capture.runtimeStateStreamFailure) {
        await route.fulfill({ status: 503, ...jsonFailure("RUNTIME_STATE_UNAVAILABLE", "运行态流不可用") });
        return;
      }
      const summary = capture.runtimeStateSummary ?? {
        runningCount: 0,
        questionCount: 0,
        sessions: [],
        generatedAt: "2026-07-10T00:00:00Z"
      };
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: `event: session-runtime.snapshot\ndata: ${JSON.stringify(summary)}\n\n`
      });
      return;
    }
    if (method === "POST" && /^\/api\/internal\/agent\/opencode\/sessions\/[^/]+\/run-context$/.test(url.pathname)) {
      const sessionId = decodeURIComponent(url.pathname.match(/\/sessions\/([^/]+)\/run-context$/)?.[1] ?? "ses_1");
      capture.runContextRequests?.push(sessionId);
      await capture.runContextRequestGates?.[sessionId];
      const contextIndex = (capture.runContextRequests?.length ?? 1) - 1;
      await route.fulfill(json({
        contextToken: capture.runContextTokens?.[contextIndex] ?? `ctx_e2e_${contextIndex + 1}`,
        contextVersion: contextIndex + 1,
        expiresAt: "2026-07-11T00:00:00Z"
      }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/agent\/opencode\/sessions\/[^/]+\/resends$/.test(url.pathname)) {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.runResendRequests?.push(request);
      const resend = {
        resendId: "rsd_e2e",
        sourceRunId: String(request.expectedRunId ?? "run_history"),
        replacementRunId: "run_resend_replacement",
        trigger: "MANUAL",
        totalAttempt: 1,
        automaticAttempt: 0,
        automaticLimit: 3,
        status: "WAITING",
        executeAt: "2026-08-07T08:02:00Z"
      };
      await route.fulfill(json({
        replacementRun: {
          runId: "run_resend_replacement",
          sessionId: url.pathname.match(/\/sessions\/([^/]+)\/resends$/)?.[1] ?? "ses_1",
          workspaceId: "wrk_1234567890abcdef",
          status: "PENDING",
          createdAt: "2026-08-07T08:01:30Z",
          updatedAt: "2026-08-07T08:01:30Z",
          resend
        },
        resend
      }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/agent\/opencode\/sessions\/[^/]+\/session-tree\/messages$/.test(url.pathname)) {
      capture.sessionTreeRequests?.push(`${url.pathname}${url.search}`);
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/session-tree\/messages$/)?.[1] ?? "ses_history";
      await route.fulfill(json(capture.sessionTreeMessagesBySessionId?.[sessionId] ?? capture.sessionTreeMessages ?? {
        sessionId,
        sessions: [],
        messagesBySessionId: {},
        childSessionIdByTaskPartId: {},
        events: []
      }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/messages$/.test(url.pathname)) {
      capture.sessionMessageRequests?.push(`${url.pathname}${url.search}`);
      await capture.sessionMessagesGate;
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/messages$/)?.[1] ?? "ses_history";
      await route.fulfill(json(pageOf(capture.sessionMessagesBySessionId?.[sessionId] ?? capture.sessionMessages ?? [])));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/questions$/.test(url.pathname)) {
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/questions$/)?.[1] ?? "";
      await capture.sessionInteractionsGate;
      await route.fulfill(json(capture.sessionQuestionsById?.[sessionId] ?? []));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/permissions$/.test(url.pathname)) {
      const sessionId = url.pathname.match(/\/sessions\/([^/]+)\/permissions$/)?.[1] ?? "";
      await capture.sessionInteractionsGate;
      await route.fulfill(json(capture.sessionPermissionsById?.[sessionId] ?? []));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/questions\/[^/]+\/reply$/.test(url.pathname)) {
      capture.questionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/messages\/[^/]+\/feedback\/me$/.test(url.pathname)) {
      capture.feedbackRequests?.push(url.pathname);
      await capture.messageFeedbackGate;
      await route.fulfill(json(null));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/run-feedbacks/me/query") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.runFeedbackQueryRequests?.push(request);
      const runIds = Array.isArray(request.runIds) ? request.runIds.filter((runId): runId is string => typeof runId === "string") : [];
      await route.fulfill(json(runIds.map((runId) => ({ runId, runStatus: "SUCCEEDED", feedback: null }))));
      return;
    }
    if (method === "GET" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/active-run$/.test(url.pathname)) {
      capture.activeRunRequests?.push(url.pathname);
      await capture.activeRunRequestGate;
      await route.fulfill(json(capture.activeRun ?? null));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/runs/run_history") {
      capture.historyRunRequests?.push(url.pathname);
      await capture.historyRunGate;
      await route.fulfill(json(capture.historyRun ?? {}));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/runs/run_history/diff") {
      await route.fulfill(json({ runId: "run_history", files: capture.historyDiffFiles ?? [] }));
      return;
    }
    const runDetailMatch = url.pathname.match(/^\/api\/internal\/agent\/opencode\/runs\/([^/]+)$/);
    if (method === "GET" && runDetailMatch) {
      const runId = decodeURIComponent(runDetailMatch[1] ?? "");
      capture.runDetailRequests?.push(runId);
      const remainingFailures = capture.runDetailFailuresBeforeSuccess?.[runId] ?? 0;
      if (remainingFailures > 0) {
        capture.runDetailFailuresBeforeSuccess![runId] = remainingFailures - 1;
        await route.fulfill({ status: 503, ...jsonFailure("RUN_DETAIL_UNAVAILABLE", "Run 详情暂不可用") });
        return;
      }
      const detail = capture.runsByRunId?.[runId];
      if (detail) {
        await route.fulfill(json(detail));
      } else {
        await route.fulfill({ status: 404, ...jsonFailure("RUN_NOT_FOUND", "Run 不存在") });
      }
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/side-question\/runs$/.test(url.pathname)) {
      capture.sideQuestionRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      const requestIndex = (capture.sideQuestionRequests?.length ?? 1) - 1;
      const runId = capture.sideQuestionRunIds?.[requestIndex] ?? `run_side_question_${requestIndex + 1}`;
      await route.fulfill(json({ runId }));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/agents") {
      const workspaceId = url.searchParams.get("workspaceId") ?? "";
      capture.agentRequests?.push(`${method} ${url.pathname}${url.search}`);
      await capture.agentGatesByWorkspace?.[workspaceId];
      const queued = agentResponses.length > 0 ? agentResponses.shift() : undefined;
      if (queued && !Array.isArray(queued)) {
        await route.fulfill({
          status: queued.status ?? 500,
          ...jsonFailure(queued.code, queued.message, queued.details)
        });
        return;
      }
      const agents = queued ?? capture.agentsByWorkspace?.[workspaceId] ?? capture.agents ?? [{ id: "build", name: "Build" }];
      await route.fulfill(json(agents));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/models") {
      const workspaceId = url.searchParams.get("workspaceId");
      capture.runtimeCatalogRequests?.push({ path: url.pathname, workspaceId, shareId: sessionShareHeader ?? null });
      const expectedSharedWorkspaceId = typeof capture.sessionShareAccess?.workspaceId === "string"
        ? capture.sessionShareAccess.workspaceId
        : null;
      if (sessionShareHeader && expectedSharedWorkspaceId && workspaceId !== expectedSharedWorkspaceId) {
        await route.fulfill({ status: 403, ...jsonFailure("FORBIDDEN", "分享请求必须绑定精确会话或工作区") });
        return;
      }
      await route.fulfill(json(capture.modelResponses?.shift() ?? capture.models ?? [
        { id: "sonnet", providerId: "anthropic", name: "Sonnet" },
        { id: "opus", providerId: "anthropic", name: "Opus" },
        { id: "glm-5.2", providerId: "volcengine", name: "GLM-5.2" },
        { id: "north-mini-code", providerId: "opencode-zen", name: "North Mini Code Free", free: true }
      ]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/providers") {
      const workspaceId = url.searchParams.get("workspaceId");
      capture.runtimeCatalogRequests?.push({ path: url.pathname, workspaceId, shareId: sessionShareHeader ?? null });
      const expectedSharedWorkspaceId = typeof capture.sessionShareAccess?.workspaceId === "string"
        ? capture.sessionShareAccess.workspaceId
        : null;
      if (sessionShareHeader && expectedSharedWorkspaceId && workspaceId !== expectedSharedWorkspaceId) {
        await route.fulfill({ status: 403, ...jsonFailure("FORBIDDEN", "分享请求必须绑定精确会话或工作区") });
        return;
      }
      await route.fulfill(json(capture.providers ?? [
        { id: "anthropic", name: "Anthropic", status: "ready" },
        { id: "volcengine", name: "Volcengine Ark", status: "ready" },
        { id: "opencode-zen", name: "OpenCode Zen", status: "ready" }
      ]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/commands") {
      await route.fulfill(json([{ id: "test", name: "test", description: "Run tests" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/mcp/resources") {
      await route.fulfill(json([{ id: "issue-1", name: "Issue 1", uri: "mcp://issue/1", type: "issue" }]));
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/platform/opencode-runtime/mcp/tools") {
      await route.fulfill(json(["bash"]));
      return;
    }
    if (
      method === "GET" &&
      [
        "/api/internal/platform/opencode-runtime/lsp/status",
        "/api/internal/platform/opencode-runtime/mcp/status",
        "/api/internal/platform/opencode-runtime/vcs/status"
      ].includes(url.pathname)
    ) {
      await route.fulfill(json(capture.vcsStatus ?? { status: "ready", branch: "main", defaultBranch: "main" }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/agent/opencode/runs") {
      const request = JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>;
      capture.runRequests?.push(request);
      const requestIndex = (capture.runRequests?.length ?? 1) - 1;
      await (capture.runRequestGates?.[requestIndex] ?? capture.runRequestGate);
      const failureResponse = capture.runFailureResponses?.shift();
      if (failureResponse) {
        await route.fulfill({
          status: failureResponse.status,
          ...jsonFailure(failureResponse.code, failureResponse.message)
        });
        return;
      }
      const failureCode = capture.runFailures?.shift();
      if (failureCode) {
        await route.fulfill({ status: 409, ...jsonFailure(failureCode, "会话运行上下文已失效") });
        return;
      }
      const runId = capture.runIds?.[requestIndex] ?? "run_1";
      await route.fulfill(json({
        runId,
        sessionId: String(request.sessionId ?? "ses_1"),
        workspaceId: "wrk_1234567890abcdef",
        status: "RUNNING",
        clientRequestId: request.clientRequestId,
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/agent\/opencode\/runs\/[^/]+\/cancel$/.test(url.pathname)) {
      const runId = url.pathname.match(/\/runs\/([^/]+)\/cancel$/)?.[1] ?? "";
      capture.cancelRunRequests?.push(runId);
      await route.fulfill(json({
        runId,
        sessionId: "ses_1",
        workspaceId: "wrk_1234567890abcdef",
        status: "CANCELLED",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:01Z"
      }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/agent\/opencode\/session\/[^/]+\/command$/.test(url.pathname)) {
      capture.commandRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/compact$/.test(url.pathname)) {
      capture.compactRequests?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await capture.compactRequestGate;
      await route.fulfill(json(true));
      return;
    }
    const runEventsMatch = url.pathname.match(/^\/api\/internal\/agent\/opencode\/runs\/([^/]+)\/events$/);
    if (method === "GET" && runEventsMatch) {
      const runId = runEventsMatch[1] ?? "run_1";
      capture.runEventRequests?.push(url.pathname);
      const runEvents = capture.runEventsByRunId?.[runId] ?? capture.runEvents;
      await route.fulfill({
        status: 200,
        headers: { ...corsHeaders(), "Content-Type": "text/event-stream", "Cache-Control": "no-cache" },
        body: sse(runEvents ?? [
          event(1, "permission.asked", { requestId: "perm_1", sessionId: "ses_1", title: "Run bash", description: "Allow npm test?" }),
          event(2, "question.asked", {
            requestId: "ques_1",
            sessionId: "ses_1",
            questions: [{ id: "q1", text: "Need target env?", kind: "text" }]
          }),
          event(3, "diff.proposed", { files: [diffFile()] }),
          event(4, "run.succeeded", {})
        ])
      });
      return;
    }
    if (method === "GET" && url.pathname === "/api/internal/agent/opencode/runs/run_1/diff") {
      await route.fulfill(json({ runId: "run_1", files: [diffFile()] }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/ses_1/permissions/perm_1/reply") {
      capture.permissionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && /^\/api\/internal\/platform\/opencode-runtime\/sessions\/[^/]+\/permissions\/[^/]+\/reply$/.test(url.pathname)) {
      capture.permissionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/ses_1/questions/ques_1/reply") {
      capture.questionReplies?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({ accepted: true }));
      return;
    }
    if (method === "POST" && url.pathname === "/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/tickets") {
      capture.terminalTickets?.push(JSON.parse(route.request().postData() ?? "{}") as Record<string, unknown>);
      await route.fulfill(json({
        ticket: "pty_123",
        expiresAt: "2026-06-19T13:00:00Z",
        webSocketUrl: "/api/internal/platform/opencode-runtime/sessions/ses_1/terminal/ws?ticket=pty_123"
      }));
      return;
    }
    await route.fulfill(json({}));
  });
}

async function callAgentWorkbenchHandler(
  page: Page,
  handler: string,
  args: unknown[] = []
) {
  await page.evaluate(async ({ handlerName, handlerArgs }) => {
    type VueInternalInstance = {
      parent?: VueInternalInstance | null;
      setupState?: Record<string, unknown>;
    };
    let instance: VueInternalInstance | null | undefined = (document.querySelector(".managed-workspace-layout") as (HTMLElement & {
      __vueParentComponent?: VueInternalInstance;
    }) | null)?.__vueParentComponent;
    while (instance && typeof instance.setupState?.[handlerName] !== "function") {
      instance = instance.parent;
    }
    const target = instance?.setupState?.[handlerName];
    if (typeof target !== "function") throw new Error(`AgentWorkbench.${handlerName} not found`);
    await target(...handlerArgs);
  }, { handlerName: handler, handlerArgs: args });
}

async function emitDiffViewerSave(page: Page, path: string, content: string) {
  await page.evaluate(({ filePath, fileContent }) => {
    type VueInternalInstance = { emit?: (event: string, ...args: unknown[]) => void };
    const root = document.querySelector(".managed-editor-main > div.flex-1 > div") as (HTMLElement & {
      __vueParentComponent?: VueInternalInstance;
    }) | null;
    if (!root?.__vueParentComponent?.emit) throw new Error("DiffViewer instance not found");
    root.__vueParentComponent.emit("saveFile", filePath, fileContent);
  }, { filePath: path, fileContent: content });
}

async function gotoWorkbench(page: Page, options: { selectConversation?: boolean } = {}) {
  await page.goto("/workbench", { waitUntil: "domcontentloaded" });
  // 工作台会并行加载用户、应用、工作区和运行态目录；先等外围壳挂载，再开始交互，避免慢机器下把初始化竞态误报为功能失败。
  await page.locator(".figma-app").waitFor({ state: "visible", timeout: 20_000 }).catch(() => undefined);
  if (options.selectConversation === false) return;
  const newConversationButton = page.getByRole("button", { name: "新建对话" });
  // 部分用例会被路由到登录或只读页面；只有工作台实际渲染该入口时才进入新对话草稿。
  const buttonVisible = await newConversationButton
    .waitFor({ state: "visible", timeout: 20_000 })
    .then(() => true)
    .catch(() => false);
  if (buttonVisible && await newConversationButton.isEnabled()) {
    await newConversationButton.click();
  }
}

/** 会话卡片主按钮与置顶按钮并列；历史切换必须限定主按钮，避免可访问名称包含同一标题时产生歧义。 */
function historySessionButton(page: Page, title: string | RegExp) {
  return page.locator(".figma-chat-history-card-main").filter({ hasText: title });
}

/** Agents 为产品默认收起区；需要操作 Agent 树的用例必须显式展开，避免依赖旧版默认状态。 */
async function openAgentsPanel(page: Page) {
  const agentsButton = page.getByRole("button", { name: "Agents", exact: true });
  await expect(agentsButton).toBeVisible({ timeout: 20_000 });
  await agentsButton.click();
  await expect(page.locator(".agent-root-row").first()).toBeVisible({ timeout: 20_000 });
}

/** 通过统一工作空间入口打开源码管理弹窗，覆盖首次下载和更新等管理流程。 */
async function openAppSourceFromWorkspaceSwitch(page: Page) {
  const fileExplorer = page.locator(".figma-file-explorer");
  await fileExplorer.getByRole("button", { name: "切换应用代码库或测试工作空间" }).click();
  await page.getByRole("menu").getByRole("button", { name: "管理应用代码库" }).click();
}

/** 菜单直列源码版本库；点击指定版本库必须直接打开，不经过源码选择弹窗。 */
async function openAppSourceRepositoryFromWorkspaceSwitch(page: Page, repositoryName: string) {
  const fileExplorer = page.locator(".figma-file-explorer");
  await fileExplorer.getByRole("button", { name: "切换应用代码库或测试工作空间" }).click();
  await page.getByRole("menu").getByRole("button", { name: `打开${repositoryName}源码` }).click();
}

/** 未下载版本库在菜单中保持灰色可点击，点击后直接进入并选中对应管理项。 */
async function openAppSourceManagementForRepository(page: Page, repositoryName: string) {
  const fileExplorer = page.locator(".figma-file-explorer");
  await fileExplorer.getByRole("button", { name: "切换应用代码库或测试工作空间" }).click();
  await page.getByRole("menu").getByRole("button", { name: `管理${repositoryName}源码` }).click();
}

function json(data: unknown) {
  return {
    contentType: "application/json",
    headers: corsHeaders(),
    body: JSON.stringify({ success: true, traceId: "trace_e2e", data })
  };
}

function jsonFailure(code: string, message: string, details: Record<string, unknown> = {}) {
  return {
    contentType: "application/json",
    headers: corsHeaders(),
    body: JSON.stringify({ success: false, traceId: "trace_e2e", code, message, retryable: true, details })
  };
}

/**
 * E2E mock 用：把 role code 翻译成 mock 后端会返回的 dict_label 形式。
 * 与后端测试 JDBC fixture 的角色 dict_label 保持一致，避免 e2e 与单测走两套命名。
 */
function roleLabelOf(role: string): string {
  switch (role) {
    case "SUPER_ADMIN":
      return "超级管理员";
    case "SYSTEM_ADMIN":
      return "系统管理员";
    case "APP_ADMIN":
      return "应用管理员";
    case "USER":
      return "普通用户";
    default:
      return role;
  }
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, X-Trace-Id, Authorization",
    "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS"
  };
}

function pageOf(items: unknown[]) {
  return { items, page: 0, size: 30, total: items.length };
}

function workspace() {
  return {
    workspaceId: "wrk_1234567890abcdef",
    name: "demo-tests",
    rootPath: "/Users/huang/workspace/demo-tests",
    linuxServerId: "10.8.0.12",
    status: "ACTIVE",
    createdAt: "2026-06-19T00:00:00Z",
    updatedAt: "2026-06-19T00:00:00Z"
  };
}

function workspaceViewDirectoryEntry(id: string, path: string) {
  return {
    id,
    path,
    name: path.split("/").at(-1) ?? path,
    directory: true,
    size: 0,
    lastModifiedAt: "2026-06-19T00:00:00Z",
    locator: { kind: "WORKSPACE", path },
    source: "WORKSPACE",
    merged: false,
    collision: false,
    readonly: false,
    workspacePath: path,
    referenceAliases: []
  };
}

function workspaceViewFileEntry(id: string, path: string) {
  return {
    ...workspaceViewDirectoryEntry(id, path),
    directory: false,
    size: 12
  };
}

function runnableWorkspaceSetup() {
  return {
    recentWorkspaces: {
      app_gcms: {
        ...workspace(),
        appId: "app_gcms",
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1"
      }
    },
    personalWorkspaces: {
      awv_20260715: [defaultPersonalWorkspace("awv_20260715")]
    }
  };
}

function versionSelectionWorkspaceSetup() {
  return {
    workspaceTemplates: {
      app_gcms: [{
        workspaceId: "awp_main",
        workspaceName: "F-GCMS 主服务",
        appId: "app_gcms",
        repositoryId: "repo_1",
        defaultBranch: "main",
        createdAt: "2026-06-24T00:00:00Z",
        updatedAt: "2026-06-24T00:00:00Z"
      }]
    },
    workspaceVersions: {
      "app_gcms:awp_main": [{
        versionId: "awv_2024_01",
        applicationWorkspaceId: "awp_main",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: "2024年1月",
        branch: "feature_testagent_20240101",
        repoRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1",
        workspaceRootPath: "/tmp/test-agent/appworkspace/awp_main/repo_1/F-GCMS/workspace",
        status: "ACTIVE",
        createdAt: "2026-06-24T00:00:00Z",
        updatedAt: "2026-06-24T00:00:00Z"
      }]
    }
  };
}

/** 生成足量工具，让工具盒子滚动容器在短视口下真实产生滚动高度。 */
function toolboxScrollTools() {
  return Array.from({ length: 24 }, (_, index) => ({
    toolId: `it-tools.scroll-${index}`,
    source: "IT_TOOLS",
    sourceName: "IT-Tools",
    sourceVersion: "e2e",
    nameZh: `滚动工具${index + 1}`,
    nameEn: `Scroll tool ${index + 1}`,
    descriptionZh: "用于滚动布局验证",
    category: "SECURITY",
    categoryLabel: "安全与加密",
    keywords: ["scroll"],
    launchPath: `/toolbox/apps/it-tools/scroll-${index + 1}`,
    clickCount: 0,
    hotRank: null
  }));
}

function agentWorkspaceSetup() {
  return {
    ...runnableWorkspaceSetup(),
    // mock 进程固定归属 server-a；公共仓库必须同服，否则安全路由会按设计拒绝浏览。
    publicAgentRepositories: [publicAgentRepository("server-a", "backend-a")],
    workspaceTemplates: {
      app_gcms: [{
        workspaceId: "awp_1",
        workspaceName: "F-GCMS",
        appId: "app_gcms",
        repositoryId: "repo_1",
        defaultBranch: "main",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }]
    },
    workspaceVersions: {
      "app_gcms:awp_1": [{
        versionId: "awv_20260715",
        applicationWorkspaceId: "awp_1",
        appId: "app_gcms",
        repositoryId: "repo_1",
        version: "2026年7月",
        branch: "feature_testagent_20260715",
        repoRootPath: "/Users/huang/workspace/app-feature",
        workspaceRootPath: "/Users/huang/workspace/app-feature/F-GCMS/workspace",
        runtimeWorkspace: {
          ...workspace(),
          workspaceId: "wrk_feature_agent",
          name: "F-GCMS feature",
          rootPath: "/Users/huang/workspace/app-feature/F-GCMS/workspace",
          appId: "app_gcms",
          versionId: "awv_20260715",
          applicationWorkspaceId: "awp_1"
        },
        status: "ACTIVE",
        createdAt: "2026-06-19T00:00:00Z",
        updatedAt: "2026-06-19T00:00:00Z"
      }]
    }
  };
}

function publicAgentRepository(linuxServerId: string, serverName: string) {
  return {
    linuxServerId,
    serverName,
    gitRootPath: `/mock/${linuxServerId}/public-config`,
    configDirPath: `/mock/${linuxServerId}/public-config/opencode`,
    worktreeRootPath: `/mock/${linuxServerId}/public-worktrees`,
    status: "READY",
    initialized: true,
    initializationAllowed: true,
    currentBranch: "main",
    commitHash: `${linuxServerId}_commit`,
    message: null
  };
}

function publicAgentWorktree(linuxServerId: string) {
  return {
    worktreeId: `agw_${linuxServerId}`,
    scope: "PUBLIC",
    workspaceId: null,
    linuxServerId,
    worktreeName: "public-usr_admin",
    branch: "public-usr_admin",
    rootPath: `/mock/${linuxServerId}/public-worktrees/public-usr_admin`,
    agentDirectory: `/mock/${linuxServerId}/public-worktrees/public-usr_admin/opencode`,
    status: "ACTIVE",
    createdAt: "2026-07-17T00:00:00Z",
    updatedAt: "2026-07-17T00:00:00Z",
    createdByUserId: "usr_admin",
    createdByUsername: "admin"
  };
}

function defaultPersonalWorkspace(versionId: string) {
  return {
    personalWorkspaceId: "psw_default",
    versionId,
    appId: "app_gcms",
    applicationWorkspaceId: "awp_1",
    workspaceName: "default",
    branch: "feature_testagent_20260715_usr_admin_default",
    repoRootPath: "/Users/huang/workspace/personal-default",
    workspaceRootPath: "/Users/huang/workspace/personal-default",
    runtimeWorkspace: {
      ...workspace(),
      workspaceId: "wrk_personal_default",
      name: "default",
      rootPath: "/Users/huang/workspace/personal-default",
      appId: "app_gcms",
      versionId,
      applicationWorkspaceId: "awp_1"
    },
    baseCommit: "commit_base",
    status: "ACTIVE",
    createdAt: "2026-06-19T00:00:00Z",
    updatedAt: "2026-06-19T00:00:00Z"
  };
}

function appSourceRepository(overrides: Record<string, unknown> = {}) {
  return {
    repositoryId: "repo-code",
    name: "应用代码库",
    englishName: "application-code",
    downloadState: "DOWNLOADED_ACTIVE",
    generation: 1,
    purpose: "TEAM",
    branch: "main",
    targetCommit: "commit-1",
    selectedPaths: [{ path: "src", type: "DIRECTORY" }],
    expiresAt: "2026-07-30T00:00:00Z",
    occupied: false,
    openable: true,
    manageable: true,
    latestOperation: null,
    serverSummaries: [],
    ...overrides
  };
}

function appSourceOpenResult(workspaceId: string, generation: number, overrides: Record<string, unknown> = {}) {
  return {
    appId: "app_gcms",
    repositoryId: "repo-code",
    generation,
    purpose: "TEAM",
    workspaceId,
    linuxServerId: "10.8.0.12",
    expiresAt: "2026-07-30T00:00:00Z",
    ...overrides
  };
}

function appSourceOperation(
  operationId: string,
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "PARTIAL_FAILED" | "FAILED",
  overrides: Record<string, unknown> = {}
) {
  return {
    operationId,
    appId: "app_gcms",
    repositoryId: "repo-code",
    sourceGeneration: null,
    targetGeneration: 1,
    operationType: "DOWNLOAD",
    status,
    purpose: "TEAM",
    branch: "main",
    targetCommit: "a".repeat(40),
    selectedPaths: [{ path: "src", type: "DIRECTORY" }],
    expiresAt: "2026-07-30T00:00:00Z",
    traceId: `trace_${operationId}`,
    acceptedAt: "2026-07-28T10:00:00Z",
    completedAt: null,
    globalSteps: [],
    serverSummaries: [],
    ...overrides
  };
}

function appSourceProgressEvent(type: "snapshot" | "step" | "completed", operation: Record<string, unknown>) {
  return {
    type,
    operationId: operation.operationId,
    operation,
    traceId: operation.traceId
  };
}

function session() {
  return {
    sessionId: "ses_1",
    workspaceId: "wrk_1234567890abcdef",
    title: "E2E Session",
    status: "ACTIVE",
    createdAt: "2026-06-19T00:00:00Z",
    updatedAt: "2026-06-19T00:00:00Z"
  };
}

function sessionShareAccess(overrides: Record<string, unknown> = {}) {
  return {
    shareId: "shr_readonly",
    version: 4,
    actorUserId: "usr_reader",
    actorUnifiedAuthId: "ucid_reader",
    actorUsername: "阅读者",
    executionOwnerUserId: "usr_owner",
    sessionId: "ses_shared_readonly",
    workspaceId: "wrk_shared_readonly",
    canChat: false,
    delegated: true,
    ownerAccess: false,
    expiresAt: "2026-08-16T00:00:00Z",
    participants: [
      {
        userId: "usr_owner",
        unifiedAuthId: "ucid_owner",
        username: "会话所属人",
        owner: true,
        canChat: true,
        status: "OWNER"
      },
      {
        userId: "usr_reader",
        unifiedAuthId: "ucid_reader",
        username: "阅读者",
        owner: false,
        canChat: false,
        status: "ACTIVE"
      },
      {
        userId: "usr_writer",
        unifiedAuthId: "ucid_writer",
        username: "协作者",
        owner: false,
        canChat: true,
        status: "ACTIVE"
      }
    ],
    ...overrides
  };
}

function sessionShareRuntimeState(overrides: Record<string, unknown> = {}) {
  return {
    active: true,
    reason: null,
    shareId: "shr_readonly",
    version: 4,
    sessionId: "ses_shared_readonly",
    workspaceId: "wrk_shared_readonly",
    canChat: false,
    expiresAt: "2026-08-16T00:00:00Z",
    activeRun: null,
    sessionUpdatedAt: "2026-08-09T00:59:00Z",
    generatedAt: "2026-08-09T01:00:00Z",
    ...overrides
  };
}

async function startHistoryLoadingObservation(page: Page): Promise<void> {
  await page.evaluate(() => {
    const observedWindow = window as Window & {
      __historyLoadingObserved?: boolean;
      __historyLoadingObserver?: MutationObserver;
    };
    observedWindow.__historyLoadingObserver?.disconnect();
    observedWindow.__historyLoadingObserved = false;
    const observer = new MutationObserver(() => {
      if (document.querySelector(".figma-chat-history-loading")) {
        observedWindow.__historyLoadingObserved = true;
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    observedWindow.__historyLoadingObserver = observer;
  });
}

async function historyLoadingObserved(page: Page): Promise<boolean> {
  return page.evaluate(() =>
    (window as Window & { __historyLoadingObserved?: boolean }).__historyLoadingObserved === true
  );
}

function sharedSessionListItem(overrides: Record<string, unknown> = {}) {
  return {
    shareId: "shr_active",
    sharePath: "/s/shr_active",
    sessionId: "ses_shared_list",
    workspaceId: "wrk_shared_list",
    sessionTitle: "分享会话",
    ownerUserId: "usr_owner",
    ownerUnifiedAuthId: "ucid_owner",
    ownerUsername: "会话所属人",
    sharedAt: "2026-08-09T00:00:00Z",
    expiresAt: "2026-08-16T00:00:00Z",
    canChat: true,
    status: "ACTIVE",
    ...overrides
  };
}

function petContextMessage() {
  return {
    messageId: "msg_pet_context",
    remoteMessageId: "msg_remote_pet_context",
    sessionId: "ses_1",
    role: "ASSISTANT",
    content: "当前任务上下文已准备",
    createdAt: "2026-06-19T00:00:00Z",
    runId: "run_history"
  };
}

function diffFile() {
  return {
    path: "src/App.tsx",
    patch: "@@ -1 +1,2 @@ render\n-old();\n+newFlow();\n+assertReady();",
    additions: 2,
    deletions: 1,
    status: "modified"
  };
}

function opencodeProcessStatus(
  status: "READY" | "NEEDS_INITIALIZATION" | "UNAVAILABLE",
  serviceStatus?: "UNASSIGNED" | "NOT_RUNNING"
) {
  if (status === "READY") {
    return {
      status,
      initializable: false,
      message: "TestAgent 进程可用",
      processId: "ocp_1234567890abcdef",
      linuxServerId: "server-a",
      containerId: "ctr_01",
      port: 4096,
      baseUrl: "http://10.8.0.12:4096",
      serviceStatus: "RUNNING",
      serviceAddress: "10.8.0.12:4096",
      checkedAt: "2026-06-24T00:00:00Z"
    };
  }
  return {
    status,
    initializable: status === "NEEDS_INITIALIZATION",
    message: status === "NEEDS_INITIALIZATION" ? "需要初始化 TestAgent 进程" : "没有可用的 TestAgent 容器",
    ...(serviceStatus === "NOT_RUNNING" ? {
      serviceStatus,
      processId: "ocp_1234567890abcdef",
      linuxServerId: "server-a",
      containerId: "ctr_01",
      port: 4096,
      serviceAddress: "10.8.0.12:4096"
    } : serviceStatus ? { serviceStatus } : {}),
    checkedAt: "2026-06-24T00:00:00Z"
  };
}

function event(seq: number, type: string, payload: Record<string, unknown>) {
  return {
    eventId: `evt_${seq}`,
    runId: "run_1",
    seq,
    type,
    traceId: "trace_e2e",
    occurredAt: "2026-06-19T00:00:00Z",
    payload
  };
}

function sse(events: ReturnType<typeof event>[]) {
  return events.map((item) => `event: ${item.type}\ndata: ${JSON.stringify(item)}\n\n`).join("");
}
