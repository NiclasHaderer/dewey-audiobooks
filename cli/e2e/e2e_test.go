//go:build e2e

// Expects a server seeded by just test-cli-e2e with the user below and a library called "Test"
package e2e

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/apptest"
)

const (
	username = "test"
	password = "testtest"
)

var dewey *server

func TestMain(m *testing.M) {
	os.Exit(run(m))
}

func run(m *testing.M) int {
	dir, err := os.MkdirTemp("", "dewey-e2e")
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	defer os.RemoveAll(dir)

	s, err := connect(dir)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	defer s.revokeToken()
	if err := s.findSeed(newCLI(dir)); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	dewey = s
	return m.Run()
}

type server struct {
	url       string
	session   string
	token     string
	tokenID   string
	libraryID string
	bookID    string
}

func connect(dir string) (*server, error) {
	s := &server{url: "http://localhost:8080"}
	if url := os.Getenv("DEWEY_E2E_URL"); url != "" {
		s.url = url
	}

	c := newCLI(dir)
	credentials := fmt.Sprintf(`{"username": %q, "password": %q}`, username, password)
	login := c.Pipe(credentials, "--url", s.url, "sessions", "login", "--jq", ".accessToken", "--body", "-")
	if login.Code != 0 {
		return nil, fmt.Errorf("no seeded server at %s, run the tests with just test-cli-e2e\n%s", s.url, login.Stderr)
	}
	s.session = strings.TrimSpace(login.Stdout)
	return s, s.createToken()
}

// The CLI has no command to create or revoke tokens, since both take the password, so these call the API
func (s *server) createToken() error {
	body := fmt.Sprintf(`{"name": "e2e", "permissions": {"isAdmin": true, "libraries": null}, "password": %q}`, password)
	content, err := s.asSession(http.MethodPost, "auth/user/tokens", body)
	if err != nil {
		return err
	}
	var created struct {
		Token       string `json:"token"`
		AccessToken struct {
			ID string `json:"id"`
		} `json:"accessToken"`
	}
	if err := json.Unmarshal(content, &created); err != nil {
		return err
	}
	s.token, s.tokenID = created.Token, created.AccessToken.ID
	return nil
}

func (s *server) revokeToken() {
	if _, err := s.asSession(http.MethodDelete, "auth/user/tokens/"+s.tokenID, fmt.Sprintf(`{"password": %q}`, password)); err != nil {
		fmt.Fprintln(os.Stderr, "could not revoke the e2e token:", err)
	}
}

func (s *server) asSession(method string, path string, body string) ([]byte, error) {
	request, err := http.NewRequest(method, s.url+"/api/"+path, strings.NewReader(body))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Authorization", "Bearer "+s.session)
	request.Header.Set("Content-Type", "application/json")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	content, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	if response.StatusCode >= 300 {
		return nil, fmt.Errorf("%s %s: %s %s", method, path, response.Status, content)
	}
	return content, nil
}

// just creates the seeded library before the tests start, but the server scans it in the background, so its first
// book can appear well after that
func (s *server) findSeed(c cli) error {
	query := func(args ...string) string {
		return strings.TrimSpace(c.Run(append([]string{"--url", s.url, "--token", s.token}, args...)...).Stdout)
	}
	s.libraryID = query("libraries", "list", "--jq", `.[] | select(.name == "Test") | .id`)
	if s.libraryID == "" {
		return fmt.Errorf("%s has no library called Test, run the tests with just test-cli-e2e", s.url)
	}
	for deadline := time.Now().Add(time.Minute); time.Now().Before(deadline); time.Sleep(time.Second) {
		if s.bookID = query("books", "list", s.libraryID, "--limit", "1", "--jq", ".items[0].id // empty"); s.bookID != "" {
			return nil
		}
	}
	return fmt.Errorf("the scan of the Test library at %s found no book", s.url)
}

func (s *server) loggedOut(t *testing.T) cli {
	return newCLI(t.TempDir())
}

func (s *server) loggedIn(t *testing.T) cli {
	t.Helper()
	c := s.loggedOut(t)
	if out := c.must(t, "auth", "login", "--url", s.url, "--token", s.token); !strings.Contains(out, "as "+username) {
		t.Fatalf("unexpected login output %q", out)
	}
	return c
}

type cli struct {
	apptest.CLI
}

func newCLI(dir string) cli {
	return cli{apptest.CLI{Config: filepath.Join(dir, "config.json")}}
}

func (c cli) must(t *testing.T, args ...string) string {
	t.Helper()
	r := c.Run(args...)
	if r.Code != 0 {
		t.Fatalf("dewey %s exited with %d\nstdout: %s\nstderr: %s", strings.Join(args, " "), r.Code, r.Stdout, r.Stderr)
	}
	return strings.TrimSpace(r.Stdout)
}

func fails(t *testing.T, r apptest.Result, messages ...string) {
	t.Helper()
	if r.Code == 0 {
		t.Fatalf("expected an error saying %q, got %q", messages, r.Stdout)
	}
	for _, message := range messages {
		if !strings.Contains(r.Stderr, message) {
			t.Fatalf("expected an error saying %q, got %q", message, r.Stderr)
		}
	}
}

func libraryBody(t *testing.T, name string) string {
	return fmt.Sprintf(`{
		"name": %q, "icon": null, "folders": [%q], "preferEmbeddedMetadata": true,
		"metadataAgents": [], "combineMetadataAgentFields": false,
		"fileScanners": [{"name": "AudioFolderScanner"}], "combineFileScannerFields": false, "language": "English",
		"region": "US"
	}`, name, t.TempDir())
}

