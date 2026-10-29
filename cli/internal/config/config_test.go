package config

import (
	"os"
	"path/filepath"
	"testing"
)

func corruptConfig(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.json")
	if err := os.WriteFile(path, []byte("{not json"), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestFlagsAndEnvironmentCoveringEverythingSkipABrokenSavedLogin(t *testing.T) {
	getenv := func(key string) string {
		return map[string]string{"DEWEY_TOKEN": "env-token"}[key]
	}

	settings, err := Resolve(Settings{URL: "http://flag"}, getenv, corruptConfig(t))

	if err != nil {
		t.Fatal(err)
	}
	if settings != (Settings{URL: "http://flag", Token: "env-token"}) {
		t.Fatalf("resolved %+v", settings)
	}
}

func TestABrokenSavedLoginFailsWhenAValueIsMissing(t *testing.T) {
	_, err := Resolve(Settings{URL: "http://flag"}, func(string) string { return "" }, corruptConfig(t))

	if err == nil {
		t.Fatal("expected the broken file to be reported")
	}
}

func TestTheSavedLoginFillsWhatFlagsAndEnvironmentLeaveOpen(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	if err := Save(path, Settings{URL: "http://saved", Token: "saved-token"}); err != nil {
		t.Fatal(err)
	}

	settings, err := Resolve(Settings{URL: "http://flag"}, func(string) string { return "" }, path)

	if err != nil {
		t.Fatal(err)
	}
	if settings != (Settings{URL: "http://flag", Token: "saved-token"}) {
		t.Fatalf("resolved %+v", settings)
	}
}
