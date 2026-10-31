import (
	"bytes"
	"cmp"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"reflect"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/cli/go-gh/v2/pkg/jq"
	"github.com/cli/go-gh/v2/pkg/tableprinter"
	"github.com/cli/go-gh/v2/pkg/term"
	"github.com/cli/go-gh/v2/pkg/text"
	"github.com/muesli/reflow/wordwrap"
	"github.com/spf13/cobra"
	"github.com/spf13/pflag"
)

const bodyUsage = "The JSON request `body`: inline, @file to read it from a file, or - to read it from stdin"

type UsageError string

func (e UsageError) Error() string { return string(e) }

// cobra's default shows the help for a mistyped command and exits 0
func Group(cmd *cobra.Command, args []string) error {
	if len(args) == 0 {
		return cmd.Help()
	}
	message := fmt.Sprintf("Unknown command %q for %q", args[0], cmd.CommandPath())
	// cobra only sets the distance for typos in its own lookup, so SuggestionsFor would match prefixes alone
	if cmd.SuggestionsMinimumDistance <= 0 {
		cmd.SuggestionsMinimumDistance = 2
	}
	if suggestions := cmd.SuggestionsFor(args[0]); len(suggestions) > 0 {
		message += "\n\nDid you mean this?\n\t" + strings.Join(suggestions, "\n\t")
	}
	return UsageError(message)
}

// Also names missing required flags: cobra checks the arguments first and would report only those
func Args(names ...string) cobra.PositionalArgs {
	return func(cmd *cobra.Command, given []string) error {
		if len(given) > len(names) {
			if len(names) == 0 {
				return UsageError(fmt.Sprintf("%s takes no arguments, but got %q", cmd.CommandPath(), given))
			}
			return UsageError(fmt.Sprintf(
				"Too many arguments: %s takes %s, but got %q", cmd.CommandPath(), strings.Join(placeholders(names), " "), given,
			))
		}
		missing := placeholders(names[len(given):])
		cmd.Flags().VisitAll(func(flag *pflag.Flag) {
			if _, required := flag.Annotations[cobra.BashCompOneRequiredFlag]; required && !flag.Changed {
				missing = append(missing, "--"+flag.Name)
			}
		})
		if len(missing) > 0 {
			return UsageError("Missing " + strings.Join(missing, " and "))
		}
		return nil
	}
}

func placeholders(names []string) []string {
	wrapped := make([]string, len(names))
	for i, name := range names {
		wrapped[i] = "<" + name + ">"
	}
	return wrapped
}

// A parse error is what the value has to be instead, like "an integer", for the error message to name
type kind[T any] struct {
	name  string
	parse func(raw string) (T, error)
}

var textKind = kind[string]{"string", func(raw string) (string, error) { return raw, nil }}

var uuidPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

var uuidKind = kind[string]{"uuid", func(raw string) (string, error) {
	if !uuidPattern.MatchString(raw) {
		return "", errors.New("a uuid like 6f1c2d3e-4a5b-4c6d-8e7f-0a1b2c3d4e5f")
	}
	return raw, nil
}}

var boolKind = kind[bool]{"bool", func(raw string) (bool, error) {
	value, err := strconv.ParseBool(raw)
	if err != nil {
		return false, errors.New("true or false")
	}
	return value, nil
}}

func intKind[T ~int8 | ~int16 | ~int32 | ~int64]() kind[T] {
	return kind[T]{"int", func(raw string) (T, error) {
		value, err := strconv.ParseInt(raw, 10, reflect.TypeFor[T]().Bits())
		if err != nil {
			return 0, errors.New("an integer")
		}
		return T(value), nil
	}}
}

func numberKind[T ~float32 | ~float64]() kind[T] {
	return kind[T]{"number", func(raw string) (T, error) {
		value, err := strconv.ParseFloat(raw, reflect.TypeFor[T]().Bits())
		if err != nil {
			return 0, errors.New("a number")
		}
		return T(value), nil
	}}
}

