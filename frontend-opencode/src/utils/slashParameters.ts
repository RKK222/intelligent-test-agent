import type { CommandInfo } from "@test-agent/shared-types";

export type SlashParameterValue = string | boolean;

export interface SlashParameterField {
  id: string;
  kind: "value" | "flag";
  label: string;
  prefix?: string;
  required: boolean;
  placeholder?: string;
}

export interface SlashParameterForm {
  commandName: string;
  description?: string;
  fields: SlashParameterField[];
  chunks: Array<{ type: "literal"; value: string } | { type: "field"; fieldId: string }>;
}

export function createSlashParameterForm(command: Pick<CommandInfo, "name" | "description" | "arguments" | "hints">) {
  const commandName = command.name.replace(/^\//, "").trim();
  if (!commandName) {
    return undefined;
  }
  const chunks = command.hints?.length ? chunksFromHints(command.hints) : chunksFromTemplate(command.arguments ?? "");
  const fields = chunks.flatMap((chunk) => (chunk.type === "field" ? [chunk.field] : []));
  if (!fields.length) {
    return undefined;
  }
  return {
    commandName,
    description: command.description,
    fields,
    chunks: chunks.map((chunk) => (chunk.type === "field" ? { type: "field" as const, fieldId: chunk.field.id } : chunk))
  };
}

export function initialSlashParameterValues(form: SlashParameterForm): Record<string, SlashParameterValue> {
  return Object.fromEntries(form.fields.map((field) => [field.id, field.kind === "flag" ? false : ""]));
}

export function buildSlashCommandText(form: SlashParameterForm, values: Record<string, SlashParameterValue>) {
  const args = form.chunks.flatMap((chunk) => {
    if (chunk.type === "literal") {
      return [chunk.value];
    }
    const field = form.fields.find((item) => item.id === chunk.fieldId);
    if (!field) {
      return [];
    }
    const value = values[field.id];
    if (field.kind === "flag") {
      return value === true && field.prefix ? [field.prefix] : [];
    }
    const text = typeof value === "string" ? value.trim() : "";
    if (!text) {
      return [];
    }
    return field.prefix ? [field.prefix, quoteArg(text)] : [quoteArg(text)];
  });
  return [`/${form.commandName}`, ...args].join(" ");
}

function chunksFromHints(hints: string[]) {
  return hints
    .filter((hint) => hint.trim())
    .map((hint, index) => ({
      type: "field" as const,
      field: valueField(`hint:${hint}:${index}`, hint === "$ARGUMENTS" ? "arguments" : hint.replace(/^\$(\d+)$/, "Argument $1"), true, hint)
    }));
}

function chunksFromTemplate(template: string) {
  const tokens = template.match(/\[[^\]]+\]|<[^>]+>|\S+/g) ?? [];
  const chunks: Array<{ type: "literal"; value: string } | { type: "field"; field: SlashParameterField }> = [];
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index] ?? "";
    if (token.startsWith("[") && token.endsWith("]")) {
      const optional = parseOptionalToken(token.slice(1, -1), chunks.length);
      if (optional) {
        chunks.push({ type: "field", field: optional });
      }
      continue;
    }
    const placeholder = placeholderName(token);
    if (placeholder) {
      chunks.push({ type: "field", field: valueField(`arg:${placeholder}:${chunks.length}`, placeholder, true, placeholder) });
      continue;
    }
    const next = placeholderName(tokens[index + 1] ?? "");
    if (token.startsWith("--") && next) {
      chunks.push({
        type: "field",
        field: valueField(`option:${token}:${next}:${chunks.length}`, next, true, next, token)
      });
      index += 1;
      continue;
    }
    if (!token.startsWith("<") && !token.startsWith("[")) {
      chunks.push({ type: "literal", value: token });
    }
  }
  return chunks;
}

function parseOptionalToken(token: string, index: number): SlashParameterField | undefined {
  const trimmed = token.trim();
  if (!trimmed) {
    return undefined;
  }
  const flagWithValue = trimmed.match(/^(--[\w-]+)\s+<([^>]+)>$/);
  if (flagWithValue) {
    return valueField(`optional:${flagWithValue[1]}:${index}`, flagWithValue[2] ?? "value", false, flagWithValue[2], flagWithValue[1]);
  }
  if (/^--[\w-]+$/.test(trimmed)) {
    return {
      id: `flag:${trimmed}:${index}`,
      kind: "flag",
      label: trimmed,
      prefix: trimmed,
      required: false
    };
  }
  const placeholder = placeholderName(trimmed);
  if (placeholder) {
    return valueField(`optional:${placeholder}:${index}`, placeholder, false, placeholder);
  }
  return valueField(`optional:${trimmed}:${index}`, trimmed, false, trimmed);
}

function valueField(id: string, label: string, required: boolean, placeholder?: string, prefix?: string): SlashParameterField {
  return {
    id,
    kind: "value",
    label,
    prefix,
    required,
    placeholder
  };
}

function placeholderName(token: string) {
  const match = token.trim().match(/^<([^>]+)>$/);
  return match?.[1]?.trim();
}

function quoteArg(value: string) {
  if (!/\s/.test(value)) {
    return value;
  }
  return `"${value.replace(/(["\\$`])/g, "\\$1")}"`;
}
