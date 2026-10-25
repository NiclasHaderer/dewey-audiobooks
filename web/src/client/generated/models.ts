/* eslint-disable */
// @ts-nocheck

export interface ApiVersion {
  version: string
}

export interface Author {
  biography: string | null
  birthDate: string | null
  bornIn: string | null
  deathDate: string | null
  id: UUID
  imageID: UUID | null
  libraryId: UUID
  name: string
  provider: string | null
  providerID: string | null
  website: string | null
}

export interface AuthorCreate {
  name: string
}

export interface AuthorDetailed {
  biography: string | null
  birthDate: string | null
  books: Array<Book>
  bornIn: string | null
  deathDate: string | null
  id: UUID
  imageID: UUID | null
  libraryId: UUID
  locked: Array<AuthorField>
  name: string
  provider: string | null
  providerID: string | null
  series: Array<Series>
  website: string | null
}

export type AuthorField =
  "NAME" | "PROVIDER" | "PROVIDER_ID" | "BIOGRAPHY" | "IMAGE_ID" | "WEBSITE" | "BORN_IN" | "BIRTH_DATE" | "DEATH_DATE"

export interface AuthorUpdate {
  biography?: string | null
  birthDate?: string | null
  books?: Array<UUID>
  bornIn?: string | null
  deathDate?: string | null
  image?: string | null
  name?: string
  provider?: string | null
  providerID?: string | null
  unlock?: Array<AuthorField>
  website?: string | null
}

export interface Book {
  authors: Array<NamedId>
  coverID: UUID | null
  description: string | null
  durationMs: number
  genres: Array<string>
  id: UUID
  isbn: string | null
  language: MetadataLanguage | null
  libraryId: UUID
  narrators: Array<string>
  positionMs: number
  provider: string | null
  providerID: string | null
  providerRating: number | null
  publisher: string | null
  releaseDate: string | null
  series: Array<TitledId>
  status: PlayStatus
  title: string
}

export interface BookDetailed {
  authors: Array<NamedId>
  chapters: Array<Chapter>
  coverID: UUID | null
  description: string | null
  durationMs: number
  genres: Array<string>
  id: UUID
  isbn: string | null
  language: MetadataLanguage | null
  libraryId: UUID
  locked: Array<BookField>
  narrators: Array<string>
  positionMs: number
  provider: string | null
  providerID: string | null
  providerRating: number | null
  publisher: string | null
  releaseDate: string | null
  series: Array<TitledId>
  status: PlayStatus
  title: string
  tracks: Array<Track>
}

export type BookField =
  | "TITLE"
  | "AUTHORS"
  | "SERIES"
  | "PROVIDER"
  | "PROVIDER_ID"
  | "PROVIDER_RATING"
  | "RELEASE_DATE"
  | "PUBLISHER"
  | "LANGUAGE"
  | "DESCRIPTION"
  | "NARRATORS"
  | "GENRES"
  | "ISBN"
  | "COVER_ID"
  | "CHAPTERS"

export interface BookUpdate {
  authors?: Array<UUID>
  chapters?: Array<ChapterMark>
  cover?: string | null
  description?: string | null
  genres?: Array<string> | null
  isbn?: string | null
  language?: MetadataLanguage | null
  narrators?: Array<string> | null
  provider?: string | null
  providerID?: string | null
  providerRating?: number | null
  publisher?: string | null
  releaseDate?: string | null
  series?: Array<UUID> | null
  title?: string
  unlock?: Array<BookField>
}

export interface ChangeEvent {
  entity: ChangedEntity
  id: UUID
  libraryId: UUID
  type: ChangeType
}

export interface ChangeEventBatch {
  events: Array<ChangeEvent>
  missedPreviousEvents: boolean
}

export type ChangeType = "CREATED" | "UPDATED" | "DELETED"

export type ChangedEntity = "LIBRARY" | "BOOK" | "AUTHOR" | "SERIES"

