package main

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/config"
)

const someID = "6f1c2d3e-4a5b-4c6d-8e7f-0a1b2c3d4e5f"

type server struct {
	url      string
	requests *atomic.Int32
}

func serve(t *testing.T, responses map[string]string) server {
	requests := new(atomic.Int32)
	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(responses[r.URL.Path]))
	}))
	t.Cleanup(s.Close)
	return server{s.URL, requests}
}

func dewey(t *testing.T, env map[string]string, stdin string, args ...string) (string, error) {
	t.Helper()
	root := newRoot(filepath.Join(t.TempDir(), "config.json"), func(key string) string { return env[key] })
	var out bytes.Buffer
	root.SetArgs(args)
	root.SetIn(strings.NewReader(stdin))
	root.SetOut(&out)
	root.SetErr(&out)
	_, err := root.ExecuteC()
	return out.String(), err
}

func TestAMisspelledFieldInsideAnOptionalBodyFieldFails(t *testing.T) {
	s := serve(t, nil)

	_, err := dewey(t, nil, "", "--url", s.url, "--token", "t", "books", "edit", someID, someID, "--body", `{"chapters": [{"titel": "x"}]}`)

	if err == nil || !strings.Contains(err.Error(), `unknown field "titel"`) {
		t.Fatalf("expected an unknown field error, got %v", err)
	}
	if n := s.requests.Load(); n != 0 {
		t.Fatalf("sent %d requests", n)
	}
}

func TestATabOrLineBreakInAValueKeepsTheTableRowWhole(t *testing.T) {
	s := serve(t, map[string]string{"/api/libraries": `[{"id": "` + someID + `", "name": "Main\tLib\nTwo", "folders": []}]`})

	out, err := dewey(t, nil, "", "--url", s.url, "--token", "t", "libraries", "list")

	if err != nil {
		t.Fatal(err)
	}
	if lines := strings.Split(strings.TrimSuffix(out, "\n"), "\n"); len(lines) != 1 || !strings.Contains(out, "Main Lib Two") {
		t.Fatalf("expected one row, got %q", out)
	}
}

func TestATypoInACommandGetsASuggestion(t *testing.T) {
	_, err := dewey(t, nil, "", "boks")

	if err == nil || !strings.Contains(err.Error(), "Did you mean this?\n\tbooks") {
		t.Fatalf("expected a suggestion, got %v", err)
	}
}

func TestAServerWithoutASchemeIsRejected(t *testing.T) {
	_, err := dewey(t, nil, "", "--url", "localhost:8080", "--token", "t", "me", "view")

	if err == nil || !strings.Contains(err.Error(), "like http://localhost:8080") {
		t.Fatalf("expected a missing scheme error, got %v", err)
	}
}

func TestLoginRejectsAnEmptyToken(t *testing.T) {
	s := serve(t, nil)

	_, err := dewey(t, nil, "\n", "auth", "login", "--url", s.url)

	if err == nil || err.Error() != "The access token is empty" {
		t.Fatalf("expected an empty token error, got %v", err)
	}
}

func TestLoginTakesTheServerFromTheEnvironment(t *testing.T) {
	s := serve(t, map[string]string{"/api/auth/user/current": `{"username": "test", "permissions": {"isAdmin": false}}`})
	path := filepath.Join(t.TempDir(), "config.json")
	root := newRoot(path, func(key string) string { return map[string]string{"DEWEY_URL": s.url}[key] })
	root.SetArgs([]string{"auth", "login", "--token", "t"})
	root.SetOut(new(bytes.Buffer))

	if _, err := root.ExecuteC(); err != nil {
		t.Fatal(err)
	}
	saved, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	if saved != (config.Settings{URL: s.url, Token: "t"}) {
		t.Fatalf("saved %+v", saved)
	}
}
