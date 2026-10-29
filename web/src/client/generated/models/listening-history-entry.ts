import type { Book } from "./book"
import type { UUID } from "./uuid"

export interface ListeningHistoryEntry {
  at: string
  book: Book
  id: UUID
  positionMs: number
}
