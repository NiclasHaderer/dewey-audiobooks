package io.dewey.openapi.client.go

import io.dewey.openapi.client.common.ClientGenerator
import io.dewey.openapi.client.common.ClientPart
import io.dewey.openapi.client.common.splitWords
import io.dewey.openapi.common.getResourceContent
import io.dewey.openapi.ktor.OpenApiRoute
import io.dewey.openapi.wire.Body
import io.dewey.openapi.wire.EnumDeclaration
import io.dewey.openapi.wire.ObjectDeclaration
import io.dewey.openapi.wire.Parameter
import io.dewey.openapi.wire.ScalarKind
import io.dewey.openapi.wire.WireModel
import io.dewey.openapi.wire.WireType
import io.ktor.server.application.Application
import java.io.File
import java.nio.file.Path

/** Where a route lands on the command line: `group name`, or just `name` for a route without a tag. */
data class CommandName(
    val group: String?,
    val name: String,
)

/**
 * The group is the route's tag and the name its summary without the tag's noun, so "Get book" tagged "Books" is
 * `books get` and "List access tokens" tagged "Access tokens" is `access-tokens list`.
 */
fun commandName(
    summary: String,
    tag: String?,
    command: String? = null,
): CommandName {
    val words = words(summary)
    if (tag == null) return CommandName(null, command ?: words.joinToString("-"))
    val noun = words(tag)
    if (command != null) return CommandName(noun.joinToString("-"), command)
    val singular = noun.dropLast(1) + singular(noun.last())
    val name =
        listOf(noun, singular)
            .firstNotNullOfOrNull { form ->
                val start = words.windowed(form.size).indexOf(form)
                if (start < 0) null else words.take(start) + words.drop(start + form.size)
            } ?: words
    check(name.isNotEmpty()) { "The summary \"$summary\" names only its tag \"$tag\", add a verb to it" }
    return CommandName(noun.joinToString("-"), name.joinToString("-"))
}

private fun words(text: String): List<String> =
    text
        .lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() }