func createLibrary(t *testing.T, c cli, name string) string {
	t.Helper()
	r := c.Pipe(libraryBody(t, name), "libraries", "create", "--jq", ".id", "--body", "-")
	if r.Code != 0 {
		t.Fatalf("exited with %d: %s", r.Code, r.Stderr)
	}
	id := strings.TrimSpace(r.Stdout)
	t.Cleanup(func() { c.Run("libraries", "delete", id) })
	return id
}

func TestLoginRemembersTheServerAndTheToken(t *testing.T) {
	c := dewey.loggedIn(t)

	if user := c.must(t, "me", "view", "--jq", ".username"); user != username {
		t.Fatalf("logged in as %q", user)
	}
}

func TestLogoutForgetsTheLogin(t *testing.T) {
	c := dewey.loggedIn(t)

	c.must(t, "auth", "logout")

	fails(t, c.Run("me", "view"), "--url")
}

func TestLoginTakesTheServerFromTheEnvironment(t *testing.T) {
	c := dewey.loggedOut(t)
	c.Env = map[string]string{"DEWEY_URL": dewey.url}
	c.must(t, "auth", "login", "--token", dewey.token)
	c.Env = nil

	if user := c.must(t, "me", "view", "--jq", ".username"); user != username {
		t.Fatalf("logged in as %q", user)
	}
}

func TestWithoutATokenTheErrorSaysHowToGetOne(t *testing.T) {
	fails(t, dewey.loggedOut(t).Run("--url", dewey.url, "me", "view"), "Access tokens")
}

func TestALibraryIsCreatedRenamedAndDeleted(t *testing.T) {
	c := dewey.loggedIn(t)
	file := filepath.Join(t.TempDir(), "library.json")
	if err := os.WriteFile(file, []byte(libraryBody(t, "E2E")), 0o600); err != nil {
		t.Fatal(err)
	}

	id := c.must(t, "libraries", "create", "--jq", ".id", "--body", "@"+file)
	t.Cleanup(func() { c.Run("libraries", "delete", id) })
	if r := c.Pipe(`{"name": "E2E renamed"}`, "libraries", "edit", id, "--body", "-"); r.Code != 0 {
		t.Fatalf("exited with %d: %s", r.Code, r.Stderr)
	}
	renamed := c.must(t, "libraries", "view", id, "--jq", ".name")
	c.must(t, "libraries", "delete", id)
	remaining := c.must(t, "libraries", "list", "--jq", ".[].id")

	if renamed != "E2E renamed" {
		t.Fatalf("library is called %q", renamed)
	}
	if strings.Contains(remaining, id) {
		t.Fatal("the deleted library is still listed")
	}
}

func TestAnEditChangesOnlyTheFieldsItSets(t *testing.T) {
	c := dewey.loggedIn(t)
	id := createLibrary(t, c, "E2E patch")
	edit := func(body string) string {
		t.Helper()
		if r := c.Pipe(body, "libraries", "edit", id, "--body", "-"); r.Code != 0 {
			t.Fatalf("exited with %d: %s", r.Code, r.Stderr)
		}
		return c.must(t, "libraries", "view", id, "--jq", `"\(.name) \(.icon)"`)
	}

	set := edit(`{"icon": "book"}`)
	absent := edit(`{"name": "E2E patched"}`)
	nulled := edit(`{"icon": null}`)

	if set != "E2E patch book" {
		t.Fatalf("setting the icon left %q", set)
	}
	if absent != "E2E patched book" {
		t.Fatalf("leaving the icon out left %q", absent)
	}
	if nulled != "E2E patched null" {
		t.Fatalf("nulling the icon left %q", nulled)
	}
}

func TestATabOrLineBreakInAValueKeepsTheTableRowWhole(t *testing.T) {
	c := dewey.loggedIn(t)
	id := createLibrary(t, c, "E2E\tTab\nBreak")

	out := c.must(t, "libraries", "list")

	for _, line := range strings.Split(out, "\n") {
		if strings.Contains(line, id) && !strings.Contains(line, "E2E Tab Break") {
			t.Fatalf("expected the library on one row, got %q", out)
		}
	}
	if strings.Contains(out, "\nBreak") {
		t.Fatalf("the line break split the row: %q", out)
	}
}

func TestAMisspelledFieldInsideAnOptionalBodyFieldFails(t *testing.T) {
	c := dewey.loggedIn(t)

	r := c.Run("books", "edit", dewey.libraryID, dewey.bookID, "--body", `{"chapters": [{"titel": "x"}]}`)

	fails(t, r, `unknown field "titel"`)
}

func TestATrackDownloadsAsTheFileItWasScannedFrom(t *testing.T) {
	c := dewey.loggedIn(t)
	trackID := c.must(t, "books", "view", dewey.libraryID, dewey.bookID, "--jq", ".tracks[0].id // empty")
	if trackID == "" {
		t.Fatal("the seeded book has no tracks")
	}
	output := filepath.Join(t.TempDir(), "track.mp3")

	c.must(t, "files", "audio", trackID, "--output", output)

	downloaded, err := os.ReadFile(output)
	if err != nil {
		t.Fatal(err)
	}
	found := false
	err = filepath.WalkDir("../../test-resources", func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() || found {
			return err
		}
		content, err := os.ReadFile(path)
		found = err == nil && bytes.Equal(content, downloaded)
		return err
	})
	if err != nil {
		t.Fatal(err)
	}
	if !found {
		t.Fatalf("the %d downloaded bytes match no file in test-resources", len(downloaded))
	}
}
