import type { UUID } from "@dewey/client/generated/models"

export const toBase64 = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    // eslint-disable-next-line @typescript-eslint/no-base-to-string
    reader.onload = () => resolve(reader.result!.toString())
    reader.onerror = reject
  })

export const isUUID = (uuidString: string): uuidString is UUID => {
  return /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i.test(uuidString)
}

// A "YYYY-MM-DD" date has no time zone. new Date() would read it as UTC midnight, which is the day before west of UTC.
export const parseDate = (date: string): Date => {
  const [year, month, day] = date.split("-").map(Number)
  return new Date(year, month - 1, day)
}

export const formatDate = (date: string) => {
  return parseDate(date).toLocaleDateString(undefined, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
}

export const apiErrorMessage = (error: string | object): string => {
  if (typeof error === "string") return error
  if (error && typeof error === "object" && "error" in error && typeof error.error === "string") return error.error
  return "Something went wrong"
}

export const notNullIsh = <T>(p: T | null | undefined): p is T => p !== null && p !== undefined

export const fromFormNumber = (value?: string | null): number | null => {
  return value ? Number(value) : null
}

export const fromFormDate = (date?: string | null): string | null => date || null

export const toRealURL = (baseUrl: string): string => {
  if (baseUrl.startsWith("/")) {
    return `${location.protocol}//${location.host}${baseUrl}`
  }
  return baseUrl
}

export const unique = <T>(list?: T[]): T[] => {
  return [...new Set(list)]
}

export const pluralize = (count: number, singular: string, plural = `${singular}s`): string => {
  return `${count} ${count === 1 ? singular : plural}`
}

const gigabytes = new Intl.NumberFormat(undefined, {
  style: "unit",
  unit: "gigabyte",
  unitDisplay: "short",
  maximumFractionDigits: 1,
})

export const formatGigabytes = (bytes: number): string => gigabytes.format(bytes / 1e9)

export const slug = (label: string): string =>
  label
    .toLowerCase()
    .replace(/[^\p{Letter}\p{Number}]+/gu, "-")
    .slice(0, 60)
    .replace(/^-+|-+$/g, "") || "untitled"

export const withoutSearchParam = (path: string, search: string, name: string): string => {
  const params = new URLSearchParams(search)
  params.delete(name)
  const rest = params.toString()
  return rest ? `${path}?${rest}` : path
}
