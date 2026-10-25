import type { MetadataAgentID } from "./MetadataAgentID"
import type { MetadataSearchBook } from "./MetadataSearchBook"

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
