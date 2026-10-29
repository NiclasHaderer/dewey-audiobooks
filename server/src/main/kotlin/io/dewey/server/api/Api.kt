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

@Resource("api")
class Api {
    @Secured(Guards.Admin)
    @Resource("fs")
    @Summary("List folders at a certain path", method = "GET")
    @Tagged("Filesystem")
    data class FileSystem(
        val path: String,
        val showHidden: Boolean = false,
        private val parent: Api,
    )

    @Resource("auth")
    @Tagged("Auth")
    data class Auth(
        private val parent: Api,
    ) {
        @Summary("Login user", method = "POST")
        @Resource("login")
        data class Login(
            private val parent: Auth,
        ) : DeweyLoginParams

        @Summary("Logout user", method = "POST")
        @Resource("logout")
        data class Logout(
            private val parent: Auth,
        ) : DeweyLogoutParams

        @Summary("Register user", method = "POST", status = 201)
        @Resource("register")
        data class Register(
            private val parent: Auth,
        ) : DeweyRegisterParams

        @Summary("Retrieve Jwks", method = "GET")
        @Resource("jwks.json")
        data class Jwks(
            private val parent: Auth,
        ) : DeweyJwksParams

        @Secured(Guards.Normal)
        @Resource("user")
        data class User(
            private val parent: Auth,
        ) {
            @Summary("List users", method = "GET")
            @Secured(Guards.Admin)
            @Resource("")
            data class All(
                private val parent: User,
            ) : DeweyListUserParams

            @NotSecured
            @Summary("Refresh access token", method = "POST")
            @Resource("refresh")
            data class Refresh(
                private val parent: User,
            ) : DeweyRefreshTokenParams

            @Resource("{id}")
            @Summary("Get user", method = "GET")
            @Summary("Delete user", method = "DELETE")
            @Secured(Guards.NormalLogin, method = "DELETE")
            data class Id(
                override val id: Uuid,
                private val parent: User,
            ) : DeweyDeleteUserParams,
                DeweyDisplayUserParams {
                @Summary("Update username", method = "POST")
                @Secured(Guards.NormalLogin)
                @Resource("username")
                data class Username(
                    private val parent: Id,
                ) : DeweyRenameUserParams {
                    override val id: Uuid
                        get() = parent.id
                }

                @Summary("Update password", method = "POST")
                @Secured(Guards.NormalLogin)
                @Resource("password")
                data class Password(
                    private val parent: Id,
                ) : DeweyChangePasswordParams {
                    override val id: Uuid
                        get() = parent.id
                }

                @Secured(Guards.AdminLogin)
                @Summary("Update permissions", method = "PUT")
                @Resource("permissions")
                data class Permissions(
                    private val parent: Id,
                ) : DeweyModifyPermissionsParams {
                    override val id: Uuid
                        get() = parent.id
                }
            }

            @Summary("Get current user", method = "GET")
            @Resource("current")
            data class Current(
                private val parent: User,
            ) : DeweyCurrentUserParams

            @Summary("List access tokens", method = "GET")
            @Summary("Create access token", method = "POST", status = 201)
            @Secured(Guards.NormalLogin, method = "POST")
            @Resource("tokens")
            @Tagged("Access tokens")
            data class Tokens(
                private val parent: User,
            ) {
                @Summary("Update access token", method = "PATCH")
                @Summary("Revoke access token", method = "DELETE")
                @Secured(Guards.NormalLogin)
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Tokens,
                ) {
                    @Summary("Reveal access token", method = "POST")
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
    @Tagged("Me")
    data class Me(
        private val parent: Api,
    ) {
        @Summary("Get continue listening", method = "GET")
        @Resource("continue-listening")
        data class ContinueListening(
            val limit: Int = 20,
            private val parent: Me,
        )

        @Summary("Get listening history", method = "GET")
        @Resource("history")
        data class History(
            val limit: Int = 20,
            val offset: Long = 0,
            private val parent: Me,
        )
    }

    @Summary("Ping server", method = "POST")
    @Tagged("Server")
    @Resource("ping")
    data class Ping(
        private val parent: Api,
    )

    @Summary("Get api version", method = "GET")
    @Tagged("Server")
    @Resource("version")
    data class Version(
        private val parent: Api,
    )

    @Secured(Guards.Normal)
    @Summary("List third party licenses", method = "GET")
    @Tagged("Server")
    @Resource("licenses")
    data class Licenses(
        private val parent: Api,
    )

    @Secured(Guards.Admin)
    @Summary("List file scanners", method = "GET")
    @Tagged("Scanner")
    @Resource("scanners")
    data class Scanners(
        private val parent: Api,
    )

    @Secured(Guards.Admin)
    @Summary("List metadata agents", method = "GET")
    @Tagged("Scanner")
    @Resource("metadata-agents")
    data class MetadataAgents(
        private val parent: Api,
    )

    @Secured(Guards.Normal)
    @Summary("List libraries", method = "GET")
    @Summary("Create library", method = "POST", status = 201)
    @Resource("libraries")
    @Tagged("Library")
    data class Libraries(
        private val parent: Api,
    ) {
        @Summary("Search in all libraries", method = "GET")
        @Resource("search")
        data class Search(
            val query: String? = null,
            val author: String? = null,
            val book: String? = null,
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
        @Summary("Delete library", method = "DELETE")
        @Summary("Update library", method = "PATCH")
        @Summary("Get library", method = "GET")
        data class Id(
            val libraryId: Uuid,
            private val parent: Libraries,
        ) : BeforeBodyParsing {
            override suspend fun RoutingContext.beforeBodyParsing() {
                assertLibraryPermissions(libraryId)
            }

            @Summary("Rescan library", method = "POST", status = 202)
            @Resource("rescan")
            data class Rescan(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId
            }

            @Resource("books")
            @Tagged("Books")
            data class Books(
                private val parent: Libraries.Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List books", method = "GET")
                @Resource("")
                data class All(
                    val limit: Int = 20,
                    val offset: Long = 0,
                    val order: Order = Order.ASC,
                    val showInvisible: Boolean = false,
                    private val parent: Books,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get book autocomplete", method = "GET")
                @Resource("autocomplete")
                data class Autocomplete(
                    val query: String,
                    private val parent: Books,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get book", method = "GET")
                @Summary("Update book", method = "PATCH")
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Books,
                ) {
                    val libraryId
                        get() = parent.libraryId

                    @Summary("Auto match book", method = "POST")
                    @Resource("automatch")
                    data class AutoMatch(
                        private val parent: Id,
                    ) {
                        val libraryId
                            get() = parent.libraryId

                        val id
                            get() = parent.id
                    }

                    @Summary("Set book progress", method = "PUT", status = 204)
                    @Resource("progress")
                    data class Progress(
                        private val parent: Id,
                    ) : UserScoped {
                        val libraryId
                            get() = parent.libraryId

                        val id
                            get() = parent.id

                        @Summary("Set book finished", method = "PUT", status = 204)
                        @Resource("finished")
                        data class Finished(
                            private val parent: Progress,
                        ) : UserScoped {
                            val libraryId
                                get() = parent.libraryId

                            val id
                                get() = parent.id
                        }

                        @Summary("Set book dismissed", method = "PUT", status = 204)
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
            @Tagged("Authors")
            @Summary("Create author", method = "POST", status = 201)
            data class Authors(
                private val parent: Libraries.Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List authors", method = "GET")
                @Resource("")
                data class All(
                    val limit: Int = 20,
                    val offset: Long = 0,
                    val order: Order = Order.ASC,
                    val showInvisible: Boolean = false,
                    private val parent: Authors,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get author autocomplete", method = "GET")
                @Resource("autocomplete")
                data class Autocomplete(
                    val query: String,
                    private val parent: Authors,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get author", method = "GET")
                @Summary("Update author", method = "PATCH")
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Authors,
                ) {
                    val libraryId
                        get() = parent.libraryId

                    @Summary("Auto match author", method = "POST")
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
            @Tagged("Narrators")
            data class Narrators(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List narrators", method = "GET")
                @Resource("")
                data class All(
                    val limit: Int = 20,
                    val offset: Long = 0,
                    val order: Order = Order.ASC,
                    private val parent: Narrators,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get narrator", method = "GET")
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
            @Tagged("Genres")
            data class Genres(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List genres", method = "GET")
                @Resource("")
                data class All(
                    val limit: Int = 20,
                    val offset: Long = 0,
                    val order: Order = Order.ASC,
                    private val parent: Genres,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get genre", method = "GET")
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
            @Tagged("Series")
            @Summary("Create series", method = "POST", status = 201)
            data class Series(
                private val parent: Libraries.Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Summary("List series", method = "GET")
                @Resource("")
                data class All(
                    val limit: Int = 20,
                    val offset: Long = 0,
                    val order: Order = Order.ASC,
                    val showInvisible: Boolean = false,
                    private val parent: Series,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get series autocomplete", method = "GET")
                @Resource("autocomplete")
                data class Autocomplete(
                    val query: String,
                    private val parent: Series,
                ) {
                    val libraryId
                        get() = parent.libraryId
                }

                @Summary("Get series", method = "GET")
                @Summary("Update series", method = "PATCH")
                @Resource("{id}")
                data class Id(
                    val id: Uuid,
                    private val parent: Series,
                ) {
                    val libraryId
                        get() = parent.libraryId

                    @Summary("Auto match series", method = "POST")
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
            @Tagged("Metadata")
            data class Metadata(
                private val parent: Id,
            ) {
                val libraryId
                    get() = parent.libraryId

                @Resource("author")
                data class Author(
                    private val parent: Metadata,
                ) {
                    @Summary("Get author metadata", method = "GET")
                    @Resource("{id}")
                    data class Id(
                        val id: String,
                        val provider: String,
                        private val parent: Author,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId
                    }

                    @Summary("Search author metadata", method = "GET")
                    @Resource("search")
                    data class Search(
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
                    @Summary("Get book metadata", method = "GET")
                    @Resource("{id}")
                    data class Id(
                        val id: String,
                        val provider: String,
                        private val parent: Book,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId

                        @Summary("Get book chapter metadata", method = "GET")
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

                    @Summary("Search book metadata", method = "GET")
                    @Resource("search")
                    data class Search(
                        val query: String,
                        val keywords: String? = null,
                        val authorName: String? = null,
                        val narrator: String? = null,
                        val language: MetadataLanguage? = null,
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
                    @Summary("Get series metadata", method = "GET")
                    @Resource("{id}")
                    data class Id(
                        val id: String,
                        val provider: String,
                        private val parent: Series,
                    ) {
                        val libraryId
                            get() = parent.parent.libraryId
                    }

                    @Summary("Search series metadata", method = "GET")
                    @Resource("search")
                    data class Search(
                        val query: String,
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
    @Tagged("Files")
    data class Files(
        private val parent: Api,
    ) {
        @Resource("audio")
        data class Audio(
            private val parent: Files,
        ) {
            @Summary("Get audio file", method = "GET")
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
            @Summary("Get image file", method = "GET")
            @Resource("{id}")
            data class Id(
                val id: Uuid,
                private val parent: Images,
            )
        }
    }
}