private fun singular(word: String): String =
    when {
        word.endsWith("ies") -> word.dropLast(3) + "y"
        word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

// What a command cannot do without is an argument and the rest are flags
private val GoRoute.positional get() = pathParameters + requiredQuery

// The root command owns these, so a generated flag of the same name would shadow them
private val RESERVED_FLAGS = setOf("body", "output", "json", "jq", "help", "url", "token")

// Names the generated command function uses itself or imports, which a parameter cannot take
private val RESERVED_LOCALS =
    setOf(
        "cmd",
        "args",
        "flags",
        "api",
        "c",
        "err",
        "out",
        "opts",
        "requestBody",
        "stream",
        "flag",
        "arg",
        "client",
        "models",
        "time",
        "cobra",
    )

/**
 * The JSON a command reads and prints, spelled out for whoever calls it: a person or an agent that has nothing but the
 * help text. Every object type it mentions is described once, in the order it is first met.
 */
private class SchemaText(
    private val model: WireModel,
) {
    private val pending = ArrayDeque<Pair<String, () -> String>>()
    private val described = mutableSetOf<String>()

    fun text(
        type: WireType,
        bindings: Map<String, WireType> = emptyMap(),
    ): String {
        val base =
            when (type) {
                is WireType.Scalar -> {
                    when (type.kind) {
                        ScalarKind.STRING, ScalarKind.CHAR -> "string"
                        ScalarKind.UUID -> "uuid"
                        ScalarKind.DATE -> "date (YYYY-MM-DD)"
                        ScalarKind.DATE_TIME -> "date-time (RFC 3339)"
                        ScalarKind.BOOLEAN -> "boolean"
                        ScalarKind.BYTE, ScalarKind.SHORT, ScalarKind.INT, ScalarKind.LONG -> "integer"
                        ScalarKind.FLOAT, ScalarKind.DOUBLE -> "number"
                    }
                }

                is WireType.ListOf -> {
                    val enum = (type.element as? WireType.Named)?.let { model.declaration(it.name) } as? EnumDeclaration
                    if (enum != null) {
                        "list of any of ${enum.values.joinToString(", ")}"
                    } else {
                        "list of ${text(type.element, bindings)}"
                    }
                }

                is WireType.MapOf -> {
                    "map of ${text(type.key, bindings)} to ${text(type.value, bindings)}"
                }

                is WireType.TypeParameter -> {
                    bindings[type.name]?.let { text(it) } ?: type.name
                }

                is WireType.Named -> {
                    when (val declaration = model.declaration(type.name)) {
                        is EnumDeclaration -> {
                            "one of ${declaration.values.joinToString(", ")}"
                        }

                        is ObjectDeclaration -> {
                            val arguments = type.arguments.map { resolve(it, bindings) }
                            val name =
                                if (arguments.isEmpty()) {
                                    type.name
                                } else {
                                    "${type.name} of ${arguments.joinToString(", ") { text(it) }}"
                                }
                            pending.addLast(name to { fields(declaration, arguments, "  ") })
                            name
                        }
                    }
                }
            }
        return if (type.nullable) "$base or null" else base
    }

    // An object's own fields, for the body or the output it is
    fun fieldsOf(type: WireType): String? {
        val named = (if (type is WireType.ListOf) type.element else type) as? WireType.Named ?: return null
        val declaration = model.declaration(named.name) as? ObjectDeclaration ?: return null
        described += text(named).removeSuffix(" or null")
        return fields(declaration, named.arguments, "")
    }

    fun where(): String? =
        buildString {
            while (pending.isNotEmpty()) {
                val (name, fields) = pending.removeFirst()
                if (described.add(name)) append("$name\n").append(fields())
            }
        }.ifEmpty { null }

    private fun fields(
        declaration: ObjectDeclaration,
        arguments: List<WireType>,
        indent: String,
    ): String {
        val bindings = declaration.typeParameters
            .map { it.name }
            .zip(arguments)
            .toMap()
        return table(
            declaration.properties.map { property ->
                val notes = listOfNotNull(if (property.optional) "optional" else null, property.description)
                property.name to (listOf(text(property.type, bindings)) + notes).joinToString(", ")
            },
            indent,
        )
    }

    // A JSON value of the type for an example: an object with the fields it needs, or its first one when it needs none
    fun sample(
        type: WireType,
        depth: Int = 0,
    ): String =
        when (type) {
            is WireType.Scalar -> {
                when (type.kind) {
                    ScalarKind.STRING, ScalarKind.CHAR -> "\"...\""
                    ScalarKind.UUID -> "\"<uuid>\""
                    ScalarKind.DATE -> "\"2024-01-31\""
                    ScalarKind.DATE_TIME -> "\"2024-01-31T12:00:00Z\""
                    ScalarKind.BOOLEAN -> "true"
                    ScalarKind.BYTE, ScalarKind.SHORT, ScalarKind.INT, ScalarKind.LONG -> "0"
                    ScalarKind.FLOAT, ScalarKind.DOUBLE -> "0.5"
                }
            }

            is WireType.ListOf -> {
                "[]"
            }

            is WireType.MapOf -> {
                "{}"
            }

            is WireType.TypeParameter -> {
                "null"
            }

            is WireType.Named -> {
                when (val declaration = model.declaration(type.name)) {
                    is EnumDeclaration -> {
                        "\"${declaration.values.first()}\""
                    }

                    is ObjectDeclaration -> {
                        if (depth > 1) {
                            "{}"
                        } else {
                            val bindings = declaration.typeParameters
                                .map { it.name }
                                .zip(type.arguments)
                                .toMap()
                            declaration.properties
                                .filter { !it.optional }
                                .ifEmpty { declaration.properties.take(1) }
                                .joinToString(", ", "{", "}") {
                                    "\"${it.name}\": ${sample(resolve(it.type, bindings), depth + 1)}"
                                }
                        }
                    }
                }
            }
        }

    private fun resolve(
        type: WireType,
        bindings: Map<String, WireType>,
    ): WireType =
        when (type) {
            is WireType.TypeParameter -> bindings[type.name] ?: type
            is WireType.ListOf -> type.copy(element = resolve(type.element, bindings))
            is WireType.MapOf -> type.copy(key = resolve(type.key, bindings), value = resolve(type.value, bindings))
            is WireType.Named -> type.copy(arguments = type.arguments.map { resolve(it, bindings) })
            is WireType.Scalar -> type
        }
}

private fun table(
    rows: List<Pair<String, String>>,
    indent: String,
): String {
    val width = rows.maxOfOrNull { it.first.length } ?: 0
    return rows.joinToString("") { (name, text) -> "$indent${name.padEnd(width)}  $text\n" }
}

class GoCliGenerator(
    override val routes: List<OpenApiRoute>,
    private val goRoutes: List<GoRoute>,
    private val model: WireModel,
    private val packageName: String,
    // The import path of the client package the commands call, the models package is imported below it
    private val clientImportPath: String,
    // The guards that refuse the access tokens the CLI logs in with, so a route behind one gets no command
    private val loginOnlyGuards: Set<String>,
    dist: Path,
    fileWriter: ((File, String) -> Unit)?,
    cleanDistPackage: Boolean,
) : ClientGenerator(dist, fileWriter, cleanDistPackage) {
    private val commands: List<Pair<CommandName, GoRoute>> by lazy {
        goRoutes
            .filter { it.route.inCli && it.route.secured?.name !in loginOnlyGuards }
            .map { commandName(it.route.summary!!, it.route.tags.firstOrNull(), it.route.command) to it }
            .also { all ->
                val duplicates = all.groupBy { it.first }.filterValues { it.size > 1 }
                check(duplicates.isEmpty()) {
                    duplicates.entries.joinToString("; ") { (name, routes) ->
                        "${routes.joinToString { it.second.route.summary!! }} would all be " +
                            "`${listOfNotNull(name.group, name.name).joinToString(" ")}`, give them different summaries"
                    }
                }
            }
    }

    override fun generateClient(): List<ClientPart> {
        val runtime = GENERATED_HEADER + "package $packageName\n\n" + getResourceContent("/go/commands/runtime.go")
        val runtimeTest =
            GENERATED_HEADER + "package $packageName\n\n" + getResourceContent("/go/commands/runtime_test.go")
        return listOf(
            ClientPart("runtime.go", runtime),
            ClientPart("runtime_test.go", runtimeTest),
            ClientPart("commands.go", commandsFile()),
        )
    }

    private fun commandsFile(): String {
        val functions = commands.map { (name, route) -> command(name, route) }
        // Read off the types the commands spell out, as the help texts in them may mention anything
        val parameters = commands.flatMap { (_, route) -> route.positional + route.optionalQuery }
        val typed =
            parameters.map { kind(it) } +
                commands.mapNotNull { (_, route) -> (route.requestBody as? Body.Json)?.type?.go(MODELS) }
        val imports =
            buildList {
                if (parameters.any { it.type.usesTime() }) add("time")
                add("")
                add("github.com/spf13/cobra")
                add(clientImportPath)
                if (typed.any { MODELS in it }) add("$clientImportPath/models")
            }.dropWhile { it.isEmpty() }
        return buildString {
            append(GENERATED_HEADER)
            append("package $packageName\n\n")
            append("import (\n")
            imports.forEach { append(if (it.isEmpty()) "\n" else "\t\"$it\"\n") }
            append(")\n\n")
            append("type API func() (*client.Client, error)\n\n")
            append(register())
            append("\n")
            append(functions.joinToString("\n"))
        }
    }

    private fun register(): String =
        buildString {
            append("func Register(root *cobra.Command, api API) {\n")
            commands.mapNotNull { it.second.route.tagGroup }.distinct().sorted().forEach { section ->
                val title = goString("${section.uppercase()} COMMANDS")
                append("\troot.AddGroup(&cobra.Group{ID: ${goString(section.lowercase())}, Title: $title})\n")
            }
            commands.groupBy { it.first.group }.toSortedMap(nullsFirst()).forEach { (group, members) ->
                val adds = members.joinToString(", ") { "${functionName(it.second)}(api)" }
                if (group == null) {
                    append("\troot.AddCommand($adds)\n")
                } else {
                    // Suffixed, so a group named like a parameter of Register or an import cannot shadow it
                    val variable = parameterName(group) + "Group"
                    // One route of the group describing its tag is enough
                    val short =
                        members.firstNotNullOfOrNull { it.second.route.tagDescription }
                            ?: members
                                .first()
                                .second.route.tags
                                .first()
                    val section = members.firstNotNullOfOrNull { it.second.route.tagGroup }?.lowercase()
                    val fields =
                        listOfNotNull(
                            "Use: \"$group\"",
                            "Short: ${goString(short)}",
                            section?.let { "GroupID: \"$it\"" },
                            "RunE: Group",
                        )
                    append("\t$variable := &cobra.Command{${fields.joinToString(", ")}}\n")
                    append("\t$variable.AddCommand($adds)\n")
                    append("\troot.AddCommand($variable)\n")
                }
            }
            append("}\n")
        }

    private fun functionName(route: GoRoute) = route.functionName.replaceFirstChar(Char::lowercaseChar) + "Command"

    private fun command(
        name: CommandName,
        goRoute: GoRoute,
    ): String =
        buildString {
            val route = goRoute.route
            goRoute.optionalQuery.forEach { flag ->
                check(flagName(flag) !in RESERVED_FLAGS) {
                    "The query parameter ${flag.name} of ${route.summary} clashes with the --${flagName(flag)} flag"
                }
            }
            check(goRoute.requestBody is Body.Json || goRoute.requestBody == Body.None) {
                "${route.summary} takes a ${goRoute.requestBody} body, which the CLI cannot send yet"
            }
            check(goRoute.responseBody !in setOf(Body.Text, Body.Redirect)) {
                "${route.summary} answers with a ${goRoute.responseBody} body, which the CLI cannot print yet"
            }
            val positional = goRoute.positional
            val use = (listOf(name.name) + positional.map { "<${flagName(it)}>" }).joinToString(" ")
            val description = route.description ?: route.summary!!
            val example = example(listOfNotNull("dewey", name.group, use).joinToString(" "), goRoute)

            append("func ${functionName(goRoute)}(api API) *cobra.Command {\n")
            append("\tcmd := &cobra.Command{\n")
            append("\t\tUse:     ${goString(use)},\n")
            // The parent's list of commands shows Short, so it says what the command does rather than repeat its name
            append("\t\tShort:   ${goString(description)},\n")
            append("\t\tArgs:    Args(${positional.joinToString(", ") { "\"${flagName(it)}\"" }}),\n")
            example?.let { append("\t\tExample: ${goString(it)},\n") }
            if (name.name == "list") append("\t\tAliases: []string{\"ls\"},\n")
            append("\t\tAnnotations: map[string]string{\n")
            sections(goRoute).forEach { (section, text) -> append("\t\t\t${goString(section)}: ${goString(text)},\n") }
            append("\t\t},\n")
            append("\t}\n")
            val response = goRoute.responseBody
            if (goRoute.optionalQuery.isNotEmpty() || goRoute.bodyType != null || response != Body.None) {
                append("\tflags := cmd.Flags()\n")
            }
            if (response == Body.Binary) append("\toutputFlag := flags.StringP(\"output\", \"o\", \"\", outputUsage)\n")
            if (response is Body.Json) {
                val fields = rowFields(response.type)
                val jsonExample = fields.take(2).joinToString(",")
                val jq =
                    when {
                        fields.isEmpty() -> "."
                        response.type is WireType.ListOf -> ".[].${fields.first()}"
                        else -> ".${fields.first()}"
                    }
                val arguments = (listOf(jsonExample, jq) + fields).joinToString(", ") { "\"$it\"" }
                append("\tjsonFlag, jqFlag := outputFlags(flags, $arguments)\n")
            }
            goRoute.optionalQuery.forEach { flag ->
                val arguments = "flags, \"${flagName(flag)}\", ${kind(flag)}, ${goString(flagUsage(flag))}"
                append("\t${local(flag)}Flag := flag($arguments)\n")
            }
            if (goRoute.bodyType != null) {
                append("\tbodyFlag := flags.String(\"body\", \"\", bodyUsage)\n")
                append("\t_ = cmd.MarkFlagRequired(\"body\")\n")
            }

            append("\tcmd.RunE = func(cmd *cobra.Command, args []string) error {\n")
            positional.forEachIndexed { index, parameter ->
                append(fallible(local(parameter), "arg(args[$index], \"${flagName(parameter)}\", ${kind(parameter)})"))
            }
            goRoute.optionsType?.let { options ->
                append("\t\tvar opts client.$options\n")
                goRoute.optionalQuery.forEach { parameter ->
                    append("\t\tif flags.Changed(\"${flagName(parameter)}\") {\n")
                    // An option is a pointer, nil until given, except a list or a map, which is nil itself
                    val pointer = optionalWire(parameter.type).go(MODELS).startsWith("*")
                    val value = (if (pointer) "" else "*") + "${local(parameter)}Flag"
                    append("\t\t\topts.${exported(parameter.name)} = $value\n")
                    append("\t\t}\n")
                }
            }
            (goRoute.requestBody as? Body.Json)?.let { body ->
                append(fallible("requestBody", "readJSON[${body.type.go(MODELS)}](cmd, *bodyFlag)"))
            }
            append(fallible("c", "api()"))

            val arguments =
                buildList {
                    add("cmd.Context()")
                    positional.forEach { add(local(it)) }
                    if (goRoute.bodyType != null) add("requestBody")
                    if (goRoute.optionsType != null) add("&opts")
                }.joinToString(", ")
            val call = "c.${goRoute.functionName}($arguments)"
            when (response) {
                is Body.Json -> {
                    append(fallible("out", call))
                    append("\t\treturn printResult(cmd, out, *jsonFlag, *jqFlag)\n")
                }

                Body.Binary -> {
                    append(fallible("out", call))
                    append("\t\treturn CopyOut(cmd, out, *outputFlag)\n")
                }

                else -> {
                    append("\t\treturn $call\n")
                }
            }
            append("\t}\n")
            append("\treturn cmd\n")
            append("}\n")
        }

    // Only what the usage line does not already show: the body to send, or the file to write
    private fun example(
        command: String,
        goRoute: GoRoute,
    ): String? =
        when {
            goRoute.requestBody is Body.Json -> {
                val body = SchemaText(model).sample((goRoute.requestBody as Body.Json).type)
                "$ $command --body '$body'"
            }

            goRoute.responseBody == Body.Binary -> {
                "$ $command --output <file>"
            }

            else -> {
                null
            }
        }

    // A command's output is a list of rows or a single row, and --json picks fields of the row
    private fun rowFields(type: WireType): List<String> {
        val named = (if (type is WireType.ListOf) type.element else type) as? WireType.Named ?: return emptyList()
        return (model.declaration(named.name) as? ObjectDeclaration)?.properties?.map { it.name }.orEmpty()
    }

    // The kind in the runtime that reads a command line value as the parameter's type
    private fun kind(parameter: Parameter): String {
        val type = parameter.type
        val go = type.go(MODELS).removePrefix("*")
        val json = "jsonKind[$go](\"json\", \"JSON of ${SchemaText(model).text(type)}\")"
        return when {
            type is WireType.Scalar -> {
                when (type.kind) {
                    ScalarKind.STRING, ScalarKind.CHAR -> "textKind"
                    ScalarKind.UUID -> "uuidKind"
                    ScalarKind.BOOLEAN -> "boolKind"
                    ScalarKind.BYTE, ScalarKind.SHORT, ScalarKind.INT, ScalarKind.LONG -> "intKind[$go]()"
                    ScalarKind.FLOAT, ScalarKind.DOUBLE -> "numberKind[$go]()"
                    ScalarKind.DATE -> "dateKind[$go]()"
                    ScalarKind.DATE_TIME -> "jsonKind[$go](\"time\", \"a date-time like 2024-01-31T12:00:00Z\")"
                }
            }

            type is WireType.Named -> {
                (model.declaration(type.name) as? EnumDeclaration)
                    ?.let { enum -> "enumKind[$go](${enum.values.joinToString(", ") { "\"$it\"" }})" }
                    ?: json
            }

            else -> {
                json
            }
        }
    }

    // A sentence, with the choices of an enum listed as {open|closed}
    private fun flagUsage(flag: Parameter): String {
        val choices =
            ((flag.type as? WireType.Named)?.let { model.declaration(it.name) } as? EnumDeclaration)
                ?.values
                ?.joinToString("|", ": {", "}")
                .orEmpty()
        val description =
            flag.description ?: splitWords(flag.name, " ").replaceFirstChar(Char::uppercaseChar)
        return description + choices
    }

    // The sections the help shows below the flags, by their heading, in the order they are shown
    private fun sections(goRoute: GoRoute): Map<String, String> {
        val schema = SchemaText(model)
        val sections = linkedMapOf<String, String>()
        val positional = goRoute.positional
        if (positional.isNotEmpty()) {
            val rows =
                positional.map { parameter ->
                    val type = schema.text(parameter.type)
                    "<${flagName(parameter)}>" to (parameter.description?.let { "$it ($type)" } ?: type)
                }
            sections["ARGUMENTS"] = table(rows, "").trimEnd()
        }
        (goRoute.requestBody as? Body.Json)?.let { body ->
            sections["BODY"] = schema.fieldsOf(body.type)?.trimEnd() ?: "A JSON ${schema.text(body.type)}"
        }
        when (val response = goRoute.responseBody) {
            Body.Binary -> {
                sections["OUTPUT"] = "The file, written to --output or to stdout when it is redirected"
            }

            is Body.Json -> {
                val fields = schema.fieldsOf(response.type)
                sections["JSON FIELDS"] =
                    when {
                        fields == null -> "A JSON ${schema.text(response.type)}"
                        response.type is WireType.ListOf -> "A list, each item with:\n${fields.trimEnd()}"
                        else -> fields.trimEnd()
                    }
            }

            else -> {}
        }
        schema.where()?.let { sections["TYPES"] = it.trimEnd() }
        return sections
    }

    private fun fallible(
        variable: String,
        expression: String,
    ): String = "\t\t$variable, err := $expression\n\t\tif err != nil {\n\t\t\treturn err\n\t\t}\n"

    private fun flagName(parameter: Parameter): String = splitWords(parameter.name, "-")

    private fun local(parameter: Parameter): String {
        val name = parameterName(parameter.name)
        return if (name in RESERVED_LOCALS) "${name}Param" else name
    }

    private fun goString(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}

fun Application.generateGoCli(
    packageName: String,
    clientImportPath: String,
    savePath: String,
    loginOnlyGuards: Set<String> = emptySet(),
    routes: List<OpenApiRoute>? = null,
    fileWriter: ((File, String) -> Unit)? = null,
    cleanDistPackage: Boolean = true,
) {
    // The client generator is only asked for its routes here, the client itself is written by generateGoClient
    val client = goClientGenerator("client", clientImportPath, Path.of(savePath), routes)
    GoCliGenerator(
        routes = routes ?: client.goRoutes.map { it.route },
        goRoutes = client.goRoutes,
        model = client.model,
        packageName = packageName,
        clientImportPath = clientImportPath,
        loginOnlyGuards = loginOnlyGuards,
        dist = Path.of(savePath),
        fileWriter = fileWriter,
        cleanDistPackage = cleanDistPackage,
    ).safeClient()
}
