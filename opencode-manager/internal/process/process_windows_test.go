//go:build windows

package process

import (
	"reflect"
	"testing"
)

func TestResolveWindowsCommandPassesNativeExecutablesThrough(t *testing.T) {
	command, args := resolveWindowsCommand(`C:\tools\opencode.exe`, []string{"serve", "--port", "4096"})
	if command != `C:\tools\opencode.exe` {
		t.Fatalf("expected original command for native exe, got %q", command)
	}
	if !reflect.DeepEqual(args, []string{"serve", "--port", "4096"}) {
		t.Fatalf("expected unchanged args, got %#v", args)
	}
}

func TestResolveWindowsCommandPassesUnqualifiedNamesThrough(t *testing.T) {
	// 无扩展名的命令应原样返回，避免误判 PATH 中存在同名 .ps1 的情况。
	command, args := resolveWindowsCommand("opencode", []string{"serve"})
	if command != "opencode" {
		t.Fatalf("expected original command for unqualified name, got %q", command)
	}
	if !reflect.DeepEqual(args, []string{"serve"}) {
		t.Fatalf("expected unchanged args, got %#v", args)
	}
}

func TestResolveWindowsCommandWrapsPowerShellScript(t *testing.T) {
	script := `D:\Tool\nodes\nodejs\opencode.ps1`
	command, args := resolveWindowsCommand(script, []string{"serve", "--port", "4096"})
	if command != "powershell.exe" {
		t.Fatalf("expected powershell.exe, got %q", command)
	}
	want := []string{"-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, "serve", "--port", "4096"}
	if !reflect.DeepEqual(args, want) {
		t.Fatalf("unexpected ps1 wrapper args: %#v", args)
	}
}

func TestResolveWindowsCommandMatchesPs1ExtensionCaseInsensitively(t *testing.T) {
	// 大写 .PS1 也必须命中，避免手工配置时大小写不一致绕过检测。
	command, args := resolveWindowsCommand(`D:\Tool\opencode.PS1`, []string{"serve"})
	if command != "powershell.exe" {
		t.Fatalf("expected powershell.exe, got %q", command)
	}
	if len(args) != 6 || args[4] != `D:\Tool\opencode.PS1` {
		t.Fatalf("expected script path in -File slot, got %#v", args)
	}
}