export interface Chapter {
  endMs: number
  startMs: number
  title: string | null
  trackId: UUID
}

export interface ChapterMark {
  startMs: number
  title: string | null
}

export interface DeweyAccessToken {
  accessToken: string
}

export interface DeweyChangePassword {
  currentPassword: string
  newPassword: string
}

export interface DeweyJWKs {
  keys: Array<JWK>
}

export interface DeweyLoginUser {
  password: string
  username: string
}

export interface DeweyModifyPermissions<PERMISSIONS> {
  permissions: PERMISSIONS
}

export interface DeweyRegisterUser {
  password: string
  username: string
}

export interface DeweyRenameUser {
  username: string
}

export interface DeweyUser {
  id: UUID
  username: string
}

export interface DeweyUserWithPermissions<PERMISSIONS extends NonNullable<any>> {
  id: UUID
  permissions: PERMISSIONS
  username: string
}

export type Empty = ""

export interface FileScanner {
  name: string
}

export interface FileSystemItem {
  name: string
  parent: string | null
  path: string
}

export interface Genre {
  bookCount: number
  name: string
}

export interface GenreDetailed {
  books: Array<Book>
  name: string
}

export interface JWK {
  e: string
  kid: string
  kty: string
  n: string
  use: string
}

export interface Library {
  bookCount: number
  combineFileScannerFields: boolean
  combineMetadataAgentFields: boolean
  fileScanners: Array<FileScanner>
  folders: Array<string>
  icon: string | null
  id: UUID
  language: MetadataLanguage
  metadataAgents: Array<NamedMetadataAgent>
  name: string
  preferEmbeddedMetadata: boolean
  region: MetadataRegion
}

export type LibraryPermissionLevel = "READONLY" | "READ_WRITE"

export interface LibraryPermissions {
  id: UUID
  name: string
  permissions: LibraryPermissionLevel
}

export interface LibrarySearchResult {
  authors: Array<Author>
  books: Array<Book>
  series: Array<Series>
}

export interface ListeningHistoryEntry {
  at: string
  book: Book
  id: UUID
  positionMs: number
}

export interface MetadataAgentApiModel {
  name: string
  supportedRegions: Array<MetadataRegion>
}

export interface MetadataAgentID {
  itemID: string
  provider: string
}

export interface MetadataAuthor {
  biography: string | null
  birthDate: string | null
  bornIn: string | null
  deathDate: string | null
  id: MetadataAgentID
  imageURL: string | null
  link: string
  name: string | null
  website: string | null
}

export interface MetadataBook {
  authors: Array<MetadataSearchAuthor> | null
  coverURL: string | null
  description: string | null
  id: MetadataAgentID
  isbn: string | null
  language: MetadataLanguage | null
  link: string | null
  narrators: Array<string>
  providerRating: number | null
  publisher: string | null
  releaseDate: string | null
  series: Array<MetadataBookSeries>
  title: string | null
}

export interface MetadataBookSeries {
  id: MetadataAgentID
  index: number | null
  link: string
  title: string | null
}

export interface MetadataChapters {
  chapters: Array<ChapterMark>
  runtimeMs: number
}

export type MetadataLanguage =
  "Spanish" | "English" | "German" | "French" | "Italian" | "Danish" | "Finnish" | "Norwegian" | "Swedish" | "Russian"

export type MetadataRegion = "AU" | "CA" | "DE" | "ES" | "FR" | "IN" | "IT" | "JP" | "US" | "UK"

export interface MetadataSearchAuthor {
  id: MetadataAgentID
  link: string
  name: string | null
}

export interface MetadataSearchBook {
  authors: Array<MetadataSearchAuthor> | null
  coverURL: string | null
  id: MetadataAgentID
  language: MetadataLanguage | null
  link: string | null
  narrators: Array<string>
  releaseDate: string | null
  series: Array<MetadataBookSeries>
  title: string | null
}

export type MetadataSearchCount = "Small" | "Medium" | "Large" | "ExtraLarge"