func enumKind[T ~string](values ...T) kind[T] {
	return kind[T]{"string", func(raw string) (T, error) {
		names := make([]string, len(values))
		for i, value := range values {
			if strings.EqualFold(string(value), raw) {
				return value, nil
			}
			names[i] = string(value)
		}
		return "", fmt.Errorf("one of %s", strings.Join(names, ", "))
	}}
}

func dateKind[T ~string]() kind[T] {
	return kind[T]{"date", func(raw string) (T, error) {
		if _, err := time.Parse(time.DateOnly, raw); err != nil {
			return "", errors.New("a date like 2024-01-31")
		}
		return T(raw), nil
	}}
}

// Retrying quoted lets a JSON string like a date-time go without quotes on the command line
func jsonKind[T any](name string, wants string) kind[T] {
	return kind[T]{name, func(raw string) (T, error) {
		var value T
		if json.Unmarshal([]byte(raw), &value) == nil || json.Unmarshal([]byte(strconv.Quote(raw)), &value) == nil {
			return value, nil
		}
		return value, errors.New(wants)
	}}
}

type flagValue[T any] struct {
	value *T
	kind  kind[T]
}

func (v flagValue[T]) String() string {
	if v.value == nil {
		return ""
	}
	return fmt.Sprint(*v.value)
}

func (v flagValue[T]) Set(raw string) error {
	value, err := v.kind.parse(raw)
	if err == nil {
		*v.value = value
	}
	return err
}

func (v flagValue[T]) Type() string { return v.kind.name }

func flag[T any](flags *pflag.FlagSet, name string, kind kind[T], usage string) *T {
	value := new(T)
	flags.Var(flagValue[T]{value, kind}, name, usage)
	if _, isBool := any(value).(*bool); isBool {
		flags.Lookup(name).NoOptDefVal = "true"
	}
	return value
}

func arg[T any](raw string, name string, kind kind[T]) (T, error) {
	value, err := kind.parse(raw)
	if err != nil {
		return value, UsageError(fmt.Sprintf("<%s> takes %v, not %q", name, err, raw))
	}
	return value, nil
}

func FlagError(_ *cobra.Command, err error) error {
	var invalid *pflag.InvalidValueError
	var missing *pflag.ValueRequiredError
	switch {
	case errors.As(err, &invalid):
		// The list of valid fields is all the help it needs, so no usage
		if _, ok := invalid.GetFlag().Value.(fieldsValue); ok {
			return errors.Unwrap(invalid)
		}
		return UsageError(fmt.Sprintf(
			"--%s takes %v, not %q", invalid.GetFlag().Name, errors.Unwrap(invalid), invalid.GetValue(),
		))
	case errors.As(err, &missing):
		if fields, ok := missing.GetFlag().Value.(fieldsValue); ok {
			return errors.New(fields.choices("Specify one or more comma-separated fields for --json:"))
		}
	}
	return UsageError(err.Error())
}

func readBody(cmd *cobra.Command, raw string) ([]byte, error) {
	switch {
	case raw == "-":
		return io.ReadAll(cmd.InOrStdin())
	case strings.HasPrefix(raw, "@"):
		content, err := os.ReadFile(raw[1:])
		if err != nil {
			return nil, fmt.Errorf("body: %w", err)
		}
		return content, nil
	default:
		return []byte(raw), nil
	}
}

// Unknown fields fail, so a misspelled one is not silently dropped
func readJSON[T any](cmd *cobra.Command, raw string) (T, error) {
	var value T
	content, err := readBody(cmd, raw)
	if err != nil {
		return value, err
	}
	decoder := json.NewDecoder(bytes.NewReader(content))
	decoder.DisallowUnknownFields()
	err = decoder.Decode(&value)
	var mismatch *json.UnmarshalTypeError
	var syntax *json.SyntaxError
	switch {
	case err == nil:
		return value, nil
	case strings.HasPrefix(err.Error(), "json: unknown field "):
		field := strings.TrimPrefix(err.Error(), "json: unknown field ")
		return value, UsageError(fmt.Sprintf(
			"--body has an unknown field %s. See BODY in --help for the fields it takes.", field,
		))
	case errors.As(err, &mismatch):
		return value, UsageError(fmt.Sprintf(
			"--body: %s must be %s, not a JSON %s. See BODY in --help.", mismatch.Field, jsonType(mismatch.Type), mismatch.Value,
		))
	case errors.As(err, &syntax), errors.Is(err, io.EOF), errors.Is(err, io.ErrUnexpectedEOF):
		return value, UsageError(fmt.Sprintf("--body is not valid JSON: %v", err))
	default:
		return value, UsageError(fmt.Sprintf("--body: %v", err))
	}
}

