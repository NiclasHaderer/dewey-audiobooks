import { BookMarkedIcon, KeyRoundIcon, LucideIcon, ScaleIcon, UserIcon, UsersIcon } from "lucide-react"

export type NavEntry = { href: string; Icon: LucideIcon; label: string }

export const accountItem: NavEntry = { href: "/settings/account", Icon: UserIcon, label: "Account" }
export const userItems: NavEntry[] = [
  accountItem,
  { href: "/settings/tokens", Icon: KeyRoundIcon, label: "Access tokens" },
]
export const licensesItem: NavEntry = { href: "/settings/licenses", Icon: ScaleIcon, label: "Open source licenses" }
export const adminItems: NavEntry[] = [
  { href: "/settings/libraries", Icon: BookMarkedIcon, label: "Libraries" },
  { href: "/settings/users", Icon: UsersIcon, label: "Users" },
]
export const adminPaths = adminItems.map(i => i.href)
