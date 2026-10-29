package main

import (
	"fmt"
	"io"
	"strings"

	"github.com/NiclasHaderer/dewey-audiobooks/cli/gen/commands"
	"github.com/spf13/cobra"
)

var annotatedSections = []string{"ARGUMENTS", "BODY", "JSON FIELDS", "OUTPUT", "TYPES"}

func printHelp(out io.Writer, cmd *cobra.Command) error {
	return writeHelp(out, cmd, true)
}

// Shorter than the help, so the error above it stays in view
func printUsage(out io.Writer, cmd *cobra.Command) error {
	return writeHelp(out, cmd, false)
}

func writeHelp(out io.Writer, cmd *cobra.Command, full bool) error {
	var help strings.Builder
	output := commands.OutputTo(out)
	section := func(title string, body string) {
		body = strings.TrimRight(body, "\n")
		if strings.TrimSpace(body) == "" {
			return
		}
		fmt.Fprintf(&help, "%s\n%s\n\n", output.Bold(title), indent(body))
	}

	description := cmd.Long
	if description == "" {
		description = cmd.Short
	}
	if full && description != "" {
		help.WriteString(output.Wrap(description, 0) + "\n\n")
	}
	if cmd.IsAdditionalHelpTopicCommand() {
		section("EXAMPLES", cmd.Example)
		_, err := fmt.Fprint(out, strings.TrimRight(help.String(), "\n")+"\n")
		return err
	}

	usage := cmd.UseLine()
	if cmd.HasAvailableSubCommands() {
		usage = cmd.CommandPath() + " <command> [flags]"
	}
	section("USAGE", usage)
	if full && len(cmd.Aliases) > 0 {
		aliases := make([]string, len(cmd.Aliases))
		for i, alias := range cmd.Aliases {
			aliases[i] = cmd.Parent().CommandPath() + " " + alias
		}
		section("ALIASES", strings.Join(aliases, "\n"))
	}
	if cmd.HasSubCommands() {
		commandSections(cmd, section)
	}
	if !full {
		section("ARGUMENTS", cmd.Annotations["ARGUMENTS"])
	}
	section("FLAGS", flagUsages(cmd.LocalFlags().FlagUsages()))
	if full {
		section("INHERITED FLAGS", flagUsages(cmd.InheritedFlags().FlagUsages()))
		for _, title := range annotatedSections {
			section(title, cmd.Annotations[title])
		}
		section("EXAMPLES", cmd.Example)
		section("LEARN MORE", output.Wrap(`Use dewey <command> <subcommand> --help for more information about a command.
Read about tables, --json and --jq in dewey help formatting.
Read about exit codes in dewey help exit-codes.`, 2))
	} else {
		fmt.Fprintf(&help, "Use %s --help for more information.\n", cmd.CommandPath())
	}
	_, err := fmt.Fprint(out, strings.TrimRight(help.String(), "\n")+"\n")
	return err
}

// One width for every section lines the descriptions up down the whole page
func commandSections(cmd *cobra.Command, section func(title string, body string)) {
	shown := listed(cmd.Commands())
	width := 0
	for _, sub := range shown {
		width = max(width, len(sub.Name())+1)
	}
	for _, group := range cmd.Groups() {
		var members []*cobra.Command
		for _, sub := range shown {
			if sub.GroupID == group.ID {
				members = append(members, sub)
			}
		}
		section(group.Title, commandList(members, width))
	}
	var ungrouped, topics []*cobra.Command
	for _, sub := range shown {
		switch {
		case sub.GroupID != "":
		case sub.IsAdditionalHelpTopicCommand():
			topics = append(topics, sub)
		default:
			ungrouped = append(ungrouped, sub)
		}
	}
	title := "AVAILABLE COMMANDS"
	if len(cmd.Groups()) > 0 {
		title = "ADDITIONAL COMMANDS"
	}
	section(title, commandList(ungrouped, width))
	section("HELP TOPICS", commandList(topics, width))
}

func listed(commands []*cobra.Command) []*cobra.Command {
	var shown []*cobra.Command
	for _, command := range commands {
		if command.IsAvailableCommand() || command.IsAdditionalHelpTopicCommand() {
			shown = append(shown, command)
		}
	}
	return shown
}

func commandList(commands []*cobra.Command, width int) string {
	lines := make([]string, len(commands))
	for i, command := range commands {
		lines[i] = fmt.Sprintf("%-*s  %s", width, command.Name()+":", command.Short)
	}
	return strings.Join(lines, "\n")
}

// pflag indents its usages for a section of its own, the section indents them again
func flagUsages(usages string) string {
	lines := strings.Split(strings.TrimRight(usages, "\n"), "\n")
	for i, line := range lines {
		lines[i] = strings.TrimPrefix(line, "  ")
	}
	return strings.Join(lines, "\n")
}

func indent(text string) string {
	lines := strings.Split(text, "\n")
	for i, line := range lines {
		if line != "" {
			lines[i] = "  " + line
		}
	}
	return strings.Join(lines, "\n")
}
