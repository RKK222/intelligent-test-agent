package state

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

// ProcessRecord 是管理进程跨 CLI 调用保存的本地进程快照。
type ProcessRecord struct {
	Port          int       `json:"port"`
	PID           int       `json:"pid"`
	BaseURL       string    `json:"baseUrl"`
	UnifiedAuthID string    `json:"unifiedAuthId,omitempty"`
	SessionPath   string    `json:"sessionPath"`
	ConfigPath    string    `json:"configPath"`
	StartedAt     time.Time `json:"startedAt"`
	StartCommand  string    `json:"startCommand,omitempty"`
	TraceID       string    `json:"traceId"`
}

// FileStore 使用 stateDir/processes/{port}.json 保存端口到 PID 的索引。
type FileStore struct {
	root string
}

// NewFileStore 创建本地文件状态仓库。
func NewFileStore(root string) *FileStore {
	return &FileStore{root: root}
}

// Save 写入或覆盖端口状态，供启动成功和后续状态修正使用。
func (s *FileStore) Save(record ProcessRecord) error {
	if err := validate(record); err != nil {
		return err
	}
	if err := os.MkdirAll(s.processDir(), 0o755); err != nil {
		return err
	}
	payload, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return err
	}
	tmp := s.recordPath(record.Port) + ".tmp"
	if err := os.WriteFile(tmp, append(payload, '\n'), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, s.recordPath(record.Port))
}

// Create 只在端口未被占用时写入状态，防止重复启动同一端口。
func (s *FileStore) Create(record ProcessRecord) error {
	if err := validate(record); err != nil {
		return err
	}
	if err := os.MkdirAll(s.processDir(), 0o755); err != nil {
		return err
	}
	payload, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return err
	}
	file, err := os.OpenFile(s.recordPath(record.Port), os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o644)
	if err != nil {
		if errors.Is(err, os.ErrExist) {
			return fmt.Errorf("port %d is already managed", record.Port)
		}
		return err
	}
	defer file.Close()
	_, err = file.Write(append(payload, '\n'))
	return err
}

// Get 按端口读取进程状态，不存在时返回 ok=false。
func (s *FileStore) Get(port int) (ProcessRecord, bool, error) {
	payload, err := os.ReadFile(s.recordPath(port))
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return ProcessRecord{}, false, nil
		}
		return ProcessRecord{}, false, err
	}
	var record ProcessRecord
	if err := json.Unmarshal(payload, &record); err != nil {
		return ProcessRecord{}, false, err
	}
	return record, true, nil
}

// List 返回所有端口状态，按端口升序排序。
func (s *FileStore) List() ([]ProcessRecord, error) {
	entries, err := os.ReadDir(s.processDir())
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return []ProcessRecord{}, nil
		}
		return nil, err
	}
	records := make([]ProcessRecord, 0, len(entries))
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".json") {
			continue
		}
		payload, err := os.ReadFile(filepath.Join(s.processDir(), entry.Name()))
		if err != nil {
			return nil, err
		}
		var record ProcessRecord
		if err := json.Unmarshal(payload, &record); err != nil {
			return nil, err
		}
		records = append(records, record)
	}
	sort.Slice(records, func(i, j int) bool {
		return records[i].Port < records[j].Port
	})
	return records, nil
}

// Delete 删除指定端口状态；不存在时视为成功，便于重复清理。
func (s *FileStore) Delete(port int) error {
	err := os.Remove(s.recordPath(port))
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	return err
}

func (s *FileStore) processDir() string {
	return filepath.Join(s.root, "processes")
}

func (s *FileStore) recordPath(port int) string {
	return filepath.Join(s.processDir(), strconv.Itoa(port)+".json")
}

func validate(record ProcessRecord) error {
	if record.Port < 1 || record.Port > 65535 {
		return fmt.Errorf("port must be between 1 and 65535")
	}
	if record.PID < 1 {
		return fmt.Errorf("pid must be positive")
	}
	if record.StartedAt.IsZero() {
		return fmt.Errorf("startedAt is required")
	}
	return nil
}
