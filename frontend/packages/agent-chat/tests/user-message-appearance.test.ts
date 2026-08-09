import { describe, expect, it } from "vitest";
import { resolveUserMessageAppearance } from "../src/user-message-appearance";

describe("resolveUserMessageAppearance", () => {
  it("uses a stable sender color and hides the current user's name", () => {
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

    expect(mine.displayName).toBeUndefined();
    expect(otherFirst.displayName).toBe("协作者");
    expect(otherFirst.style).toEqual(otherSecond.style);
    expect(otherFirst.style.backgroundColor).not.toBe(mine.style.backgroundColor);
  });

  it("keeps legacy messages on the established default bubble style", () => {
    expect(resolveUserMessageAppearance({}, "usr_me")).toEqual({
      own: true,
      style: {}
    });
  });
});
