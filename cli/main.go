package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"strings"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/gen/client"
	"github.com/NiclasHaderer/dewey-audiobooks/cli/gen/commands"
	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/config"
	"github.com/spf13/cobra"
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
	defer stop()
	root := newRoot(path, os.Getenv)
	cmd, err := root.ExecuteContextC(ctx)
	code := exitCode(ctx, err)
	if code != 0 {
		report(os.Stderr, cmd, err, code)
	}
	stop()
	os.Exit(code)
}

type notLoggedIn string

func (e notLoggedIn) Error() string { return string(e) }

func exitCode(ctx context.Context, err error) int {
	var unauthorized *client.APIError
	switch {
	case err == nil:
		return 0
	case ctx.Err() != nil:
		return 2
	case errors.As(err, new(notLoggedIn)),
		errors.As(err, &unauthorized) && unauthorized.Status == http.StatusUnauthorized:
		return 4
	default:
		return 1
	}
}

func report(out io.Writer, cmd *cobra.Command, err error, code int) {
	var usage commands.UsageError
	var apiError *client.APIError
	var unreachable *url.Error
	message := err.Error()
	output := commands.OutputTo(out)
	say := func(format string, args ...any) { fmt.Fprintln(out, output.Wrap(fmt.Sprintf(format, args...), 0)) }
	switch {
	case code == 2:
		say("Cancelled")
	case code == 4:
		say("%s", message)
		say("To log in, create a token under Settings > Access tokens in the web app and run dewey auth login --url <server>.")
	case errors.As(err, &usage):
		say("%s\n", strings.TrimRight(message, "\n"))
		if cmd != nil {
			_ = printUsage(out, cmd)
		}
	case errors.As(err, &apiError) && apiError.Status == http.StatusForbidden:
		say("%s. Your account or access token is not allowed to do this.", message)
	case errors.As(err, &unreachable):
		target, _, _ := strings.Cut(unreachable.URL, "/api")
		say("Could not reach the server at %s: %v.", target, rootCause(unreachable.Err))
		say("Check that the server is running and that --url, DEWEY_URL or the saved login point to it.")
	default:
		say("%s", message)
	}
}

// Drops the dial tcp and address wrappers, which say nothing to a person
func rootCause(err error) error {
	for {
		next := errors.Unwrap(err)
		if next == nil {
			return err
		}
		err = next
	}
}

func newRoot(configPath string, getenv func(string) string) *cobra.Command {
	root := &cobra.Command{
		Use:   "dewey",
		Short: "Work with a Dewey audiobook server",
		Example: `$ dewey auth login --url http://localhost:8080
$ dewey libraries list
$ dewey books list <library-id> --jq '.items[].title'`,
		Args:          cobra.ArbitraryArgs,
		RunE:          commands.Group,
		SilenceUsage:  true,
		SilenceErrors: true,
	}
	var flags config.Settings
	root.PersistentFlags().StringVar(&flags.URL, "url", "", "The `server` to talk to, like http://localhost:8080")
	root.PersistentFlags().StringVar(&flags.Token, "token", "", "The access `token` to log in with")
	root.PersistentFlags().BoolP("help", "h", false, "Show help for command")

	api := func() (*client.Client, error) {
		settings, err := config.Resolve(flags, getenv, configPath)
		if err != nil {
			return nil, err
		}
		return newClient(settings)
	}
	commands.Register(root, api)
	root.AddCommand(authCommand(configPath, &flags, getenv))
	root.AddCommand(topics()...)
	root.InitDefaultCompletionCmd()
	root.SetHelpFunc(func(cmd *cobra.Command, _ []string) { _ = printHelp(cmd.OutOrStdout(), cmd) })
	root.SetFlagErrorFunc(commands.FlagError)
	return root
}

func newClient(settings config.Settings) (*client.Client, error) {
	if settings.URL == "" {
		return nil, notLoggedIn("No server set. Pass --url, set DEWEY_URL or run dewey auth login.")
	}
	if parsed, err := url.Parse(settings.URL); err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.Host == "" {
		return nil, fmt.Errorf("%q is not a server address, pass one with its scheme like http://localhost:8080", settings.URL)
	}
	c := client.New(settings.URL)
	c.Authorize = func(request *http.Request) error {
		if settings.Token == "" {
			return notLoggedIn("No access token set. Pass --token, set DEWEY_TOKEN or run dewey auth login.")
		}
		request.Header.Set("Authorization", "Bearer "+settings.Token)
		return nil
	}
	return c, nil
}
