package config

import "testing"

func TestDeriveStableRuntimeIdentityMatchesGoldenVector(t *testing.T) {
	containerID := deriveContainerID("server-a")
	if containerID != "ctr_37d8ba8e9e4fe57cfde6371a3bc181239377021141ae2dbb78160444cd2e51a8" {
		t.Fatalf("unexpected container id %q", containerID)
	}

	managerID := deriveManagerID(containerID)
	if managerID != "mgr_3cce4dbff435a28a8abdbce551007183674c5e0c85f27b4a2fcbdec68c099308" {
		t.Fatalf("unexpected manager id %q", managerID)
	}
}

func TestDeriveContainerIDDependsOnlyOnStableLinuxServerID(t *testing.T) {
	first := deriveContainerID("server-a")
	second := deriveContainerID("server-a")
	if first != second {
		t.Fatalf("same linux server id must produce stable container id: %q != %q", first, second)
	}
	if first == deriveContainerID("server-b") {
		t.Fatalf("different linux server ids must produce different container ids")
	}
}
