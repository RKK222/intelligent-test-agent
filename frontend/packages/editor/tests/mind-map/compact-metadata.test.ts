import { describe, expect, it } from "vitest";
import {
  decodeMindMapMetadata,
  encodeMindMapMetadata
} from "../../src/mind-map/compact-metadata";
import type { MindMapMetadata } from "../../src/mind-map/model";

describe("思维导图紧凑元数据", () => {
  it("往返 nextId、折叠状态和全部白名单样式，并规范化颜色", () => {
    const metadata: MindMapMetadata = {
      nextId: 8,
      nodes: {
        n1: {
          collapsed: true,
          style: {
            textColor: "#abc",
            fillColor: "#123456",
            borderColor: "#fedcba",
            borderWidth: 2,
            shape: "roundedRectangle",
            fontSize: 18,
            bold: true,
            lineColor: "#010203",
            lineWidth: 3,
            lineDash: "dashed"
          }
        }
      },
      unknownFields: []
    };

    const decoded = decodeMindMapMetadata(encodeMindMapMetadata(metadata));

    expect(decoded).toEqual({
      nextId: 8,
      nodes: {
        n1: {
          collapsed: true,
          style: {
            textColor: "#AABBCC",
            fillColor: "#123456",
            borderColor: "#FEDCBA",
            borderWidth: 2,
            shape: "roundedRectangle",
            fontSize: 18,
            bold: true,
            lineColor: "#010203",
            lineWidth: 3,
            lineDash: "dashed"
          }
        }
      },
      unknownFields: []
    });
  });

  it("未知 v1 TLV 解码后再次编码仍原样保留", () => {
    const unknown = { type: 0x7e, value: Uint8Array.from([0, 255, 17]) };
    const encoded = encodeMindMapMetadata({ nextId: 2, nodes: {}, unknownFields: [unknown] });
    const decoded = decodeMindMapMetadata(encoded);

    expect(decoded.unknownFields).toEqual([unknown]);
    expect(encodeMindMapMetadata(decoded)).toBe(encoded);
  });

  it("拒绝损坏校验、越界属性和超过 1 MiB 的载荷", () => {
    const valid = encodeMindMapMetadata({ nextId: 2, nodes: {}, unknownFields: [] });
    const middle = Math.floor(valid.length / 2);
    const corrupt = `${valid.slice(0, middle)}${valid[middle] === "A" ? "B" : "A"}${valid.slice(middle + 1)}`;

    expect(() => decodeMindMapMetadata(corrupt)).toThrow(/校验|Base64URL/);
    expect(() => encodeMindMapMetadata({
      nextId: 2,
      nodes: { n1: { style: { fontSize: 999 } } },
      unknownFields: []
    })).toThrow(/字号/);
    expect(() => decodeMindMapMetadata("A".repeat(1_398_103))).toThrow(/1 MiB|长度/);
  });
});
