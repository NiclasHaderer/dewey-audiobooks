import type { Book } from "./Book"
import type { UUID } from "./UUID"

export interface ListeningHistoryEntry {
  at: string
  book: Book
  id: UUID
  positionMs: number
}
