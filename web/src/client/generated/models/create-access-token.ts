import type { AccessTokenPermissions } from "./access-token-permissions"

export interface CreateAccessToken {
  expiresAt: string | null
  name: string
  password: string
  permissions: AccessTokenPermissions
}
