import { describe, expect, it } from "vitest";
import { resolveUserMessageAppearance } from "../src/user-message-appearance";

describe("resolveUserMessageAppearance", () => {
  it("uses fixed borderless colors for the current user and everyone else", () => {
    const mine = resolveUserMessageAppearance({
      senderUserId: "usr_me",
      senderUsername: "我自己"
    }, "usr_me");
    const otherFirst = resolveUserMessageAppearance({
      senderUserId: "usr_other",
      senderUsername: "协作者"
    }, "usr_me");
    const otherSecond = resolveUserMessageAppearance({
      senderUserId: "usr_other",
      senderUsername: "协作者"
    }, "usr_someone_else");

    expect(mine).toEqual({
      own: true,
      style: {
        backgroundColor: "#EAF3FD",
        border: "none"
      }
    });
    expect(otherFirst).toEqual({
      own: false,
      displayName: "协作者",
      style: {
        backgroundColor: "var(--ta-chat-other-user-bg, #DED9F6)",
        border: "none"
      }
    });
    expect(otherFirst.style).toEqual(otherSecond.style);
  });

  it("uses the current user's borderless color for legacy messages", () => {
    expect(resolveUserMessageAppearance({}, "usr_me")).toEqual({
      own: true,
      style: {
        backgroundColor: "#EAF3FD",
        border: "none"
      }
    });
  });
});
