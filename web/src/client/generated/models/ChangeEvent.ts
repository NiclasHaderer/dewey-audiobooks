import type { ChangeType } from "./ChangeType"
import type { ChangedEntity } from "./ChangedEntity"
import type { UUID } from "./UUID"

export interface ChangeEvent {
  entity: ChangedEntity
  id: UUID
  libraryId: UUID
  type: ChangeType
}