export interface MetadataSeries {
  authors: Array<string> | null
  books: Array<MetadataSearchBook> | null
  coverURL: string | null
  description: string | null
  id: MetadataAgentID
  link: string
  primaryWorks: number | null
  title: string | null
  totalBooks: number | null
}

export interface NamedId {
  id: UUID
  name: string
}

export interface NamedMetadataAgent {
  name: string
}

export interface Narrator {
  bookCount: number
  name: string
}

export interface NarratorDetailed {
  books: Array<Book>
  name: string
}

export type Order = "ASC" | "DESC"

export interface PaginatedResponse<T> {
  items: Array<T>
  limit: number
  offset: number
  total: number
}

export interface PartialUpdateLibrary {
  combineFileScannerFields?: boolean
  combineMetadataAgentFields?: boolean
  fileScanners?: Array<FileScanner>
  folders?: Array<string>
  icon?: string | null
  language?: MetadataLanguage
  metadataAgents?: Array<NamedMetadataAgent>
  name?: string
  preferEmbeddedMetadata?: boolean
  region?: MetadataRegion
}

export type PlayStatus = "UNPLAYED" | "IN_PROGRESS" | "FINISHED"

export interface ProgressUpdate {
  positionMs: number
}

export interface Series {
  authors: Array<NamedId>
  bookCoverIDs: Array<UUID>
  coverID: UUID | null
  description: string | null
  genres: Array<string>
  id: UUID
  libraryId: UUID
  primaryWorks: number | null
  provider: string | null
  providerID: string | null
  title: string
  totalBooks: number | null
}

export interface SeriesCreate {
  title: string
}

export interface SeriesDetailed {
  authors: Array<NamedId>
  bookCoverIDs: Array<UUID>
  books: Array<Book>
  coverID: UUID | null
  description: string | null
  genres: Array<string>
  id: UUID
  libraryId: UUID
  locked: Array<SeriesField>
  narrators: Array<string>
  primaryWorks: number | null
  provider: string | null
  providerID: string | null
  title: string
  totalBooks: number | null
  yearRange: YearRange | null
}

export type SeriesField =
  "TITLE" | "PROVIDER" | "PROVIDER_ID" | "TOTAL_BOOKS" | "PRIMARY_WORKS" | "COVER_ID" | "DESCRIPTION"

export interface SeriesUpdate {
  books?: Array<UUID>
  cover?: string | null
  description?: string | null
  primaryWorks?: number | null
  provider?: string | null
  providerID?: string | null
  title?: string
  totalBooks?: number | null
  unlock?: Array<SeriesField>
}

export interface SetDismissed {
  dismissed: boolean
}

export interface SetFinished {
  finished: boolean
}

export interface ThirdPartyLicense {
  license: string
  licenseUrl: string | null
  name: string
  repository: string | null
  text: string | null
  version: string
}

export interface TitledId {
  id: UUID
  title: string
}

export interface Track {
  book: TitledId
  durationMs: number
  fileModifiedAt: string
  id: UUID
  title: string
  trackNr: number
}

export type UUID = `${string}-${string}-${string}-${string}-${string}`

export interface UpdateLibrary {
  combineFileScannerFields: boolean
  combineMetadataAgentFields: boolean
  fileScanners: Array<FileScanner>
  folders: Array<string>
  icon: string | null
  language: MetadataLanguage
  metadataAgents: Array<NamedMetadataAgent>
  name: string
  preferEmbeddedMetadata: boolean
  region: MetadataRegion
}

export interface UpdateLibraryPermissions {
  id: UUID
  permissions: LibraryPermissionLevel
}

export interface UpdateUserPermissions {
  isAdmin: boolean
  libraries: Array<UpdateLibraryPermissions>
}

export interface UserPermissions {
  isAdmin: boolean
  libraries: Array<LibraryPermissions>
}

export interface YearRange {
  end: number
  start: number
}
