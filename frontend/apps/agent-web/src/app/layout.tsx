import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TestAgent IDE",
  description: "测试智能体 Web IDE"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
