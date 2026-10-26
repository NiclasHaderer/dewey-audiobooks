// The generated client lives in a directory the generator wipes, so its runtime is tested from here
package clienttest

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/client"
	"github.com/NiclasHaderer/dewey-audiobooks/cli/client/models"
)

func serve(t *testing.T, handler http.HandlerFunc) *client.Client {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	api := client.New(server.URL)
	api.Authorize = func(request *http.Request) error {
		request.Header.Set("Authorization", "Bearer secret")
		return nil
	}
	return api
}

func TestAnUnsetPatchIsLeftOutAndASetNilOneIsSentAsNull(t *testing.T) {
	update := models.BookUpdate{
		Title:       models.PatchOf("Dune"),
		Description: models.PatchOf[*string](nil),
	}

	encoded, err := json.Marshal(update)
	if err != nil {
		t.Fatal(err)
	}

	var fields map[string]any
	if err := json.Unmarshal(encoded, &fields); err != nil {
		t.Fatal(err)
	}
	if len(fields) != 2 || fields["title"] != "Dune" {
		t.Fatalf("only the two set fields are sent, got %s", encoded)
	}
	if value, present := fields["description"]; !present || value != nil {
		t.Fatalf("a Patch set to nil is sent as null, got %s", encoded)
	}
}

func TestAPatchReadBackKnowsItWasSent(t *testing.T) {
	var update models.BookUpdate
	if err := json.Unmarshal([]byte(`{"description":null}`), &update); err != nil {
		t.Fatal(err)
	}
	if !update.Description.Set || update.Description.Value != nil || update.Title.Set {
		t.Fatalf("expected description set to nil and title unset, got %+v", update)
	}
}

func TestOptionalQueryParametersAreOnlySentWhenGiven(t *testing.T) {
	var query string
	var path string
	api := serve(t, func(writer http.ResponseWriter, request *http.Request) {
		path, query = request.URL.Path, request.URL.RawQuery
		_, _ = io.WriteString(writer, `{"items":[],"total":0,"offset":0,"limit":5}`)
	})
	limit := int32(5)

	if _, err := api.ListBooks(context.Background(), "lib/1", &client.ListBooksOptions{Limit: &limit}); err != nil {
		t.Fatal(err)
	}

	if path != "/api/libraries/lib/1/books" && path != "/api/libraries/lib%2F1/books" {
		t.Fatalf("the library is escaped into the path, got %q", path)
	}
	if query != "limit=5" {
		t.Fatalf("only the given option is sent, got %q", query)
	}
}

func TestOnlyASecuredRouteIsAuthorized(t *testing.T) {
	var authorization []string
	api := serve(t, func(writer http.ResponseWriter, request *http.Request) {
		authorization = append(authorization, request.Header.Get("Authorization"))
		if request.URL.Path == "/api/libraries" {
			_, _ = io.WriteString(writer, `[]`)
		}
	})

	if err := api.PingServer(context.Background()); err != nil {
		t.Fatal(err)
	}
	if _, err := api.ListLibraries(context.Background()); err != nil {
		t.Fatal(err)
	}

	if authorization[0] != "" || authorization[1] != "Bearer secret" {
		t.Fatalf("the ping is public and the libraries are not, got %q", authorization)
	}
}

func TestAnErrorAnswerBecomesAnAPIError(t *testing.T) {
	api := serve(t, func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusNotFound)
		_, _ = io.WriteString(writer, `{"error":"Library 'x' not found","status":404}`)
	})

	_, err := api.GetLibrary(context.Background(), "x")

	var apiError *client.APIError
	if !errors.As(err, &apiError) {
		t.Fatalf("expected an APIError, got %v", err)
	}
	if apiError.Status != http.StatusNotFound || apiError.Message != "Library 'x' not found" {
		t.Fatalf("the server's error body is kept, got %+v", apiError)
	}
}
