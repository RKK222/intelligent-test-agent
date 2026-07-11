import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";

type Listener = () => void;

const models = vi.hoisted(() => [] as Array<{
  getValue: () => string;
  setValue: (value: string) => void;
  onDidChangeContent: (listener: Listener) => { dispose: () => void };
  dispose: () => void;
}>);
const editorCreates = vi.hoisted(() => vi.fn());
const monacoLoad = vi.hoisted(() => ({
  gate: null as Promise<void> | null,
  calls: vi.fn()
}));

vi.mock("../src/monaco-env", () => {
  const monaco = {
    editor: {
      defineTheme: () => {},
      setModelLanguage: () => {},
      createModel: (initial: string) => {
        let value = initial;
        const listeners: Listener[] = [];
        const model = {
          getValue: () => value,
          setValue: (next: string) => {
            value = next;
            listeners.forEach((listener) => listener());
          },
          onDidChangeContent: (listener: Listener) => {
            listeners.push(listener);
            return { dispose: () => {} };
          },
          dispose: () => {}
        };
        models.push(model);
        return model;
      },
      create: editorCreates.mockImplementation(() => ({
        setModel: () => {},
        dispose: () => {}
      }))
    }
  };
  return {
    loadMonaco: async () => {
      monacoLoad.calls();
      if (monacoLoad.gate) await monacoLoad.gate;
      return monaco;
    }
  };
});

import MergeConflictEditor from "../src/MergeConflictEditor.vue";

const conflict = {
  path: "src/conflict.ts",
  rawStatus: "UU",
  baseContent: "base\n",
  currentContent: "current\n",
  incomingContent: "incoming\n",
  resultContent: "<<<<<<< HEAD\ncurrent\n=======\nincoming\n>>>>>>> app\n"
};

describe("MergeConflictEditor", () => {
  beforeEach(() => {
    models.length = 0;
    editorCreates.mockClear();
    monacoLoad.calls.mockClear();
    monacoLoad.gate = null;
  });

  it.each([
    ["保留当前", "current\n"],
    ["采用应用", "incoming\n"],
    ["保留两者", "current\nincoming\n"]
  ])("applies %s and saves the resolved content", async (action, expected) => {
    const view = render(MergeConflictEditor, { props: { conflict } });
    await fireEvent.click(await view.findByRole("button", { name: action }));

    const save = view.getByRole("button", { name: "保存并标记已解决" }) as HTMLButtonElement;
    await waitFor(() => expect(save.disabled).toBe(false));
    await fireEvent.click(save);

    expect(view.emitted("resolve")).toEqual([[{
      resolution: "MANUAL",
      content: expected
    }]]);
  });

  it("does not finish asynchronous Monaco initialization after unmount", async () => {
    let releaseLoad!: () => void;
    monacoLoad.gate = new Promise<void>((resolve) => {
      releaseLoad = resolve;
    });
    const view = render(MergeConflictEditor, { props: { conflict } });
    await waitFor(() => expect(monacoLoad.calls).toHaveBeenCalledOnce());

    view.unmount();
    releaseLoad();
    await Promise.resolve();
    await Promise.resolve();

    expect(editorCreates).not.toHaveBeenCalled();
  });
});
