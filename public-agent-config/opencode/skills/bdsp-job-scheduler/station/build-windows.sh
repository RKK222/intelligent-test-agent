#!/usr/bin/env bash
# 用 Go 1.20.14 工具链交叉编译 Windows exe,保证 Win7 兼容
# Go 1.21+ 已放弃 Win7 支持,必须使用 Go 1.20.x
set -e

cd "$(dirname "$0")"

export GOTOOLCHAIN=go1.20.14
export CGO_ENABLED=0

echo "===> 使用 Go 工具链: $GOTOOLCHAIN"
echo "===> 开始编译 capture-proxy-windows-amd64.exe"
GOOS=windows GOARCH=amd64 GOAMD64=v1 go build -o capture-proxy-windows-amd64.exe .
echo "===> 开始编译 capture-proxy-windows-386.exe"
GOOS=windows GOARCH=386 GO386=sse2 go build -o capture-proxy-windows-386.exe .

echo ""
echo "===> 编译完成,验证工具链版本:"
go version -m capture-proxy-windows-amd64.exe | head -1
go version -m capture-proxy-windows-386.exe   | head -1

echo ""
echo "===> 产物列表:"
ls -la capture-proxy-windows-*.exe

echo ""
echo "完成。将两个 exe 拷贝到 Windows 机器,双击 start-windows.bat 运行。"
