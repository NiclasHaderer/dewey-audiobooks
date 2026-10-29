package io.dewey.server.api

import io.dewey.auth.interactions.DeweyChangePasswordParams
import io.dewey.auth.interactions.DeweyCurrentUserParams
import io.dewey.auth.interactions.DeweyDeleteUserParams
import io.dewey.auth.interactions.DeweyDisplayUserParams
import io.dewey.auth.interactions.DeweyJwksParams
import io.dewey.auth.interactions.DeweyListUserParams
import io.dewey.auth.interactions.DeweyLoginParams
import io.dewey.auth.interactions.DeweyLogoutParams
import io.dewey.auth.interactions.DeweyModifyPermissionsParams
import io.dewey.auth.interactions.DeweyRefreshTokenParams
import io.dewey.auth.interactions.DeweyRegisterParams
import io.dewey.auth.interactions.DeweyRenameUserParams
import io.dewey.metadata.responses.MetadataLanguage
import io.dewey.metadata.responses.MetadataSearchCount
import io.dewey.models.Order
import io.dewey.openapi.ktor.BeforeBodyParsing
import io.dewey.openapi.ktor.Description
import io.dewey.openapi.ktor.NotSecured
import io.dewey.openapi.ktor.Secured
import io.dewey.openapi.ktor.Summary
import io.dewey.openapi.ktor.Tagged
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.plugins.auth.Guards
import io.dewey.server.plugins.auth.UserScoped
import io.dewey.server.plugins.auth.assertLibraryPermissions
import io.ktor.resources.Resource
import io.ktor.server.routing.RoutingContext
import kotlin.uuid.Uuid

private const val LIMIT = "How many items to list, 20 by default"
private const val OFFSET = "How many items to skip, to page through the rest"
private const val ORDER = "Sort by title or name, ascending by default"
private const val SHOW_INVISIBLE = "Also list items whose files are gone and that are about to be deleted"
private const val PREFIX = "The start of the text to complete"
private const val QUERY = "What to search for"
private const val PROVIDER = "The metadata agent the id is from, like audible"

// A summary also names the generated client methods, so the command line spelling of a route is set with command
// and what it does is told in description, which the CLI lists and the API docs show
@Resource("api")
class Api {
    @Secured(Guards.Admin)
    @Resource("fs")
    @Summary(
        "List folders at a certain path",
        method = "GET",
        command = "list",
        description = "List the folders at a path on the server",
    )
    @Tagged("Filesystem", description = "Browse the server's folders", group = "Server")
    data class FileSystem(
        @Description("The folder on the server to list")
        val path: String,
        @Description("Also list hidden folders")
        val showHidden: Boolean = false,
        private val parent: Api,
    )

