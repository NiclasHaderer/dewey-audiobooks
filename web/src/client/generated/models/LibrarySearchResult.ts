import type { Author } from "./Author"
import type { Book } from "./Book"
import type { Series } from "./Series"

export interface LibrarySearchResult {
  authors: Array<Author>
  books: Array<Book>
  series: Array<Series>
}
