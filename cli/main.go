package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/app"
	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/config"
)

func main() {
	path, err := config.Path()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	// A read from the terminal ignores the context, so a second Ctrl-C has to end the process the default way
	context.AfterFunc(ctx, stop)
	code := app.Run(ctx, path, os.Getenv, os.Args[1:], app.IO{In: os.Stdin, Out: os.Stdout, Err: os.Stderr})
	stop()
	os.Exit(code)
}
