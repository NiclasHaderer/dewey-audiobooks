import { createLucideIcon } from "lucide-react"

export const AudibleIcon = createLucideIcon("audible", [
  ["path", { d: "M2 11l10 6 10-6", key: "chevron" }],
  [
    "path",
    {
      d: "M4.61 10.56A8.6 8.6 0 0 1 19.33 9.79L17.35 10.35A7.6 7.6 0 0 0 4.61 10.56Z",
      fill: "currentColor",
      stroke: "none",
      key: "outer",
    },
  ],
  [
    "path",
    {
      d: "M7.18 11.7A5.8 5.8 0 0 1 17.01 11.36L15.23 11.85A4.92 4.92 0 0 0 7.18 11.7Z",
      fill: "currentColor",
      stroke: "none",
      key: "middle",
    },
  ],
  [
    "path",
    {
      d: "M9.63 12.87A3.1 3.1 0 0 1 14.77 12.87L13.22 13.27A2.37 2.37 0 0 0 9.63 12.87Z",
      fill: "currentColor",
      stroke: "none",
      key: "inner",
    },
  ],
])
