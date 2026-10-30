import type { Author } from "./author"
import type { Book } from "./book"
import type { Series } from "./series"

export interface LibrarySearchResult {
  authors: Array<Author>
  books: Array<Book>
  series: Array<Series>
}
