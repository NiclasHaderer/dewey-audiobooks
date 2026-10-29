import type { AccessTokenPermissions } from "./access-token-permissions"

export interface UpdateAccessToken {
  name: string
  password: string
  permissions: AccessTokenPermissions
}
