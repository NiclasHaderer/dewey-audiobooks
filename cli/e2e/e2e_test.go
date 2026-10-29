//go:build e2e

// Expects a server seeded by just test-cli-e2e with the user below and a library called "Test". Each test removes the
// libraries it adds and the run revokes its token, so the tests can also run against a dev server.
package e2e

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
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
	if err := s.findSeed(cli{s.binary, dir}); err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	dewey = s
	return m.Run()
}

type server struct {
	binary    string
	url       string
	session   string
	token     string
	tokenID   string
	libraryID string
	bookID    string
}

func connect(dir string) (*server, error) {
	s := &server{binary: filepath.Join(dir, "dewey"), url: "http://localhost:8080"}
	if url := os.Getenv("DEWEY_E2E_URL"); url != "" {
		s.url = url
	}
	build := exec.Command("go", "build", "-o", s.binary, "..")
	build.Stdout, build.Stderr = os.Stderr, os.Stderr
	if err := build.Run(); err != nil {
		return nil, fmt.Errorf("could not build dewey: %w", err)
	}

	c := cli{s.binary, dir}
	credentials := fmt.Sprintf(`{"username": %q, "password": %q}`, username, password)
	login := c.pipe(credentials, "--url", s.url, "sessions", "login", "--jq", ".accessToken", "--body", "-")
	if login.err != nil {
		return nil, fmt.Errorf("no seeded server at %s, run the tests with just test-cli-e2e\n%v %s", s.url, login.err, login.stderr)
	}
	s.session = strings.TrimSpace(login.stdout)
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
		return strings.TrimSpace(c.run(append([]string{"--url", s.url, "--token", s.token}, args...)...).stdout)
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
	return cli{s.binary, t.TempDir()}
}

func (s *server) loggedIn(t *testing.T) cli {
	t.Helper()
	c := s.loggedOut(t)
	if out := c.must(t, "auth", "login", "--url", s.url, "--token", s.token); !strings.Contains(out, "as "+username) {
		t.Fatalf("unexpected login output %q", out)
	}
	return c
}

// A home of its own keeps the binary away from the login of whoever runs the tests
type cli struct {
	binary string
	home   string
}

type result struct {
	stdout string
	stderr string
	err    error
}

func (c cli) pipe(stdin string, args ...string) result {
	cmd := exec.Command(c.binary, args...)
	cmd.Env = []string{"HOME=" + c.home, "XDG_CONFIG_HOME=" + filepath.Join(c.home, ".config"), "PATH=" + os.Getenv("PATH")}
	cmd.Stdin = strings.NewReader(stdin)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Run()
	return result{stdout.String(), stderr.String(), err}
}

func (c cli) run(args ...string) result {
	return c.pipe("", args...)
}

func (c cli) must(t *testing.T, args ...string) string {
	t.Helper()
	r := c.run(args...)
	if r.err != nil {
		t.Fatalf("dewey %s: %v\nstdout: %s\nstderr: %s", strings.Join(args, " "), r.err, r.stdout, r.stderr)
	}
	return strings.TrimSpace(r.stdout)
}

func fails(t *testing.T, r result, messages ...string) {
	t.Helper()
	if r.err == nil {
		t.Fatalf("expected an error saying %q, got %q", messages, r.stdout)
	}
	for _, message := range messages {
		if !strings.Contains(r.stderr, message) {
			t.Fatalf("expected an error saying %q, got %q", message, r.stderr)
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

func TestLoginRemembersTheServerAndTheToken(t *testing.T) {
	c := dewey.loggedIn(t)

	if user := c.must(t, "me", "view", "--jq", ".username"); user != username {
		t.Fatalf("logged in as %q", user)
	}
}

func TestLogoutForgetsTheLogin(t *testing.T) {
	c := dewey.loggedIn(t)

	c.must(t, "auth", "logout")

	fails(t, c.run("me", "view"), "--url")
}

func TestWithoutATokenTheErrorSaysHowToGetOne(t *testing.T) {
	fails(t, dewey.loggedOut(t).run("--url", dewey.url, "me", "view"), "Access tokens")
}

func TestALibraryIsCreatedRenamedAndDeleted(t *testing.T) {
	c := dewey.loggedIn(t)
	file := filepath.Join(t.TempDir(), "library.json")
	if err := os.WriteFile(file, []byte(libraryBody(t, "E2E")), 0o600); err != nil {
		t.Fatal(err)
	}

	id := c.must(t, "libraries", "create", "--jq", ".id", "--body", "@"+file)
	t.Cleanup(func() { c.run("libraries", "delete", id) })
	if r := c.pipe(`{"name": "E2E renamed"}`, "libraries", "edit", id, "--body", "-"); r.err != nil {
		t.Fatalf("%v %s", r.err, r.stderr)
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