func jsonType(t reflect.Type) string {
	switch t.Kind() {
	case reflect.Pointer:
		return jsonType(t.Elem())
	case reflect.Bool:
		return "a boolean"
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		return "an integer"
	case reflect.Float32, reflect.Float64:
		return "a number"
	case reflect.String:
		return "a string"
	case reflect.Slice, reflect.Array:
		return "a list"
	case reflect.Struct:
		if t == reflect.TypeFor[time.Time]() {
			return "a date-time string"
		}
	}
	return "an object"
}

type Output struct {
	Terminal bool
	Width    int
	Color    bool
}

func outputOf(cmd *cobra.Command) Output {
	return OutputTo(cmd.OutOrStdout())
}

func OutputTo(out io.Writer) Output {
	file, ok := out.(*os.File)
	if !ok || !term.IsTerminal(file) {
		return Output{}
	}
	terminal := term.FromEnv()
	width, _, err := terminal.Size()
	if err != nil || width <= 0 {
		width = 80
	}
	return Output{Terminal: true, Width: width, Color: terminal.IsColorEnabled()}
}

// Checked while the flag is parsed, so a misspelled field fails before a request changes anything
type fieldsValue struct {
	chosen *[]string
	fields []string
}

func (v fieldsValue) String() string { return strings.Join(*v.chosen, ",") }
func (v fieldsValue) Type() string   { return "fields" }

func (v fieldsValue) Set(raw string) error {
	for _, name := range strings.Split(raw, ",") {
		if !slices.Contains(v.fields, name) {
			return errors.New(v.choices(fmt.Sprintf("Unknown JSON field: %q\nAvailable fields:", name)))
		}
		*v.chosen = append(*v.chosen, name)
	}
	return nil
}

func (v fieldsValue) choices(heading string) string {
	return heading + "\n  " + strings.Join(v.fields, "\n  ")
}

func outputFlags(flags *pflag.FlagSet, jsonExample string, jqExample string, fields ...string) (*[]string, *string) {
	chosen := new([]string)
	if len(fields) > 0 {
		usage := "Output JSON with the specified `fields`, like --json " + jsonExample
		flags.Var(fieldsValue{chosen, fields}, "json", usage)
	}
	return chosen, flags.StringP("jq", "q", "", "Filter JSON output using a jq `expression`, like --jq '"+jqExample+"'")
}

func printResult(cmd *cobra.Command, value any, fields []string, filter string) error {
	if len(fields) > 0 || filter != "" {
		return printFields(cmd, value, fields, filter)
	}
	output := outputOf(cmd)
	result := reflect.Indirect(reflect.ValueOf(value))
	if result.Kind() != reflect.Slice {
		return printRecord(cmd.OutOrStdout(), output, result)
	}
	if result.Len() == 0 {
		if output.Terminal {
			fmt.Fprintln(cmd.ErrOrStderr(), "No results")
		}
		return nil
	}
	return printTable(cmd.OutOrStdout(), output, result)
}

// Lines across a whole wide terminal are hard to follow
const maxTextWidth = 100

func (o Output) Wrap(text string, indent int) string {
	if !o.Terminal {
		return text
	}
	width := min(o.Width, maxTextWidth) - indent
	lines := strings.Split(text, "\n")
	for i, line := range lines {
		body := strings.TrimLeft(line, " ")
		lead := line[:len(line)-len(body)]
		lines[i] = lead + strings.ReplaceAll(wordwrap.String(body, width-len(lead)), "\n", "\n"+lead)
	}
	return strings.Join(lines, "\n")
}

func (o Output) Bold(text string) string { return o.style("\x1b[1m", text) }
func (o Output) Dim(text string) string  { return o.style("\x1b[2m", text) }

