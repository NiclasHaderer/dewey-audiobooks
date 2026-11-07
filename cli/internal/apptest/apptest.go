package apptest

import (
	"bytes"
	"context"
	"strings"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/app"
)

// Its own config file and environment keep dewey away from the login and DEWEY_ variables of whoever runs the tests
type CLI struct {
	Config string
	Env    map[string]string
}

type Result struct {
	Stdout string
	Stderr string
	Code   int
}

func (c CLI) Pipe(stdin string, args ...string) Result {
	var stdout, stderr bytes.Buffer
	getenv := func(key string) string { return c.Env[key] }
	code := app.Run(context.Background(), c.Config, getenv, args, app.IO{In: strings.NewReader(stdin), Out: &stdout, Err: &stderr})
	return Result{stdout.String(), stderr.String(), code}
}

func (c CLI) Run(args ...string) Result {
	return c.Pipe("", args...)
}
