package config

import (
	"cmp"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"runtime"
)

type Settings struct {
	URL   string `json:"url"`
	Token string `json:"token"`
}

func Path() (string, error) {
	dir, err := configDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "dewey", "config.json"), nil
}

func configDir() (string, error) {
	// os.UserConfigDir resolves to ~/Library/Application Support on macOS, I prefer ~/.config.
	if runtime.GOOS != "darwin" {
		return os.UserConfigDir()
	}
	if dir := os.Getenv("XDG_CONFIG_HOME"); dir != "" {
		return dir, nil
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".config"), nil
}

func Load(path string) (Settings, error) {
	var settings Settings
	content, err := os.ReadFile(path)
	if errors.Is(err, fs.ErrNotExist) {
		return settings, nil
	}
	if err != nil {
		return settings, err
	}
	if err := json.Unmarshal(content, &settings); err != nil {
		return settings, fmt.Errorf("%s is not valid, run dewey auth login again: %w", path, err)
	}
	return settings, nil
}

// Only the user may read the file: the token in it is as good as their password
func Save(path string, settings Settings) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	content, err := json.MarshalIndent(settings, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, content, 0o600)
}

// The saved login is only read when flags and environment leave a value open, so a broken file cannot block them
func Resolve(flags Settings, getenv func(string) string, path string) (Settings, error) {
	settings := Settings{
		URL:   cmp.Or(flags.URL, getenv("DEWEY_URL")),
		Token: cmp.Or(flags.Token, getenv("DEWEY_TOKEN")),
	}
	if settings.URL != "" && settings.Token != "" {
		return settings, nil
	}
	saved, err := Load(path)
	if err != nil {
		return Settings{}, err
	}
	settings.URL = cmp.Or(settings.URL, saved.URL)
	settings.Token = cmp.Or(settings.Token, saved.Token)
	return settings, nil
}