func (o Output) style(code string, text string) string {
	if !o.Color || text == "" {
		return text
	}
	return code + text + "\x1b[0m"
}

// A tab or a line break in a value would start a new column or row
var cellText = strings.NewReplacer("\t", " ", "\n", " ")

func printTable(out io.Writer, output Output, rows reflect.Value) error {
	row := rows.Type().Elem()
	if row.Kind() == reflect.Pointer {
		row = row.Elem()
	}
	if !isObject(row) {
		for i := 0; i < rows.Len(); i++ {
			fmt.Fprintln(out, format(rows.Index(i), output))
		}
		return nil
	}
	columns := tableColumns(rows, row, output)
	table := tableprinter.New(out, output.Terminal, output.Width)
	if output.Terminal {
		names := make([]string, len(columns))
		for i, column := range columns {
			names[i] = strings.ToUpper(words(jsonName(row.Field(column))))
		}
		table.AddHeader(names, tableprinter.WithColor(output.Dim))
	}
	for i := 0; i < rows.Len(); i++ {
		for _, column := range columns {
			cell := cellText.Replace(cellOf(rows.Index(i), column, output))
			// An id is what the next command takes, so a cut one is of no use
			if uuidPattern.MatchString(cell) {
				table.AddField(cell, tableprinter.WithTruncate(nil))
			} else {
				table.AddField(cell)
			}
		}
		table.EndRow()
	}
	return table.Render()
}

// A pipe keeps columns that are empty in every row, so a script can count on which column is which
func tableColumns(rows reflect.Value, row reflect.Type, output Output) []int {
	var columns []int
	for i := 0; i < row.NumField(); i++ {
		field := row.Field(i)
		if field.IsExported() && isSingleValue(field.Type) && !(output.Terminal && emptyInEveryRow(rows, i)) {
			columns = append(columns, i)
		}
	}
	return columns
}

func emptyInEveryRow(rows reflect.Value, field int) bool {
	for i := 0; i < rows.Len(); i++ {
		if cellOf(rows.Index(i), field, Output{}) != "" {
			return false
		}
	}
	return true
}

// Lists of objects and multi-line text follow as sections of their own, since a table cell holds one line. A pipe
// gets them unindented, as the indent would end up in the first column.
func printRecord(out io.Writer, output Output, value reflect.Value) error {
	if value.Kind() != reflect.Struct {
		_, err := fmt.Fprintln(out, format(value, output))
		return err
	}
	table := tableprinter.New(out, output.Terminal, output.Width)
	sections := addFields(table, output, value, "")
	if err := table.Render(); err != nil {
		return err
	}
	for _, section := range sections {
		fmt.Fprintln(out, output.Dim(section.label))
		if !output.Terminal {
			if err := section.print(out, output); err != nil {
				return err
			}
			continue
		}
		var printed bytes.Buffer
		indented := output
		indented.Width -= len(sectionIndent)
		if err := section.print(&printed, indented); err != nil {
			return err
		}
		for _, line := range strings.SplitAfter(printed.String(), "\n") {
			if strings.TrimSpace(line) != "" {
				line = sectionIndent + line
			}
			fmt.Fprint(out, line)
		}
	}
	return nil
}

const sectionIndent = "  "

type section struct {
	label string
	print func(out io.Writer, output Output) error
}

func addFields(table tableprinter.TablePrinter, output Output, value reflect.Value, indent string) (sections []section) {
	for i := 0; i < value.NumField(); i++ {
		field := value.Type().Field(i)
		if !field.IsExported() {
			continue
		}
		label := strings.ToLower(words(jsonName(field))) + ":"
		nested := reflect.Indirect(value.Field(i))
		switch {
		case output.Terminal && nested.Kind() == reflect.Struct && isObject(nested.Type()):
			table.AddField(output.Dim(indent + label))
			table.AddField("")
			table.EndRow()
			sections = append(sections, addFields(table, output, nested, indent+"  ")...)
		case nested.Kind() == reflect.Slice && isObject(nested.Type().Elem()):
			if nested.Len() > 0 || !output.Terminal {
				sections = append(sections, section{label, func(out io.Writer, output Output) error { return printTable(out, output, nested) }})
			}
		default:
			cell := format(value.Field(i), output)
			if output.Terminal && cell == "" {
				continue
			}
			if strings.Contains(cell, "\n") {
				sections = append(sections, section{label, func(out io.Writer, output Output) error {
					_, err := fmt.Fprintln(out, output.Wrap(cell, 0))
					return err
				}})
				continue
			}
			table.AddField(output.Dim(indent + label))
			table.AddField(cell)
			table.EndRow()
		}
	}
	return sections
}

