package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/icbc/test-agent/opencode-manager/internal/config"
	"github.com/icbc/test-agent/opencode-manager/internal/control"
	"github.com/icbc/test-agent/opencode-manager/internal/health"
	"github.com/icbc/test-agent/opencode-manager/internal/process"
	"github.com/icbc/test-agent/opencode-manager/internal/state"
)

// main 只负责 CLI 协议适配，真实生命周期逻辑位于 internal/process。
func main() {
	os.Exit(run(os.Args[1:]))
}

func run(args []string) int {
	if len(args) == 0 {
		writeJSON(process.Result{Status: process.StatusFailed, Message: "missing command"})
		return 2
	}
	if args[0] == "run" {
		return runSupervisor()
	}
	cfg, err := config.LoadFromEnv()
	if err != nil {
		writeJSON(process.Result{Status: process.StatusFailed, Message: err.Error()})
		return 2
	}
	manager := process.NewManager(
		cfg,
		state.NewFileStore(cfg.StateDir),
		process.OSStarter{},
		process.OSSignaler{},
		health.Checker{},
	)

	result, err := dispatch(context.Background(), manager, args)
	writeJSON(result)
	if err != nil {
		return 1
	}
	return 0
}

func runSupervisor() int {
	cfg, err := config.LoadControlFromEnv()
	if err != nil {
		writeJSON(process.Result{Status: process.StatusFailed, Message: err.Error()})
		return 2
	}
	manager := process.NewManager(
		cfg.Config,
		state.NewFileStore(cfg.StateDir),
		process.OSStarter{},
		process.OSSignaler{},
		health.Checker{},
	)
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	supervisor := control.NewSupervisor(cfg, manager, nil)
	if err := supervisor.Run(ctx); err != nil && ctx.Err() == nil {
		writeJSON(process.Result{Status: process.StatusFailed, Message: err.Error()})
		return 1
	}
	return 0
}

func dispatch(ctx context.Context, manager *process.Manager, args []string) (process.Result, error) {
	switch args[0] {
	case "start":
		flags := newFlagSet("start")
		port := flags.Int("port", 0, "opencode server port")
		traceID := flags.String("trace-id", "", "trace id")
		if err := flags.Parse(args[1:]); err != nil {
			return process.Result{Status: process.StatusFailed, Message: err.Error(), TraceID: *traceID}, err
		}
		return manager.Start(ctx, process.StartRequest{Port: *port, TraceID: *traceID})
	case "stop":
		port, traceID, timeout, err := parseStopLike("stop", args[1:])
		if err != nil {
			return process.Result{Status: process.StatusFailed, Message: err.Error(), TraceID: traceID}, err
		}
		return manager.Stop(ctx, process.StopRequest{Port: port, TraceID: traceID, Timeout: timeout})
	case "restart":
		port, traceID, timeout, err := parseStopLike("restart", args[1:])
		if err != nil {
			return process.Result{Status: process.StatusFailed, Message: err.Error(), TraceID: traceID}, err
		}
		return manager.Restart(ctx, process.StopRequest{Port: port, TraceID: traceID, Timeout: timeout})
	case "health":
		flags := newFlagSet("health")
		port := flags.Int("port", 0, "opencode server port")
		traceID := flags.String("trace-id", "", "trace id")
		if err := flags.Parse(args[1:]); err != nil {
			return process.Result{Status: process.StatusFailed, Message: err.Error(), TraceID: *traceID}, err
		}
		return manager.Health(ctx, process.HealthRequest{Port: *port, TraceID: *traceID})
	case "list":
		flags := newFlagSet("list")
		traceID := flags.String("trace-id", "", "trace id")
		if err := flags.Parse(args[1:]); err != nil {
			return process.Result{Status: process.StatusFailed, Message: err.Error(), TraceID: *traceID}, err
		}
		return manager.List(*traceID)
	default:
		err := fmt.Errorf("unknown command %q", args[0])
		return process.Result{Status: process.StatusFailed, Message: err.Error()}, err
	}
}

func parseStopLike(name string, args []string) (int, string, time.Duration, error) {
	flags := newFlagSet(name)
	port := flags.Int("port", 0, "opencode server port")
	traceID := flags.String("trace-id", "", "trace id")
	timeout := flags.Duration("timeout", 5*time.Second, "graceful stop timeout")
	if err := flags.Parse(args); err != nil {
		return 0, *traceID, 0, err
	}
	return *port, *traceID, *timeout, nil
}

func newFlagSet(name string) *flag.FlagSet {
	flags := flag.NewFlagSet(name, flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	return flags
}

func writeJSON(value process.Result) {
	encoder := json.NewEncoder(os.Stdout)
	encoder.SetEscapeHTML(false)
	_ = encoder.Encode(value)
}