    @Resource("auth")
    @Tagged("Sessions", description = "Log in with a password, like the web app", group = "Account")
    data class Auth(
        private val parent: Api,
    ) {
        @Summary(
            "Login user",
            method = "POST",
            command = "login",
            description = "Log in with a username and password and get a short-lived access JWT",
        )
        @Resource("login")
        data class Login(
            private val parent: Auth,
        ) : DeweyLoginParams

        @Summary("Logout user", method = "POST", command = "logout", description = "End a web app session")
        @Resource("logout")
        data class Logout(
            private val parent: Auth,
        ) : DeweyLogoutParams

        @Summary(
            "Register user",
            method = "POST",
            status = 201,
            command = "register",
            description = "Create an account (the first account becomes an admin)",
        )
        @Resource("register")
        data class Register(
            private val parent: Auth,
        ) : DeweyRegisterParams

        @Summary(
            "Retrieve Jwks",
            method = "GET",
            command = "jwks",
            description = "Show the public keys the server signs its tokens with",
        )
        @Resource("jwks.json")
        data class Jwks(
            private val parent: Auth,
        ) : DeweyJwksParams

        @Secured(Guards.Normal)
        @Resource("user")
        @Tagged("Users", description = "User accounts", group = "Account")
        data class User(
            private val parent: Auth,
        ) {
            @Summary("List users", method = "GET", description = "List all accounts (admins only)")
            @Secured(Guards.Admin)
            @Resource("")
            data class All(
                private val parent: User,
            ) : DeweyListUserParams

            @NotSecured
            @Summary(
                "Refresh access token",
                method = "POST",
                command = "refresh",
                description = "Trade the refresh cookie of a web session for a new access JWT",
            )
            @Resource("refresh")
            @Tagged("Sessions")
            data class Refresh(
                private val parent: User,
            ) : DeweyRefreshTokenParams

            @Resource("{id}")
            @Summary("Get user", method = "GET", command = "view", description = "View an account")
            @Summary("Delete user", method = "DELETE", description = "Delete an account")
            @Secured(Guards.NormalLogin, method = "DELETE")
            data class Id(
                override val id: Uuid,
                private val parent: User,
            ) : DeweyDeleteUserParams,
                DeweyDisplayUserParams {
                @Summary("Update username", method = "POST", command = "rename", description = "Rename an account")
                @Secured(Guards.NormalLogin)
                @Resource("username")
                data class Username(
                    private val parent: Id,
                ) : DeweyRenameUserParams {
                    override val id: Uuid
                        get() = parent.id
                }

                @Summary(
                    "Update password",
                    method = "POST",
                    command = "set-password",
                    description = "Change the password of an account, which also revokes its access tokens",
                )
                @Secured(Guards.NormalLogin)
                @Resource("password")
                data class Password(
                    private val parent: Id,
                ) : DeweyChangePasswordParams {
                    override val id: Uuid
                        get() = parent.id
                }

                @Secured(Guards.AdminLogin)
                @Summary(
                    "Update permissions",
                    method = "PUT",
                    command = "set-permissions",
                    description = "Make an account an admin or set which libraries it can read or write",
                )
                @Resource("permissions")
                data class Permissions(
                    private val parent: Id,
                ) : DeweyModifyPermissionsParams {
                    override val id: Uuid
                        get() = parent.id
                }
            }

            @Summary(
                "Get current user",
                method = "GET",
                command = "view",
                description = "View your account and permissions",
            )
            @Resource("current")
            @Tagged("Me")
            data class Current(
                private val parent: User,
            ) : DeweyCurrentUserParams

            @Summary(
                "List access tokens",
                method = "GET",
                description = "List your access tokens, without their secrets",
            )
            @Summary(
                "Create access token",
                method = "POST",
                status = 201,
                description = "Create an access token and print its secret (needs your password)",
            )
            @Secured(Guards.NormalLogin, method = "POST")
            @Resource("tokens")
            @Tagged("Access tokens", description = "Access tokens for scripts and this CLI", group = "Account")
            data class Tokens(
                private val parent: User,
            ) {
                @Summary(
                    "Update access token",
                    method = "PATCH",
                    command = "edit",
                    description =
                        "Rename an access token or change its permissions, effective immediately (needs your password)",
                )
                @Summary(
                    "Revoke access token",
                    method = "DELETE",
                    description = "Revoke an access token, effective immediately (needs your password)",
                )
                @Secured(Guards.NormalLogin)
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Tokens,
                ) {
                    @Summary(
                        "Reveal access token",
                        method = "POST",
                        description = "Print the secret of an access token again (needs your password)",
                    )
                    @Resource("reveal")
                    data class Reveal(
                        private val parent: Id,
                    ) {
                        val id
                            get() = parent.id
                    }
                }
            }
        }
    }

    @Secured(Guards.Normal)
    @Resource("me")
    @Tagged("Me", description = "Your account and listening", group = "Account")
    data class Me(
        private val parent: Api,
    ) {
        @Summary(
            "Get continue listening",
            method = "GET",
            command = "continue-listening",
            description = "List the books you have started but not finished or dismissed, most recent first",
        )
        @Resource("continue-listening")
        data class ContinueListening(
            @Description(LIMIT)
            val limit: Int = 20,
            private val parent: Me,
        )

        @Summary(
            "Get listening history",
            method = "GET",
            command = "history",
            description = "List your saved listening progress, newest first",
        )
        @Resource("history")
        data class History(
            @Description(LIMIT)
            val limit: Int = 20,
            @Description(OFFSET)
            val offset: Long = 0,
            private val parent: Me,
        )
    }

    @Summary("Ping server", method = "POST", command = "ping", description = "Check that the server is up")
    @Tagged("Server", description = "Server status, version and licenses", group = "Server")
    @Resource("ping")
    data class Ping(
        private val parent: Api,
    )

    @Summary("Get api version", method = "GET", command = "version", description = "Show the API version of the server")
    @Tagged("Server")
    @Resource("version")
    data class Version(
        private val parent: Api,
    )

    @Secured(Guards.Normal)
    @Summary(
        "List third party licenses",
        method = "GET",
        command = "licenses",
        description = "List the server's third-party licenses",
    )
    @Tagged("Server")
    @Resource("licenses")
    data class Licenses(
        private val parent: Api,
    )

    @Secured(Guards.Admin)
    @Summary(
        "List file scanners",
        method = "GET",
        command = "file-scanners",
        description = "List the file scanners",
    )
    @Tagged("Scanners", description = "File scanners and metadata agents", group = "Server")
    @Resource("scanners")
    data class Scanners(
        private val parent: Api,
    )

    @Secured(Guards.Admin)
    @Summary(
        "List metadata agents",
        method = "GET",
        command = "metadata-agents",
        description = "List the metadata agents, like Audible",
    )
    @Tagged("Scanners")
    @Resource("metadata-agents")
    data class MetadataAgents(
        private val parent: Api,
    )

    @Secured(Guards.Normal)
    @Summary("List libraries", method = "GET", description = "List the libraries you can read")
    @Summary(
        "Create library",
        method = "POST",
        status = 201,
        description = "Create a library from folders on the server and scan them (admins only)",
    )
    @Resource("libraries")
    @Tagged("Libraries", description = "Libraries, the folders the server scans for audiobooks", group = "Library")
    data class Libraries(
        private val parent: Api,
    ) {
        @Summary(
            "Search in all libraries",
            method = "GET",
            command = "search",
            description = "Search books, authors and series in every library you can read",
        )
        @Resource("search")
        data class Search(
            @Description("Search books, authors and series for this text")
            val query: String? = null,
            @Description("Search authors for this name")
            val author: String? = null,
            @Description("Search books for this title")
            val book: String? = null,
            @Description("Search series for this title")
            val series: String? = null,
            private val parent: Libraries,
        ) {
            init {
                if (query == null && author == null && book == null && series == null) {
                    throw ErrorResponse.userError(
                        "Provide at least one of query, author, book or series",
                    )
                }
            }
        }

        @Resource("{libraryId}")
        @Summary(
            "Delete library",
            method = "DELETE",
            description = "Delete a library with its books, authors and series, keeping its files (admins only)",
        )
        @Summary("Update library", method = "PATCH", command = "edit", description = "Edit a library (admins only)")
        @Summary("Get library", method = "GET", command = "view", description = "View a library")
        data class Id(
            val libraryId: Uuid,
            private val parent: Libraries,
        ) : BeforeBodyParsing {
            override suspend fun RoutingContext.beforeBodyParsing() {
                assertLibraryPermissions(libraryId)
            }

            @Summary(
                "Rescan library",
                method = "POST",
                status = 202,
                description = "Rescan a library in the background",
            )
            @Resource("rescan")
            data class Rescan(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId
            }

            @Resource("books")
            @Tagged("Books", description = "Books and your progress in them", group = "Library")
            data class Books(
                private val parent: Libraries.Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List books", method = "GET", description = "List the books of a library")
                @Resource("")
                data class All(
                    @Description(LIMIT)
                    val limit: Int = 20,
                    @Description(OFFSET)
                    val offset: Long = 0,
                    @Description(ORDER)
                    val order: Order = Order.ASC,
                    @Description(SHOW_INVISIBLE)
                    val showInvisible: Boolean = false,
                    private val parent: Books,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary(
                    "Get book autocomplete",
                    method = "GET",
                    command = "autocomplete",
                    description = "List the book titles that start with a text",
                )
                @Resource("autocomplete")
                data class Autocomplete(
                    @Description(PREFIX)
                    val query: String,
                    private val parent: Books,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary(
                    "Get book",
                    method = "GET",
                    command = "view",
                    description = "View a book with its tracks and chapters",
                )
                @Summary("Update book", method = "PATCH", command = "edit", description = "Edit a book")
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Books,
                ) {
                    val libraryId
                        get() = parent.libraryId

                    @Summary(
                        "Auto match book",
                        method = "POST",
                        description = "Match a book with the metadata agents and apply the best result",
                    )
                    @Resource("automatch")
                    data class AutoMatch(
                        private val parent: Id,
                    ) {
                        val libraryId
                            get() = parent.libraryId

                        val id
                            get() = parent.id
                    }

                    @Summary(
                        "Set book progress",
                        method = "PUT",
                        status = 204,
                        command = "set-progress",
                        description = "Save your position in a book, in milliseconds from the start",
                    )
                    @Resource("progress")
                    data class Progress(
                        private val parent: Id,
                    ) : UserScoped {
                        val libraryId
                            get() = parent.libraryId

                        val id
                            get() = parent.id

                        @Summary(
                            "Set book finished",
                            method = "PUT",
                            status = 204,
                            command = "set-finished",
                            description = "Mark a book as finished or unfinished, keeping your position",
                        )
                        @Resource("finished")
                        data class Finished(
                            private val parent: Progress,
                        ) : UserScoped {
                            val libraryId
                                get() = parent.libraryId

                            val id
                                get() = parent.id
                        }

                        @Summary(
                            "Set book dismissed",
                            method = "PUT",
                            status = 204,
                            command = "set-dismissed",
                            description = "Hide a book from continue listening, or show it there again",
                        )
                        @Resource("dismissed")
                        data class Dismissed(
                            private val parent: Progress,
                        ) : UserScoped {
                            val libraryId
                                get() = parent.libraryId

                            val id
                                get() = parent.id
                        }
                    }
                }
            }

            @Resource("authors")
            @Tagged("Authors", description = "Authors in a library", group = "Library")
            @Summary(
                "Create author",
                method = "POST",
                status = 201,
                description = "Create an author",
            )
            data class Authors(
                private val parent: Libraries.Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List authors", method = "GET", description = "List the authors of a library")
                @Resource("")
                data class All(
                    @Description(LIMIT)
                    val limit: Int = 20,
                    @Description(OFFSET)
                    val offset: Long = 0,
                    @Description(ORDER)
                    val order: Order = Order.ASC,
                    @Description(SHOW_INVISIBLE)
                    val showInvisible: Boolean = false,
                    private val parent: Authors,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary(
                    "Get author autocomplete",
                    method = "GET",
                    command = "autocomplete",
                    description = "List the author names that start with a text",
                )
                @Resource("autocomplete")
                data class Autocomplete(
                    @Description(PREFIX)
                    val query: String,
                    private val parent: Authors,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary(
                    "Get author",
                    method = "GET",
                    command = "view",
                    description = "View an author with their books",
                )
                @Summary("Update author", method = "PATCH", command = "edit", description = "Edit an author")
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Authors,
                ) {
                    val libraryId
                        get() = parent.libraryId

                    @Summary(
                        "Auto match author",
                        method = "POST",
                        description = "Match an author with the metadata agents and apply the best result",
                    )
                    @Resource("automatch")
                    data class AutoMatch(
                        private val parent: Id,
                    ) {
                        val libraryId
                            get() = parent.libraryId

                        val id
                            get() = parent.id
                    }
                }
            }

            @Resource("narrators")
            @Tagged("Narrators", description = "Narrators in a library", group = "Library")
            data class Narrators(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List narrators", method = "GET", description = "List the narrators of a library")
                @Resource("")
                data class All(
                    @Description(LIMIT)
                    val limit: Int = 20,
                    @Description(OFFSET)
                    val offset: Long = 0,
                    @Description(ORDER)
                    val order: Order = Order.ASC,
                    private val parent: Narrators,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary(
                    "Get narrator",
                    method = "GET",
                    command = "view",
                    description = "View a narrator with the books they read",
                )
                @Resource("{name}")
                data class Name(
                    val name: String,
                    private val parent: Narrators,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }
            }

            @Resource("genres")
            @Tagged("Genres", description = "Genres in a library", group = "Library")
            data class Genres(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List genres", method = "GET", description = "List the genres of a library")
                @Resource("")
                data class All(
                    @Description(LIMIT)
                    val limit: Int = 20,
                    @Description(OFFSET)
                    val offset: Long = 0,
                    @Description(ORDER)
                    val order: Order = Order.ASC,
                    private val parent: Genres,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get genre", method = "GET", command = "view", description = "View a genre with its books")
                @Resource("{name}")
                data class Name(
                    val name: String,
                    private val parent: Genres,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }
            }

            @Resource("series")
            @Tagged("Series", description = "Series in a library", group = "Library")
            @Summary(
                "Create series",
                method = "POST",
                status = 201,
                description = "Create a series",
            )
            data class Series(
                private val parent: Libraries.Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List series", method = "GET", description = "List the series of a library")
                @Resource("")
                data class All(
                    @Description(LIMIT)
                    val limit: Int = 20,
                    @Description(OFFSET)
                    val offset: Long = 0,
                    @Description(ORDER)
                    val order: Order = Order.ASC,
                    @Description(SHOW_INVISIBLE)
                    val showInvisible: Boolean = false,
                    private val parent: Series,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary(
                    "Get series autocomplete",
                    method = "GET",
                    command = "autocomplete",
                    description = "List the series titles that start with a text",
                )
                @Resource("autocomplete")
                data class Autocomplete(
                    @Description(PREFIX)
                    val query: String,
                    private val parent: Series,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary(
                    "Get series",
                    method = "GET",
                    command = "view",
                    description = "View a series with its books in order",
                )
                @Summary("Update series", method = "PATCH", command = "edit", description = "Edit a series")
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Series,
                ) {
                    val libraryId
                        get() = parent.libraryId

                    @Summary(
                        "Auto match series",
                        method = "POST",
                        description = "Match a series with the metadata agents and apply the best result",
                    )
                    @Resource("automatch")
                    data class AutoMatch(
                        private val parent: Id,
                    ) {
                        val libraryId
                            get() = parent.libraryId

                        val id
                            get() = parent.id
                    }
                }
            }

            @Secured(Guards.Normal)
            @Resource("metadata")
            @Tagged(
                "Metadata",
                description = "Look up books, authors and series at the metadata agents",
                group = "Library",
            )
            data class Metadata(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Resource("author")
                data class Author(
                    private val parent: Metadata,
                ) {
                    @Summary(
                        "Get author metadata",
                        method = "GET",
                        command = "author",
                        description = "View an author from a metadata agent",
                    )
                    @Resource("{id}")
                    data class Id(
                        val id: String,
                        @Description(PROVIDER)
                        val provider: String,
                        private val parent: Author,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId
                    }

                    @Summary(
                        "Search author metadata",
                        method = "GET",
                        command = "search-authors",
                        description = "Search authors at the metadata agents",
                    )
                    @Resource("search")
                    data class Search(
                        @Description(QUERY)
                        val query: String,
                        private val parent: Author,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId
                    }
                }

                @Resource("book")
                data class Book(
                    private val parent: Metadata,
                ) {
                    @Summary(
                        "Get book metadata",
                        method = "GET",
                        command = "book",
                        description = "View a book from a metadata agent",
                    )
                    @Resource("{id}")
                    data class Id(
                        val id: String,
                        @Description(PROVIDER)
                        val provider: String,
                        private val parent: Book,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId

                        @Summary(
                            "Get book chapter metadata",
                            method = "GET",
                            command = "chapters",
                            description = "View the chapters of a book from a metadata agent",
                        )
                        @Resource("chapters")
                        data class Chapters(
                            private val parent: Id,
                        ) {
                            val libraryId
                                get() = parent.libraryId
                            val id
                                get() = parent.id
                            val provider
                                get() = parent.provider
                        }
                    }

                    @Summary(
                        "Search book metadata",
                        method = "GET",
                        command = "search-books",
                        description = "Search books at the metadata agents",
                    )
                    @Resource("search")
                    data class Search(
                        @Description(QUERY)
                        val query: String,
                        @Description("Keywords the book should match")
                        val keywords: String? = null,
                        @Description("The name of the author")
                        val authorName: String? = null,
                        @Description("The name of the narrator")
                        val narrator: String? = null,
                        @Description("The language of the book")
                        val language: MetadataLanguage? = null,
                        @Description("How many results to ask the agents for")
                        val pageSize: MetadataSearchCount? = null,
                        private val parent: Book,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId
                    }
                }

                @Resource("series")
                data class Series(
                    private val parent: Metadata,
                ) {
                    @Summary(
                        "Get series metadata",
                        method = "GET",
                        command = "series",
                        description = "View a series from a metadata agent",
                    )
                    @Resource("{id}")
                    data class Id(
                        val id: String,
                        @Description(PROVIDER)
                        val provider: String,
                        private val parent: Series,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId
                    }

                    @Summary(
                        "Search series metadata",
                        method = "GET",
                        command = "search-series",
                        description = "Search series at the metadata agents",
                    )
                    @Resource("search")
                    data class Search(
                        @Description(QUERY)
                        val query: String,
                        @Description("The name of the author")
                        val authorName: String? = null,
                        private val parent: Series,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId
                    }
                }
            }
        }
    }

    // The token is only checked at the handshake, so a socket opened with one would keep listening after the token
    // was revoked or expired
    @Summary("Listen to events", method = "GET")
    @Secured(Guards.MediaLogin)
    @Resource("events")
    data class Events(
        private val parent: Api,
    )

    @Secured(Guards.Media)
    @Resource("stream")
    @Tagged("Files", description = "Download audio and images", group = "Library")
    data class Files(
        private val parent: Api,
    ) {
        @Resource("audio")
        data class Audio(
            private val parent: Files,
        ) {
            @Summary(
                "Get audio file",
                method = "GET",
                command = "audio",
                description = "Download the audio file of a track",
            )
            @Resource("{id}")
            data class Id(
                val id: Uuid,
                private val parent: Audio,
            )
        }

        @Resource("images")
        data class Images(
            private val parent: Files,
        ) {
            @Summary(
                "Get image file",
                method = "GET",
                command = "image",
                description = "Download an image by its id, like a book's coverID or an author's imageID",
            )
            @Resource("{id}")
            data class Id(
                val id: Uuid,
                private val parent: Images,
            )
        }
    }
}