func isObject(t reflect.Type) bool {
	if t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	return t.Kind() == reflect.Struct && t != reflect.TypeFor[time.Time]()
}

func words(name string) string {
	var spelled strings.Builder
	runes := []rune(name)
	for i, r := range runes {
		upper := r >= 'A' && r <= 'Z'
		if i > 0 && upper && !(runes[i-1] >= 'A' && runes[i-1] <= 'Z') {
			spelled.WriteRune(' ')
		}
		spelled.WriteRune(r)
	}
	return spelled.String()
}

func isSingleValue(t reflect.Type) bool {
	if t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	if t == reflect.TypeFor[time.Time]() {
		return true
	}
	switch t.Kind() {
	case reflect.Struct, reflect.Slice, reflect.Map, reflect.Interface:
		return false
	}
	return true
}

// A nil row of a list of pointers has no fields, so each of its cells is empty
func cellOf(row reflect.Value, field int, output Output) string {
	row = reflect.Indirect(row)
	if !row.IsValid() {
		return ""
	}
	return format(row.Field(field), output)
}

// The zero Value is what a nil response becomes once its pointer is followed
func format(value reflect.Value, output Output) string {
	if !value.IsValid() {
		return ""
	}
	if value.Kind() == reflect.Pointer || value.Kind() == reflect.Interface {
		if value.IsNil() {
			return ""
		}
		return format(value.Elem(), output)
	}
	if moment, ok := value.Interface().(time.Time); ok {
		if output.Terminal {
			return text.RelativeTimeAgo(time.Now(), moment)
		}
		return moment.Format(time.RFC3339)
	}
	switch value.Kind() {
	case reflect.Slice:
		parts := make([]string, value.Len())
		for i := range parts {
			parts[i] = format(value.Index(i), output)
		}
		return strings.Join(parts, ", ")
	case reflect.Struct, reflect.Map:
		encoded, _ := json.Marshal(value.Interface())
		return string(encoded)
	}
	return fmt.Sprint(value.Interface())
}

func jsonName(field reflect.StructField) string {
	name, _, _ := strings.Cut(field.Tag.Get("json"), ",")
	if name == "" {
		return field.Name
	}
	return name
}

func printFields(cmd *cobra.Command, value any, fields []string, filter string) error {
	encoded, err := json.Marshal(value)
	if err != nil {
		return err
	}
	if len(fields) > 0 {
		pick := "{" + strings.Join(fields, ", ") + "}"
		filter = fmt.Sprintf(`if type == "array" then map(%s) else %s end | %s`, pick, pick, cmp.Or(filter, "."))
	}
	return jq.EvaluateFormatted(bytes.NewReader(encoded), cmd.OutOrStdout(), filter, "  ", outputOf(cmd).Color)
}

const outputUsage = "Write the response to this `file` instead of stdout"

// Control bytes in a binary response can leave a terminal in a broken state
var errBinaryOnTerminal = errors.New("The response is binary. Pass --output <file> or redirect stdout to a file.")

func CopyOut(cmd *cobra.Command, stream io.ReadCloser, output string) error {
	defer stream.Close()
	var destination io.Writer = cmd.OutOrStdout()
	if output != "" {
		file, err := os.Create(output)
		if err != nil {
			return fmt.Errorf("output: %w", err)
		}
		defer file.Close()
		destination = file
	} else if outputOf(cmd).Terminal {
		return errBinaryOnTerminal
	}
	_, err := io.Copy(destination, stream)
	return err
}
