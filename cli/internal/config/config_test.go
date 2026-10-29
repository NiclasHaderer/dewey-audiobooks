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

func TestSavingOverAReadableFileMakesItPrivate(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	if err := os.WriteFile(path, []byte("{}"), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := Save(path, Settings{URL: "http://saved", Token: "saved-token"}); err != nil {
		t.Fatal(err)
	}

	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("the file has mode %v", info.Mode().Perm())
	}
}

func TestTheSavedLoginFillsWhatFlagsAndEnvironmentLeaveOpen(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	if err := Save(path, Settings{URL: "http://saved", Token: "saved-token"}); err != nil {
		t.Fatal(err)
	}

	settings, err := Resolve(Settings{Token: "flag-token"}, func(string) string { return "" }, path)

	if err != nil {
		t.Fatal(err)
	}
	if settings != (Settings{URL: "http://saved", Token: "flag-token"}) {
		t.Fatalf("resolved %+v", settings)
	}
}

func TestTheSavedTokenIsNotSentToAnotherServer(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	if err := Save(path, Settings{URL: "http://saved", Token: "saved-token"}); err != nil {
		t.Fatal(err)
	}

	settings, err := Resolve(Settings{URL: "http://other"}, func(string) string { return "" }, path)

	if err != nil {
		t.Fatal(err)
	}
	if settings != (Settings{URL: "http://other"}) {
		t.Fatalf("resolved %+v", settings)
	}
}
