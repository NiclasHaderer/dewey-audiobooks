import (
	"bytes"
	"reflect"
	"testing"

	"github.com/spf13/cobra"
)

type date string

type row struct {
	Name string `json:"name"`
}

func TestADateMustBeOne(t *testing.T) {
	kind := dateKind[date]()

	if value, err := kind.parse("2024-01-31"); err != nil || value != "2024-01-31" {
		t.Fatalf("parsed %q, %v", value, err)
	}
	if _, err := kind.parse("yesterday"); err == nil {
		t.Fatal("parsed yesterday as a date")
	}
}

func TestANullResponsePrintsNothing(t *testing.T) {
	var out bytes.Buffer
	cmd := &cobra.Command{}
	cmd.SetOut(&out)

	if err := printResult(cmd, (*row)(nil), nil, ""); err != nil {
		t.Fatal(err)
	}
	if out.String() != "\n" {
		t.Fatalf("printed %q", out.String())
	}
}

func TestANullRowPrintsEmptyCells(t *testing.T) {
	var out bytes.Buffer

	if err := printTable(&out, Output{}, reflect.ValueOf([]*row{{Name: "a"}, nil})); err != nil {
		t.Fatal(err)
	}
	if out.String() != "a\n\n" {
		t.Fatalf("printed %q", out.String())
	}
}
