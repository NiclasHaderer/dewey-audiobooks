import type { MetadataAgentID } from "./metadata-agent-id"
import type { MetadataSearchBook } from "./metadata-search-book"

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
