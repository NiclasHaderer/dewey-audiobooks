package main

import "github.com/spf13/cobra"

func topics() []*cobra.Command {
	return []*cobra.Command{
		{
			Use:   "environment",
			Short: "Environment variables",
			Long: `DEWEY_URL
  The server to talk to, like http://localhost:8080.
  Overrides the server saved by dewey auth login. --url overrides it.

DEWEY_TOKEN
  The access token to log in with.
  Overrides the token saved by dewey auth login. --token overrides it.
  Create a token under Settings > Access tokens in the web app.

dewey auth login saves the server and the token in your config directory. Run dewey auth status to see where.`,
		},
		{
			Use:   "exit-codes",
			Short: "Exit codes",
			Long: `0  Success
1  Failure
2  Cancelled
4  Not logged in, or the server rejected the token`,
		},
		{
			Use:   "formatting",
			Short: "Output formats",
			Long: `By default, a list prints as a table and an object as one field per line. On a terminal, tables have a header and fit the terminal's width. In a pipe, they are tab-separated values without a header.

--json <fields>
  Prints the response as JSON with only the fields you name, like --json id,name.
  --json without fields lists the available ones.

--jq <expression>
  Filters the JSON with a jq expression, like --jq '.[].name'. jq need not be installed.
  --jq . prints the whole response.
  Manual: https://jqlang.github.io/jq/manual/

--output <file>
  Commands that download a file, like dewey files audio, write it to this file.
  Without it, they write to stdout only when it is redirected.`,
			Example: `$ dewey libraries list
$ dewey libraries list --json id,name
$ dewey libraries list --jq '.[].name'`,
		},
	}
}
