import type { ChangeType } from "./change-type"
import type { ChangedEntity } from "./changed-entity"
import type { UUID } from "./uuid"

export interface ChangeEvent {
  entity: ChangedEntity
  id: UUID
  libraryId: UUID
  type: ChangeType
}
