import type { AccessTokenLibrary } from "./access-token-library"

export interface AccessTokenPermissions {
  isAdmin: boolean
  libraries: Array<AccessTokenLibrary> | null
}
