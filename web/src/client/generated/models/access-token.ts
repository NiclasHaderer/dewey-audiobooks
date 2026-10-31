import type { AccessTokenPermissions } from "./access-token-permissions"
import type { UUID } from "./uuid"

export interface AccessToken {
  createdAt: string
  expiresAt: string | null
  id: UUID
  lastUsedAt: string | null
  name: string
  permissions: AccessTokenPermissions
}
