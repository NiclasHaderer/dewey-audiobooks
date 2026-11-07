package app_test

import (
	"path/filepath"
	"strings"
	"testing"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/apptest"
)

func dewey(t *testing.T, stdin string, args ...string) apptest.Result {
	return apptest.CLI{Config: filepath.Join(t.TempDir(), "config.json")}.Pipe(stdin, args...)
}

func fails(t *testing.T, r apptest.Result, message string) {
	t.Helper()
	if r.Code == 0 || !strings.Contains(r.Stderr, message) {
		t.Fatalf("expected an error saying %q, got %d %q", message, r.Code, r.Stderr)
	}
}

func TestATypoInACommandGetsASuggestion(t *testing.T) {
	fails(t, dewey(t, "", "boks"), "Did you mean this?\n\tbooks")
}

func TestHelpForAnUnknownTopicFails(t *testing.T) {
	fails(t, dewey(t, "", "help", "bogus"), `Unknown command "bogus"`)
}

func TestHelpForACommandShowsItsHelp(t *testing.T) {
	r := dewey(t, "", "help", "books", "list")

	if r.Code != 0 || !strings.Contains(r.Stdout, "dewey books list <library-id>") {
		t.Fatalf("expected the help of books list, got %d %q %q", r.Code, r.Stdout, r.Stderr)
	}
}

func TestAServerWithoutASchemeIsRejected(t *testing.T) {
	fails(t, dewey(t, "", "--url", "localhost:8080", "--token", "t", "me", "view"), "like http://localhost:8080")
}

func TestLoginRejectsAnEmptyToken(t *testing.T) {
	fails(t, dewey(t, "\n", "auth", "login", "--url", "http://localhost:1"), "The access token is empty")
}
