package main

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/gen/commands"
	"github.com/NiclasHaderer/dewey-audiobooks/cli/internal/config"
	"github.com/spf13/cobra"
	"golang.org/x/term"
)

func authCommand(configPath string, flags *config.Settings, getenv func(string) string) *cobra.Command {
	auth := &cobra.Command{
		Use:   "auth <command>",
		Short: "Log in to a server",
		Long: `Log in to a server.

dewey logs in with an access token, which you create under Settings > Access tokens in the web app.`,
		RunE: commands.Group,
	}
	auth.AddCommand(loginCommand(configPath, flags), statusCommand(configPath, flags, getenv), logoutCommand(configPath))
	return auth
}

func loginCommand(configPath string, flags *config.Settings) *cobra.Command {
	return &cobra.Command{
		Use:   "login",
		Short: "Log in to a server with an access token",
		Long: `Log in to a server with an access token and save both for later commands.

Without --token, dewey reads the token from stdin, which keeps it out of your shell history.`,
		Example: `# Log in, typing or pasting the token when asked for it
$ dewey auth login --url http://localhost:8080

# Log in with a token from a file
$ dewey auth login --url http://localhost:8080 < token.txt`,
		Args: commands.Args(),
		RunE: func(cmd *cobra.Command, _ []string) error {
			settings := *flags
			if settings.URL == "" {
				return errors.New("Pass the server to log in to with --url")
			}
			if settings.Token == "" {
				token, err := readToken(cmd)
				if err != nil {
					return fmt.Errorf("Could not read the access token: %w", err)
				}
				settings.Token = token
			}
			c, err := newClient(settings)
			if err != nil {
				return err
			}
			user, err := c.GetCurrentUser(cmd.Context())
			if err != nil {
				return fmt.Errorf("Could not log in to %s: %w", settings.URL, err)
			}
			if err := config.Save(configPath, settings); err != nil {
				return err
			}
			fmt.Fprintf(cmd.OutOrStdout(), "Logged in to %s as %s\n", settings.URL, user.Username)
			return nil
		},
	}
}

func readToken(cmd *cobra.Command) (string, error) {
	if file, ok := cmd.InOrStdin().(*os.File); ok && term.IsTerminal(int(file.Fd())) {
		fmt.Fprint(cmd.ErrOrStderr(), "Paste an access token: ")
		token, err := term.ReadPassword(int(file.Fd()))
		fmt.Fprintln(cmd.ErrOrStderr())
		return strings.TrimSpace(string(token)), err
	}
	line, err := bufio.NewReader(cmd.InOrStdin()).ReadString('\n')
	if err != nil && line == "" {
		return "", err
	}
	return strings.TrimSpace(line), nil
}

func statusCommand(configPath string, flags *config.Settings, getenv func(string) string) *cobra.Command {
	return &cobra.Command{
		Use:   "status",
		Short: "Show the server and user you are logged in as",
		Long:  "Show the server and user you are logged in as, and whether each comes from a flag, the environment or the saved login.",
		Args:  commands.Args(),
		RunE: func(cmd *cobra.Command, _ []string) error {
			settings, err := config.Resolve(*flags, getenv, configPath)
			if err != nil {
				return err
			}
			source := func(flag string, env string, value string) string {
				switch {
				case value == "":
					return "nowhere"
				case flag != "":
					return "the flag"
				case getenv(env) != "":
					return env
				}
				return configPath
			}
			c, err := newClient(settings)
			if err != nil {
				return err
			}
			user, err := c.GetCurrentUser(cmd.Context())
			if err != nil {
				return fmt.Errorf("Not logged in to %s: %w", settings.URL, err)
			}
			role := "a user"
			if user.Permissions.IsAdmin {
				role = "an admin"
			}
			out := cmd.OutOrStdout()
			fmt.Fprintln(out, settings.URL)
			fmt.Fprintf(out, "  Logged in as %s, %s\n", user.Username, role)
			fmt.Fprintf(out, "  Server from %s\n", source(flags.URL, "DEWEY_URL", settings.URL))
			fmt.Fprintf(out, "  Token from %s\n", source(flags.Token, "DEWEY_TOKEN", settings.Token))
			return nil
		},
	}
}

func logoutCommand(configPath string) *cobra.Command {
	return &cobra.Command{
		Use:   "logout",
		Short: "Forget the saved server and access token",
		Long:  "Forget the saved server and access token. The token keeps working until you revoke it under Settings > Access tokens in the web app.",
		Args:  commands.Args(),
		RunE: func(cmd *cobra.Command, _ []string) error {
			if err := os.Remove(configPath); err != nil && !errors.Is(err, os.ErrNotExist) {
				return err
			}
			fmt.Fprintln(cmd.OutOrStdout(), "Logged out")
			return nil
		},
	}
}
