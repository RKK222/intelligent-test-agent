package state

import (
	"testing"
	"time"
)

func TestFileStoreSavesListsAndDeletesRecords(t *testing.T) {
	store := NewFileStore(t.TempDir())
	record := ProcessRecord{
		Port:         4096,
		PID:          12345,
		BaseURL:      "http://10.8.0.12:4096",
		SessionPath:  "/data/opencode/session/4096",
		ConfigPath:   "/data/opencode/.config/opencode/",
		StartedAt:    time.Date(2026, 6, 24, 0, 0, 0, 0, time.UTC),
		StartCommand: "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
		TraceID:      "trace_1234567890abcdef",
	}

	if err := store.Save(record); err != nil {
		t.Fatalf("Save returned error: %v", err)
	}
	loaded, ok, err := store.Get(4096)
	if err != nil || !ok {
		t.Fatalf("Get returned ok=%v err=%v", ok, err)
	}
	if loaded.PID != record.PID || loaded.BaseURL != record.BaseURL || loaded.StartCommand != record.StartCommand {
		t.Fatalf("loaded record mismatch: %#v", loaded)
	}
	records, err := store.List()
	if err != nil {
		t.Fatalf("List returned error: %v", err)
	}
	if len(records) != 1 || records[0].Port != 4096 {
		t.Fatalf("unexpected records: %#v", records)
	}
	if err := store.Delete(4096); err != nil {
		t.Fatalf("Delete returned error: %v", err)
	}
	_, ok, err = store.Get(4096)
	if err != nil || ok {
		t.Fatalf("expected record to be deleted, ok=%v err=%v", ok, err)
	}
}

func TestFileStoreRejectsDuplicatePort(t *testing.T) {
	store := NewFileStore(t.TempDir())
	record := ProcessRecord{Port: 4096, PID: 12345, StartedAt: time.Now().UTC(), TraceID: "trace_1"}

	if err := store.Create(record); err != nil {
		t.Fatalf("Create returned error: %v", err)
	}
	if err := store.Create(record); err == nil {
		t.Fatalf("expected duplicate port error")
	}
}
