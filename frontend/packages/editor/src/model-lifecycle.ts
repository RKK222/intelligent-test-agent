export type DisposableEditorModel = object & {
  dispose: () => void;
};

const codeEditorModelReferences = new WeakMap<DisposableEditorModel, number>();

/** 记录 CodeEditor 对 Monaco model 的占用，支持同 URI 被多个编辑器复用。 */
export function retainCodeEditorModel(candidate: DisposableEditorModel) {
  codeEditorModelReferences.set(candidate, (codeEditorModelReferences.get(candidate) ?? 0) + 1);
}

/** 最后一个 CodeEditor 离开后销毁 Monaco model，避免全局模型注册表长期保留文件内容。 */
export function releaseCodeEditorModel(candidate: DisposableEditorModel) {
  const references = codeEditorModelReferences.get(candidate) ?? 0;
  if (references > 1) {
    codeEditorModelReferences.set(candidate, references - 1);
    return;
  }
  codeEditorModelReferences.delete(candidate);
  candidate.dispose();
}
