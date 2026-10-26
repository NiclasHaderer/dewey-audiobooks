import type { UUID } from "./uuid"

export interface DeweyUserWithPermissions<PERMISSIONS extends NonNullable<unknown>> {
  id: UUID
  permissions: PERMISSIONS
  username: string
}
