//go:build linux

package control

import ocgroups "github.com/opencontainers/cgroups"

func parseOwnCgroupFile(path string) (map[string]string, error) {
	return ocgroups.ParseCgroupFile(path)
}
